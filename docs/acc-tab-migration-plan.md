# ACC 80 tab 前后端对齐迁移计划

生成日期：2026-05-21（最后更新 2026-05-28）
来源：`apps/web/src/App.vue` 的 `accTabs` 数组（78 个 tab）+ 现有数据库表盘点。

## 0. 当前进度（2026-05-28 update）

✅ **已实现端到端：78 / 78 tab**（全部）。
✅ DB migrations：**30 个**（001…030）。最新两个：
  - `db/migrations/029_acc_rate_full_logic.sql` — RateEngine 全量分支（客户价/组价/AP 成本价 /
    渠道账号限额 / 电池仿牌限制 / 偏远规则 / 佣金规则 / 多段计费类型）
  - `db/migrations/030_acc_documentcharges.sql` — Express_Charge 闭环：charges 补
    paid_amount/unpaid_amount/settlement_status + fx_rate_snapshots + 自动同步触发器
✅ **5 框架全接**：audit / state-machine / cascade / field-gate / money-snapshot
✅ **复用类型基类模式**：acc_fines（c/s 2 种）+ acc_finance_txns（c/s × 3 type = 6 种）共 8 个
   controller 共享 2 张表
✅ **跨表级联**：banks 删除前检查 expenses/transfers/dividends 关联，阻塞删除并显示具体计数
✅ **客户外部 API**：14 个端点 1:1 复刻 ACC `api/APIClass.php`
✅ **documentcharges 模块**：5 个端点（generate-from-order / void / invoices/generate /
   invoices/{id}/settle / profits/summary）；customer invoice + 收款 + 反核销闭环

## 0.1 仍待生产化的能力（按 `docs/acc-gap-report-for-claude-2026-05-28.md`）

- 真实 UPS/FedEx/EDI 渠道接入（当前是 Demo gateway）
- 真实面单 provider（当前 Noop label gateway）
- Submit 拆分 AR/AP 多费用行（当前是合并一笔）
- 供应商账单 partner-invoices/generate + settle（数据模型就绪，service 待补）
- 12 个后台 tab 部分字段仍是占位（AccOrdersController.sellCharge/costCharge/branch、
  AccCustomersController.contact/balance/salesman 等 —— 详见 §6）
- Scale.php / sumy.php 独立 API 未迁

## 1. 后端模板

任何新 ACC tab 复用 `com.xqt.saas.acc.AccCustomerGroupsController` 这个模板，含 5 个端点：
- `GET /api/acc/<tab>` 列表分页（参数 page/pageSize/keyword/dateFrom/dateTo）
- `GET /api/acc/<tab>/{id}/raw` 原始详情
- `POST /api/acc/<tab>` 新建
- `PUT /api/acc/<tab>/{id}` 更新
- `DELETE /api/acc/<tab>/{id}` 删除

响应统一通过 `AccPaging.result(data, total)` 输出 `{data, total}`，匹配前端 `fetchAccData`。

审核动作（如果该实体在 `AuditService.AUDITABLE_ENTITIES` 注册）自动通过 `AccAuditController`：
- `POST /api/acc/<tab>/{id}/audit-biz`
- `POST /api/acc/<tab>/{id}/undo-biz`
- `POST /api/acc/<tab>/batch-audit`
- `GET  /api/acc/<tab>/{id}/audit-history`

## 2. tab → 后端表 映射现状

### 2.1 业务核心（P1 完成）

| tab | 表 | controller | 审核 |
|---|---|---|---|
| orders | orders | AccOrdersController | ✅ |
| shipments | shipments | AccShipmentsController | ✅ |
| packages | shipments（视图）| AccPackagesController | ✅（2026-05-28 加） |
| stowages | stowages | AccStowagesController | ✅ |
| transits | acc_transits | AccTransitsController | ✅ |
| forecasts | acc_forecasts | AccForecastsController | ✅ |
| dispatches | acc_dispatches | AccDispatchesController | ✅ |
| charges | charges | AccChargesController | ✅ |
| costs | charges (side=AP) | AccCostsController | ✅ |
| bills | customer_invoices | AccBillsController | ✅ |
| payments | partner_payments | AccPaymentsController | ✅ |
| receiveds | payments | AccReceivedsController | ✅ |
| profits | （聚合视图）| AccProfitsController | — |

### 2.2 异常流 + 财务扩展（023 / 024 批次完成）

collects / detains / asks / reparations / fees /
customer-fines / supplier-fines / customer-adjusts / supplier-adjusts /
customer-refunds / supplier-refunds / customer-rebates / supplier-rebates /
expense-categories / fee-item-types

### 2.3 资金管理（025 批次完成）

expenses / banks / transfers / dividends / borrowings / cycles / assets / received-sms

### 2.4 HR + 提成（026 批次完成）

employees / attendances / wages / commissions / commission-rules /
socials / social-persons / funds / fund-persons

### 2.5 物流扩展（027 批次完成）

stowage-categories / stowage-steps / ports / quick-orders / void-orders / tracks

### 2.6 客户产品 + 系统杂项（028 批次完成）

channel-accounts / products（只读视图）/ product-items /
potentials / sold-tos / notices /
logistics-interfaces / tasks / templates /
zones（只读派生视图）

### 2.7 字典 / 主数据

customers / suppliers / channels / currencies / customer-groups /
countries / postcodes / hscodes / bank-names / districts /
warehouses / acc-branches / departments /
returns / remotes / fuels / fee-types

## 3. 当前 30 个 migrations 摘要

| # | 文件 | 内容 |
|---|---|---|
| 001 | init | 基础租户 + RLS + 主业务表骨架 |
| 002-017 | 早期 | 财务核心、面单、客户 API 客户外部签名、汇率等 |
| 018 | finance_core | charges/customer_invoices/payments 等 |
| 019 | acc_labels | 面单 / label_files |
| 020 | acc_stowage | stowages/stowage_categories/stowage_ports |
| 021 | acc_audit_framework | audit_events / 5 框架 |
| 022 | acc_masterdata | countries/postcodes/hscodes/bank_names/districts/customer_groups/warehouses/returns |
| 023 | acc_exceptions_and_finance | collects/detains/asks/reparations/fees/fines |
| 024 | acc_finance_txns_and_dicts | finance_txns/expense_categories/fee_item_types |
| 025 | acc_finance_mgmt | expenses/banks/transfers/dividends/borrowings/cycles/assets/received_sms |
| 026 | acc_hr | employees/attendances/wages/commissions/socials/funds 等 9 表 |
| 027 | acc_logistics | stowage_steps/transits/dispatches/forecasts/track_items |
| 028 | acc_customer_product | channel_accounts/product_items/sold_tos/potentials/notices/logistics_interfaces/scheduled_tasks/message_templates |
| 029 | **acc_rate_full_logic** | customer_rate_cards/customer_group_rate_cards/channel_account_limits/channel_account_daily_usage/service_restrictions/rate_commission_rules/remote_rate_rules + rate_card_lines 多段计费 6 列 |
| 030 | **acc_documentcharges** | charges 补 paid_amount/unpaid_amount/settlement_status/source_ref/rate_snapshot_id/order_id/customer_id + fx_rate_snapshots + partner_invoice_lines.charge_id + 自动同步触发器 |

## 4. 现在的 controller 数量

- ACC retrofit controller：**50+ 个**（apps/backend/src/main/java/com/xqt/saas/acc/Acc*Controller.java）
- 通用审核：`AccAuditController` 一个 controller 统一处理所有 tab 的 audit-biz / undo-biz / batch-audit / audit-history
- documentcharges：`DocumentChargeController` 5 个端点

## 5. 端点访问层 = AccTenantTxFilter 覆盖

`AccTenantTxFilter` 把以下路径包进事务并设 `app.current_tenant_id`：
- `/api/acc/**`
- `/api/document/**`（2026-05-28 加）
- `/api/finance/dashboard`、`/api/finance/branches`

这些路径上的 SQL 可以省略 `WHERE tenant_id = ?`，RLS 自动过滤。

## 6. 仍需补齐的占位字段

按 `docs/acc-gap-report-for-claude-2026-05-28.md` §4：

| 文件 | 占位字段 |
|---|---|
| `AccOrdersController.java` | product="" / sellCharge=0 / costCharge=0 / branch="" |
| `AccShipmentsController.java` | supplierName="" |
| `AccCustomersController.java` | contact/mobile/balance/settlement/branch/group/salesman |
| `AccSuppliersController.java` | contact/mobile/phone/product/balance |
| `AccWarehousesController.java` | consignee/company/postcode |
| `AccBranchesController.java` | contact/phone/address/remark |
| `AccReturnsController.java` | amount=0 |
| `AccReceivedsController.java` | bankName="" |
| `AccPaymentsController.java` | auditName="" |
| `AccBillsController.java` | salesman="" |

阶段 2 修复。

## 7. 关键不对齐与决策记录

- 旧 ACC 的 `Customer` 表有 contact/mobile/balance/settlement/branch/group/salesman 等"业务运营"字段；新 `customers` 表只有 code/name/credit_limit/account_mode/default_currency。
  - **决策**：业务运营字段进 `customer_contacts` / `customer_settlement_profiles`（已建）或 `addresses`。`AccCustomersController.project()` 拼接成前端期望的扁平结构。
- 旧 `Currency` 表有 symbol/rate/decimal；新 `finance_currency` 没有汇率列（汇率在 `exchange_rates` 或 `finance_currency_exchange`）。
  - **决策**：`/api/acc/currencies` 的 list 端点 join 当日汇率读 rate。symbol 加列。
- 旧 ACC 用整数 ID，新平台多为 UUID。前端 `accColumns[*]` 都用 `key: "id"` 不关心类型，OK。
- 前端 `apiFetch` 不带 tenant header；后端 `BearerAuthFilter` 从 token 解出 tenant_id 写 `app.current_tenant_id`，所以 controller SQL 直接读 `current_setting('app.current_tenant_id')::uuid` 即可。
- 旧 ACC 的"装箱单 packages"是 shipments 的视图，审核 packages 等同于审核 shipments（2026-05-28 加映射）。
