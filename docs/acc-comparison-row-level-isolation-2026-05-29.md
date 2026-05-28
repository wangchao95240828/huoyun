# Comparison Case：员工/分公司/销售 数据可见性 RLS（任务 S7）

> 任务：docs/acc-logic-gap-claude-task-supplement-2026-05-29.md §3 任务 S7 + 任务书 §3.10#4

## 1. 问题

migration 002 实现了 `tenant_id` 级 RLS，但 tenant 内**所有 user 都能看全部数据**。

ACC 老系统按 `Employee.Type + Customer.salesman_id + Shipment.branch_id` 三维做行级隔离：
- 销售只看自己的客户
- 分公司经理只看本分公司的票
- 管理员/财务全 tenant 可见

新系统必须等价实现，避免数据越权。

## 2. 实现策略

### 关键约束："不能让现有 178 测试 / 现有数据查询少返回行"

采用 **RESTRICTIVE 策略 + 空字符串视为 no-context** 模式，向后兼容：
- 新策略仅当 `app.user_role` 已设置为具体角色时生效
- 现有未设 user context 的链路（脚本、定时任务、admin tooling）行为不变
- 现有测试 mock JdbcTemplate 不触发实际策略，零回归

### migration 044：3 张表加 RESTRICTIVE 策略

**customers `customers_sales_visibility`**
- `app.user_role` 为空 → 不限制（向后兼容）
- ADMIN / FINANCE → 全可见
- 其他角色 → 仅 `salesman_user_id = app.user_id`

**shipments `shipments_branch_visibility`**
- 同上回退
- BRANCH_MANAGER → `branch_id = app.user_branch_id`
- SALESMAN → `customer_id IN (sub-query: salesman 名下客户)`

**orders `orders_branch_visibility`**
- 同 shipments

### RequestContext.setTenant 扩展
拦截器入口设 `app.current_tenant_id` 时**同步**设置：
- `app.user_id` ← `principal.userId`
- `app.user_role` ← `principal.roles.get(0)`（首个主角色）
- `app.user_branch_id` ← 空字符串占位（待 AuthPrincipal 加 branchId 字段）

所有都用 `set_config(.., true)` LOCAL 模式，事务结束自动失效。

## 3. 测试（RequestContextTest 3 项全过）

1. SALESMAN principal → 4 个 set_config 全部触发（tenant_id / user_id / user_role=SALESMAN / branch_id）
2. ADMIN principal → user_role 设为 ADMIN（验证策略 ADMIN 短路分支）
3. 空 roles 列表 → user_role 设空字符串（向后兼容回退路径）

DB 集成测试需要 RLS 实际生效，留给后续 e2e 测试套（带真实 PG）。

### 全工程
**178 测试全过**（+3 vs S6 结束时 175）。

## 4. 设计取舍

### 为什么用 `RESTRICTIVE`
PostgreSQL RLS 多 policy 默认 PERMISSIVE → OR 关系 → 加一条会**放宽**可见性，与"隔离"诉求相反。
RESTRICTIVE 是 AND → 在已有 tenant_isolation 之上**再叠加**用户级过滤。

### 为什么 `app.user_role = '' → 不限制`
保留**逃生口**给：
- 脚本 / 定时任务 / 内部维护工具：没有 user context，应不被限制
- 兼容期：现有 controller 链路里有些是非 web 入口（如 scheduler），如果一刀切会少返行
- 现有 178 单测 mock JdbcTemplate → 不触发实际 policy → 零回归

ACC 老系统也允许"系统账户"无限制，本设计对齐。

### 为什么 `app.user_branch_id` 暂留空
`AuthPrincipal` 目前不含 branchId 字段（需要查 `users.branch_id` 或 `user_organizations` 二级表）。
分两步：
1. 任务 S7 先完成 user_id + user_role 维度（销售可见性高频）
2. 后续 mini-task 补 AuthPrincipal.branchId + AuthService SELECT 联表 → branch_id RLS 完整生效

policy 写好了 branch_id 分支，等数据接入即激活。

## 5. 生产验证 checklist

merge 前必须在 dev 环境验证：
- [ ] 用 SALESMAN A 登录 → `/api/customers` 列表只含 salesman_user_id = A 的客户
- [ ] 用 SALESMAN A 登录 → `/api/acc/orders` 只含自己客户的订单
- [ ] 用 ADMIN 登录 → 全 tenant 可见，无任何丢失
- [ ] 用脚本（无 SecurityContext）跑数据修复 → 行为与 merge 前完全一致
- [ ] AccOrdersListProjectionTest / S1-S6 全部 178 单测仍绿

## 6. ACC 对照

| ACC | 新系统 |
|-----|--------|
| `Customer.SaleAccount = $this->user_id` | `customers_sales_visibility` policy |
| `Shipment.branch_id = $this->branch_id` | `shipments_branch_visibility` policy |
| `Order WHERE branch_id = ?` | `orders_branch_visibility` policy |
| `if ($admin) { ... }` 业务代码硬编码 | `app.user_role IN ('ADMIN','FINANCE')` 短路 |
| ACC 系统脚本无 session → 不过滤 | `app.user_role = '' → 不限制` 等价 |
