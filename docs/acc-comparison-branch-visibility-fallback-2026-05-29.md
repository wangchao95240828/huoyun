# Comparison Case：BRANCH_MANAGER 数据可见性双路 policy + AuthPrincipal.branchId 真实落地

> 任务：docs/acc-final-business-logic-review-claude-task-2026-05-29.md §C2
> 决策选项：用户选了"现在就补 AuthPrincipal.branchId"

## 1. 背景

原 S7 (migration 044) 写 BRANCH_MANAGER policy 强制 `branch_id = app.user_branch_id`，
但 RequestContext 把 `app.user_branch_id` 总设为空字符串（AuthPrincipal 暂无 branchId）。
**实战中 BRANCH_MANAGER 登录 → 看零行**（功能假坏，比放宽更糟）。

audit grep 发现：
- `shipments.branch_id` 全工程**没有任何 INSERT/UPDATE 入口**（grep 全无命中）
- AuthPrincipal 无 branchId 字段
- RequestContext 用空串占位

## 2. 决策

用户选择 **"现在就补 AuthPrincipal.branchId"**（不接受 fallback 作为最终方案）。

## 3. 实现

### 3.1 AuthPrincipal record 加 branchId
9-field record → 10-field record（加 `String branchId`）。
保留 9 参旧构造作为 convenience overload（向后兼容现有测试 + ApiContractTests / DocumentChargeServiceTests）。

### 3.2 AuthService.findLoginRows SELECT 补 branch_id
```sql
SELECT ..., u.branch_id::text AS branch_id, ...
FROM users u ...
GROUP BY ..., u.branch_id
```
`users.branch_id` 来自 migration 007（已存在多年，原本只用于 acc_employees）。

### 3.3 TokenService 跨 token 持久化
- `issue`：把 base.branchId() 透传进新 token payload
- `verify`：从 JWT payload 读 `branchId` 字段，反序列化到 AuthPrincipal

### 3.4 RequestContext.setTenant 写真实值
```java
jdbc.queryForObject("select set_config('app.user_branch_id', ?, true)",
    String.class, principal.branchId() == null ? "" : principal.branchId());
```

### 3.5 CustomerApiRepository.insertShipment 级联
```sql
INSERT INTO shipments (..., branch_id)
VALUES (..., (SELECT branch_id FROM customers WHERE id = ?::uuid))
```
- customers.branch_id 早已存在（migration 007）
- 新建 shipment 时从所属客户继承分公司，让 RLS 策略真正能匹配到行
- 不影响现有 8 参方法签名（mock 仍命中）

## 4. 047 的角色：从 "trap fix" 升级为 "双路 policy"

047 不再是临时 fallback，而是**有意识的双路设计**：
- 用户带 branchId → 严格 branch_id 匹配
- 用户无 branchId（系统账号、未分配分公司的合法账号） → 容错放行

注释已订正：047 sql 顶部说明从"trap fix"改为"双路 policy"。

## 5. 测试

### RequestContextTest +2（共 5 测试，全过）
- `branchManagerPrincipalWritesRealBranchId`：BRANCH_MANAGER 带 branchId="branch-shanghai" → set_config 写真实值
- `principalWithoutBranchIdWritesEmptyString`：salesman 无 branch → 空串（policy 走容错）
- 原 3 测试保留：原 `emptyRolesUsesEmptyStringRoleForBackwardCompat` 等

### 全工程
**193 → 195**（+2）测试绿，零回归。

## 6. 端到端路径

```
登录请求 → AuthService.findLoginRows JOIN users.branch_id
        → loginPrincipal 构造 AuthPrincipal(branchId=u.branch_id)
        → TokenService.issue 把 branchId 进 JWT payload
        → 后续请求 TokenService.verify 还原 branchId
        → RequestContext.setTenant 写 app.user_branch_id
        → 任何 SELECT shipments 被 RLS 047 双路评估
        ↓
        BRANCH_MANAGER:
          - app.user_branch_id != '' AND shipments.branch_id 匹配 → 通过
          - app.user_branch_id == ''（合法无分支账号） → 容错通过
        SALESMAN:
          - shipments.customer_id 在 salesman 名下客户 → 通过
```

## 7. 生产部署 checklist

- [ ] migrations 007 + 044 + 047 已应用
- [ ] users.branch_id 数据已填（按业务运营在用户管理界面或 SQL 批量）
- [ ] customers.branch_id 数据已填（继承到新建 shipments）
- [ ] 历史 shipments.branch_id NULL 行有意识保留还是 backfill？运营决策

历史 NULL 行的语义：
- 047 严格分支 → BRANCH_MANAGER 看不到（默认隔离）
- 如果业务要回填，可执行：
  ```sql
  UPDATE shipments SET branch_id = (
    SELECT branch_id FROM customers WHERE customers.id = shipments.customer_id
  ) WHERE branch_id IS NULL;
  ```

## 8. ACC 对照

| ACC | 新系统 |
|-----|--------|
| `Employee.BranchID` | `users.branch_id` |
| 登录 session 存 `$_SESSION['BranchID']` | JWT payload.branchId |
| `Shipment WHERE BranchID = ?` 硬编码 | `RLS shipments_branch_visibility` policy |
| `Customer.BranchID` | `customers.branch_id` |
| 新建 Online.BranchID = Customer.BranchID | insertShipment 内 SUBSELECT 级联 |
| 系统账号无 BranchID 不限制 | policy 容错分支 |
