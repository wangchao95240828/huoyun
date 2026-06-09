# PRD：代码调整方案（痛点文档驱动）

**版本**: 1.1  
**日期**: 2026-05-14  
**来源**: 痛点文档-逐项细述.docx + main 分支现状分析  
**范围**: `apps/backend`（Spring Boot）、`db/migrations`、`apps/web`

---

## 一、现状快照

### 已落地（main 分支）

| 模块 | 文件位置 | 技术栈 | 状态 |
|------|---------|--------|------|
| 认证 / JWT | `auth/` | JdbcTemplate + Spring Security | ✅ 稳定 |
| 用户 / 角色 / 权限 | `admin/` | JdbcTemplate | ✅ 稳定 |
| 客户 API（签名鉴权） | `customerapi/` | JdbcTemplate | ✅ 稳定 |
| 费率引擎（rate_cards / remote_zones / fuel_surcharge_rates） | `rates/` | JdbcTemplate | ✅ 基础版 |
| 财务 CRUD 全家桶 | `finance/` | **MyBatis-Plus** | ✅ 集成完成（Sprint 0 已完成） |
| 卖货订单 / 制单订单 | `seller/`, `document/` | JdbcTemplate | ✅ 稳定 |
| 业务流程（两套） | `businessflows/` | JdbcTemplate | ✅ 稳定 |

### finance 模块已有表（已归档至 `db/migrations/018_finance_core.sql`，Sprint 0 完成）

`finance_fee_type` / `finance_monthly_statement` / `finance_customer_bill` /
`finance_customer_transaction` / `finance_supplier_bill` / `finance_supplier_transaction` /
`finance_sales_commission` / `finance_sales_commission_transaction` /
`finance_sales_cost_transaction` / `finance_transaction` / `finance_waybill_audit` /
`finance_account` / `finance_account_transaction` / `finance_payable_report` /
`finance_receivable_report` / `finance_price_maintenance` / `finance_approval` /
`finance_currency` / `finance_currency_exchange`

> 原 `src/main/java/…/finance/sql/*.sql`（8 个文件）已删除，统一入 `018_finance_core.sql`。
> 所有 19 张表均启用 RLS，`infra/docker-compose.yml` 已更新挂载。

---

## 二、架构风险（须优先修复，否则后续开发会带病运行）

### RISK-1：SQL 迁移文件位置错误（**阻塞级**）✅ 已修复（2026-05-14）

**问题**: `finance/sql/260508–260513.sql` 等 8 个文件放在 `src/main/java/…/finance/sql/`，Docker Compose 初始化只扫描 `db/migrations/`，**新部署环境不会建任何 finance 表**。

**已实施**:
1. 8 个 SQL 文件合并去重 → `db/migrations/018_finance_core.sql`（19 张表 + `update_modified_column()` 触发器函数）
2. 删除 `src/main/java/…/finance/sql/` 目录
3. `infra/docker-compose.yml` 新增挂载 `018_finance_core.sql`

**工作量**: 0.5 天

---

### RISK-2：finance 表缺少 RLS 策略（**安全级**）✅ 已修复（2026-05-14）

**问题**: 现有 `002_rls.sql` 为所有业务表启用 RLS，finance 新表没有对应 policy，导致：
- `set_config('app.current_tenant_id', ...)` 对 finance 查询无效
- 多租户间数据无隔离

**已实施**:
在 `018_finance_core.sql` 末尾为全部 19 张 finance 表批量追加：
```sql
ALTER TABLE finance_fee_type ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance_fee_type FORCE  ROW LEVEL SECURITY;
CREATE POLICY finance_fee_type_tenant_isolation ON finance_fee_type
    USING (app_tenant_matches(tenant_id)) WITH CHECK (app_tenant_matches(tenant_id));
-- （同模式 × 19 张表）
```
复用已有 `app_tenant_matches()` 函数（定义于 `002_rls.sql`），覆盖 service_role 绕过与 tenant 隔离两种情形。

**工作量**: 0.5 天

---

### RISK-3：finance 控制器未在 SecurityConfig 注册（**安全级**）✅ 已修复（2026-05-14）

**问题**: `@Order(2)` 链的 `anyRequest().authenticated()` 已覆盖所有路由，但 finance 控制器的路径前缀 `/api/finance/**` 需要明确声明，否则 Swagger 报错且未来权限细化无入口。

**已实施**:
在 `SecurityConfig.java` 的 `@Order(2)` 链中 `anyRequest()` 之前明确添加：
```java
.requestMatchers("/api/finance/**").authenticated()
```
为后续按控制器或方法级别追加 `@PreAuthorize` 留有入口。

**工作量**: 0.25 天

---

### RISK-4：MyBatis-Plus 与 JdbcTemplate 并存（**架构级**）✅ 短期补丁已完成（2026-05-14）

**问题**: 现有核心模块全用 JdbcTemplate（无 ORM），finance 模块用 MyBatis-Plus。两套数据访问策略并存会导致：
- 事务管理行为不一致（JdbcTemplate 靠 `@Transactional`，MP 有自己的事务插件）
- `set_config('app.current_tenant_id', ...)` 需要在每个 MP Service 调用前主动执行，目前 finance 服务 **没有做**
- 新人理解成本加倍

**已实施（短期补丁）**:
新建 `finance/interceptor/FinanceTenantInterceptor.java`（MyBatis `@Intercepts` 拦截 `Executor.query` + `Executor.update`），在每次 SQL 执行前自动向同一连接注入：
```java
select set_config('app.current_tenant_id', ?, true)
```
通过 `MybatisPlusConfig` 注册为 Spring Bean，**finance 所有 Mapper 调用无需手动处理 tenant**。

**长期方向**: 统一为 JdbcTemplate（跟随项目既有约定），在排期允许时逐模块迁移。

**工作量（短期补丁）**: 1 天

---

### RISK-5：`UserContext.getTenantId()` 返回 String，需转为 UUID 写入表（**小坑**）✅ 已修复（2026-05-14）

finance 服务里大量 `UUID.fromString(request.getTenantId())` 硬编码了入参，但 `UserContext.getTenantId()` 返回 String（从 JWT）。需统一一处工具方法，避免散落的 `UUID.fromString`。

**已实施**:
新建 `finance/common/TenantUtils.java`，提供：
```java
public static UUID currentUuid() {
    return UUID.fromString(UserContext.getTenantId());
}
```
全部 15 个 finance Service 替换为 `TenantUtils.currentUuid()`，无散落的 `UUID.fromString`。  
（方法名 `currentUuid` 而非 `currentUUID`，符合 PMD P3C lowerCamelCase 规范）

**工作量**: 0.25 天

---

## 三、痛点 → 功能 Gap 分析与排期

> 优先级：P0=阻塞业务 / P1=高频影响 / P2=中频 / P3=优化锦上添花

### A. 计费引擎

| 编号 | 痛点 | 当前缺口 | 涉及文件 | 优先级 | 工作量 |
|------|------|---------|---------|--------|--------|
| A1 | 品名附加费自动匹配 | `rates/` 无 `product_keyword_rules` 表，`RateEngine` 无品名匹配逻辑 | 新建 `db/migrations/019_product_keyword_rules.sql`；`rates/KeywordSurchargeService.java` | P1 | 3天 |
| A2/A3 | 附加费叠加 vs 取大策略 | `rate_card_lines` 无 `combination_strategy` 字段；`RateEngine.quote()` 直接求和 | `019` 或 `020` migration 加字段；`RateEngine` 改为分组后按策略聚合 | P1 | 2天 |
| A4 | 按箱最低计费重 | `rate_card_lines` 缺 `min_weight_per_box`, `min_amount_per_box` 字段；引擎按票算 | migration 加字段；`RateEngine.quote()` 增加逐箱计费循环 | P1 | 2天 |
| A5 | 三档偏远（REMOTE/SUPER_REMOTE/EMBARGO） | `remote_zones` 只有 zone 字符串，无 EMBARGO 枚举 | `remote_zones.zone_type` 字段加 EMBARGO 值；`RateEngine` 在下单时拦截 EMBARGO | P1 | 1天 |
| A6 | 多计费单位（KG/LB/CBM/PIECE） | `RateEngine` 只支持 KG；`rate_card_lines` 无 `uom` 字段 | migration 加 `uom` ENUM；`RateEngine` 按 UOM 分支计算 | P1 | 2天 |
| A7 | Dim Factor 多版本 | `rate_cards.dim_factor` 存在，但无 `dim_split_ratio`；计算公式固定 | migration 加 `dim_split_ratio`；`RateEngine` 公式参数化 | P2 | 1天 |
| A8 | 燃油月度版本化 | `fuel_surcharge_rates` 已有，但无自动月度应用到账单 | `FinanceWaybillAuditService` 增加燃油行自动写入；migration 加 `fuel_surcharge_rate_id FK` | P2 | 1.5天 |
| A9 | 保险费独立计费行 | 无 `insurance_rates` 表；订单无 `insured` 字段 | `019` migration 加 `insurance_rates`；`charges` 写保险行 | P2 | 1.5天 |
| A10 | 报关合并费共享 | 无 `customs_group` 概念；每票独立计费 | 新 `customs_groups` 表；`RateEngine` 识别合并关系 | P2 | 2天 |
| A11 | 单票单件附加费 | 无相关字段和触发逻辑 | `rate_card_lines` 加 `single_piece_surcharge`；引擎按箱数=1 自动触发 | P2 | 0.5天 |
| A12 | 反倾销/禁运黑名单 | 无相关表和拦截逻辑 | 新建 `product_blacklist` + `embargo_postcodes` 表；下单时校验 | P2 | 2天 |

---

### B. 对账与账单

| 编号 | 痛点 | 当前缺口 | 涉及文件 | 优先级 | 工作量 |
|------|------|---------|---------|--------|--------|
| B1 | 渠道账单模板解析 | `finance_supplier_bill` 只存聚合金额，无原始明细行；无模板版本化 | 新表 `carrier_invoice_templates` + `carrier_invoice_lines`；导入 Service | P1 | 4天 |
| B2 | 主/子单多键匹配 | `orders` 无 `master_tracking_no`, `sub_tracking_no` 字段；无置信度算法 | migration 加字段；新建 `CarrierInvoiceMatchService` | P1 | 3天 |
| B3 | 原始账单 OCR 解析 | 无文件上传入口；无解析管道 | 新建 `POST /api/finance/carrier-invoices/upload`；集成 Apache POI 解析 Excel | P2 | 5天 |
| B4 | 费用英文科目字典 | `finance_fee_type` 存在，但无 `carrier_code`（英文标识）字段 | `finance_fee_type` 加 `carrier_code text`, `channel_code text` 字段 | P1 | 0.5天 |
| B5 | 客户账单自助下载 + 邮件 | 无客户门户；`finance_customer_bill` 无 `version_no`, `status_machine` | 加 `version_no`, `download_token` 字段；新建 `GET /api/customer-portal/bills` | P2 | 3天 |
| B6 | 账单模板市场 | 无模板表 | 新建 `bill_templates` 表；`FinanceCustomerBillController` 加模板渲染接口 | P3 | 2天 |
| B7 | 成本一键导入生成草稿 | 导入后需手工分类 | 在 B1 导入管道完成后，直接写 `finance_supplier_bill` 草稿行 | P1（依赖 B1）| 依赖 |

---

### C. 时效与轨迹

| 编号 | 痛点 | 当前缺口 | 涉及文件 | 优先级 | 工作量 |
|------|------|---------|---------|--------|--------|
| C1 | 可配置 SLA 模型 | 无 `channel_sla_configs` 表；时效写死 | 新建表 + CRUD；`FinanceWaybillAudit` 关联 SLA | P1 | 2天 |
| C2 | 关键时间点采集 | `finance_waybill_audit` 有时间字段但无来源标记 | 加 `source` 枚举（MANUAL/API/OCR）+ `auditor_role` | P2 | 1天 |
| C3 | 多段时效独立展示 | `finance_waybill_audit` 有多个时间字段，但 API 未返回分段计算值 | `FinanceWaybillAuditView` 增加 `segmentDays[]` 计算字段 | P2 | 1天 |
| C4 | 内部赔付条款记录（不对外暴露） | 无 `channel_compensation_rules` 表 | 新表 + 后台只读展示；前端不暴露给客户 | P3 | 1天 |
| C5 | 子单轨迹抓取 | 无子单 tracking 表 | 新建 `sub_shipment_trackings`；关联 `orders.id` | P2 | 2天 |
| C6 | 官方 API 对接（非爬虫） | 无轨迹抓取服务 | 新建 `tracking/` 模块，FedEx/UPS API 客户端 | P3 | 5天 |

---

### D. 订单生命周期

| 编号 | 痛点 | 当前缺口 | 涉及文件 | 优先级 | 工作量 |
|------|------|---------|---------|--------|--------|
| D1 | 退件二次制单挂父单 | `orders` 无 `parent_order_id`, `lifecycle_seq` 字段 | migration 加字段；制单接口支持传 `parentOrderId` | P1 | 1.5天 |
| D2 | 统一轨迹中台 | 两套旧系统状态不一致；新系统尚无轨迹聚合 | C5 完成后，提供统一 `GET /api/tracking/{orderNo}` | P2（依赖 C5）| 依赖 |

---

### E. 预估 vs 实际成本

| 编号 | 痛点 | 当前缺口 | 涉及文件 | 优先级 | 工作量 |
|------|------|---------|---------|--------|--------|
| E1 | 渠道成本价卡（与客户价卡并行） | `rate_cards.side` 已有 AR（应收），缺 AP（应付成本）价卡 | `rate_cards` 加 `side=AP` 数据；`RateEngine` 支持双向计算；`orders` 加 `estimated_cost` 字段 | P1 | 1.5天 |
| E2 | 预估 vs 实际差异告警 | 无对比逻辑；无告警机制 | 新建 `CostVarianceService`；差异超阈值写 `audit_logs` 并推通知 | P2 | 2天 |

---

### F. 成本分摊

| 编号 | 痛点 | 当前缺口 | 涉及文件 | 优先级 | 工作量 |
|------|------|---------|---------|--------|--------|
| F1 | 提单批量分摊 | 无提单（BL）对象；无分摊计算 | 新建 `bills_of_lading` 表 + `bl_cost_allocations`；多票多费用批量分摊 Service | P1 | 3天 |
| F2 | ShipBatch（船期批次） | 无 `ship_batches` 表；运单无批次关联 | 新建 `ship_batches`（含船司/船名/航次/装柜地）；`orders` 加 `ship_batch_id FK` | P1 | 2天 |

---

### G. 多币种财务

| 编号 | 痛点 | 当前缺口 | 涉及文件 | 优先级 | 工作量 |
|------|------|---------|---------|--------|--------|
| G1 | 合同汇率公式 vs 记账汇率分离 | `finance_currency` 只存单一汇率；无公式化合同汇率 | `finance_currency` 加 `contract_rate_formula text`；计算时区分两种汇率 | P1 | 1.5天 |
| G2 | 三口径账单（合同币/记账币/汇兑差） | `finance_customer_bill` 只有单一 `currency` 和 `bill_amount` | 加 `contract_currency`, `contract_amount`, `accounting_currency`, `accounting_amount`, `fx_gain_loss` 字段 | P1 | 1天 |

---

### H. 销售提成

| 编号 | 痛点 | 当前缺口 | 涉及文件 | 优先级 | 工作量 |
|------|------|---------|---------|--------|--------|
| H1 | 提成方案配置化 + 五口径计算 | `finance_sales_commission` 只存结果；无提成方案配置表；无自动计算引擎 | 新建 `commission_schemes`（比例/阶梯/封顶/挂回款）；新建 `CommissionCalculationService`；月度批量计算任务 | P1 | 4天 |

---

### I. 平台能力

| 编号 | 痛点 | 当前缺口 | 涉及文件 | 优先级 | 工作量 |
|------|------|---------|---------|--------|--------|
| I1 | 批量操作 + 后台异步 | 无异步任务队列；批量接口阻塞 | 引入 Spring `@Async` + `TaskExecutor`；大批量操作写 `async_jobs` 表返回 jobId；前端轮询 | P2 | 2天 |
| I2 | 功能与新智慧/金智慧对齐 | MVP 缺时效导出、提单分摊（见 C1、F1） | 优先落地 C1、F1 | P1（依赖上述）| 依赖 |
| I3 | 两种收费模式统一看板 | 前端 `apps/web` 尚无财务看板页面 | 新增 `views/finance/Dashboard.vue`；聚合应收/应付/利润/提成四象限 | P2 | 3天 |

---

## 四、执行路线图（建议排期）

### Sprint 0 — 架构补齐（1周，先行）✅ 已完成（2026-05-14）

> 不做此步，后续所有 sprint 都在沙地上盖楼

| 任务 | 负责 | 估时 | 状态 |
|------|------|------|------|
| RISK-1：SQL 迁移归档到 `db/migrations/018_finance_core.sql` | 后端 | 0.5天 | ✅ |
| RISK-2：所有 finance 表加 RLS policy | 后端 | 0.5天 | ✅ |
| RISK-3：SecurityConfig 明确 finance 路径 | 后端 | 0.25天 | ✅ |
| RISK-4：`FinanceTenantInterceptor` 自动注入 `set_config` | 后端 | 1天 | ✅ |
| RISK-5：`TenantUtils.currentUuid()` 统一工具方法 | 后端 | 0.25天 | ✅ |
| 静态检查修复：Checkstyle 43项 + PMD 2项 + SpotBugs 8项 | 后端 | — | ✅ |
| 验证：`./mvnw verify` 22 项测试全绿 | 后端 | 0.5天 | ✅ |

---

### Sprint 1 — 计费引擎强化（2周）

> 解决 A1–A7，让报价从"大概正确"变成"精确自动"

优先级顺序：A4（按箱低消）→ A6（多 UOM）→ A2/A3（叠加策略）→ A1（品名规则）→ A5（三档偏远）→ A7（Dim Factor 参数化）

**关键表变更**（新 migration `019_billing_engine_v2.sql`）:
```sql
-- 现有 rate_card_lines 加字段
ALTER TABLE rate_card_lines ADD COLUMN min_weight_per_box numeric;
ALTER TABLE rate_card_lines ADD COLUMN min_amount_per_box numeric;
ALTER TABLE rate_card_lines ADD COLUMN uom text DEFAULT 'KG';  -- KG/LB/CBM/PIECE
ALTER TABLE rate_card_lines ADD COLUMN combination_strategy text DEFAULT 'STACK'; -- STACK/MAX
ALTER TABLE rate_card_lines ADD COLUMN dim_split_ratio numeric DEFAULT 1.0;
ALTER TABLE rate_card_lines ADD COLUMN single_piece_surcharge numeric;

-- 新表
CREATE TABLE product_keyword_rules (...);
ALTER TABLE remote_zones ADD COLUMN zone_type text DEFAULT 'REMOTE'; -- REMOTE/SUPER_REMOTE/EMBARGO
```

---

### Sprint 2 — 订单数据模型完善（1.5周）

> D1（退件父单）、E1（AP 成本价卡）、F2（ShipBatch）、B4（费用科目字典）

**关键表变更**（`020_order_model_v2.sql`）:
```sql
ALTER TABLE orders ADD COLUMN parent_order_id uuid REFERENCES orders(id);
ALTER TABLE orders ADD COLUMN lifecycle_seq smallint DEFAULT 1;
ALTER TABLE orders ADD COLUMN estimated_cost numeric;
ALTER TABLE orders ADD COLUMN ship_batch_id uuid;

CREATE TABLE ship_batches (...);  -- 船司/船名/航次/装柜地/ETD/ETA
ALTER TABLE finance_fee_type ADD COLUMN carrier_code text;
ALTER TABLE finance_fee_type ADD COLUMN channel_code text;
```

---

### Sprint 3 — 对账管道 MVP（2.5周）

> B1（账单模板解析）、B2（多键匹配）、B7（一键导入）、F1（BL 分摊）

**最复杂一期**，建议 B1 拆两个子任务：
1. 模板配置 CRUD（字段映射可视化）
2. Excel 文件解析 + 明细行入库 + 匹配逻辑

---

### Sprint 4 — 多币种 + 提成引擎（2周）

> G1、G2、H1

提成计算引擎核心难点在"五口径"数据对齐（应收/销售成本/预估成本/实际成本/利润），需先确认口径定义再编码。

---

### Sprint 5 — 时效轨迹 + 平台能力（2周）

> C1、C2、C3、C5、I1（异步队列）、I3（财务看板）

---

### Sprint 6 — 完善与长尾（持续）

> A8–A12、B3（OCR）、B5（客户门户）、C6（官方 API）、E2（差异告警）、I2

---

## 五、跨切面约束

1. **每个 Sprint 的 migration 文件必须放入 `db/migrations/` 并更新 `docker-compose.yml`**，禁止放入 `src/` 树。

2. **所有写操作 Service 方法必须加 `@Transactional(rollbackFor = Exception.class)`**，只读方法加 `@Transactional(readOnly = true)`。

3. **MyBatis-Plus Service 的 tenant 隔离通过 `FinanceTenantInterceptor` 自动完成**，无需在 Service 方法中手动调用。新增 finance Mapper 时无需额外处理。

4. **`./mvnw verify`（含 Checkstyle + PMD p3c + SpotBugs）全程绿色**，每个 Sprint 合并前必须跑通。

5. **finance 模块常量规范**（P3C 要求）：所有状态码（1=待审核/2=待核销/3=已核销等）必须定义为 `static final int` 常量或 enum，禁止魔数。

6. **大批量/异步操作**（Sprint 5 I1）引入后，接口设计统一返回 `{ jobId, status }` 模式，客户端轮询 `/api/jobs/{jobId}`。

---

## 六、不在本期范围

- B3（OCR / 大模型账单解析）：评估接入成本后单独排期
- C6（FedEx/UPS 官方 API 对接）：需先申请 API 资质
- B6（账单模板市场）：待 B5 客户门户上线后再做
- 旧系统（新智慧/金智慧）数据迁移：待拿到旧系统数据字典后单独规划
