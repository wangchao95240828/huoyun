# ACC 最新差距与修复计划（给 Claude 用）

生成日期：2026-05-28  
仓库：`/Users/chaowang/新航线/xqt-saas`  
旧系统对照：`/Users/chaowang/新航线/acc`

## 1. 最新结论

本报告基于 2026-05-28 二次复核后的代码扫描结果。此前 `docs/acc-gap-report-for-claude-2026-05-27.md` 以及本文件早先版本中关于 `AccStatsController`、`packages` 审核映射、前端审核 tab、后台字段占位的部分已经部分过时。

当前状态：

- ACC 后台 tab：`78/78` 均有 `/api/acc/{tab}` 后端路由。
- 额外路由：`/api/acc/stats`，已从零值占位改为真实聚合。
- DB migration：已从 28 个增加到 35 个。
- 新增：
  - `db/migrations/029_acc_rate_full_logic.sql`
  - `db/migrations/030_acc_documentcharges.sql`
  - `db/migrations/031_acc_customer_fields.sql`
  - `db/migrations/032_acc_partner_contacts.sql`
  - `db/migrations/033_acc_warehouse_org_fields.sql`
  - `db/migrations/034_acc_return_amount.sql`
  - `db/migrations/035_acc_payment_bank.sql`
  - `apps/backend/src/main/java/com/xqt/saas/documentcharges/*`
  - `apps/backend/src/test/java/com/xqt/saas/documentcharges/DocumentChargeServiceTests.java`
- `RateEngine` 已从 MVP 升级到“客户价 / 组价 / 成本价 / 佣金 / 渠道账号限制 / 电池仿牌限制 / 偏远规则 / 多段计费”的框架。
- `documentcharges` 已开始落地客户账单、收款核销、费用确认和利润聚合。
- 阶段 1 低风险项已基本完成：`AccStatsController` 真实聚合、`AccAuditController` 补 `packages` 映射、`App.vue` 补入报告列出的审核 tab。
- 阶段 2 后台字段已有明显进展：orders、shipments、customers、suppliers、warehouses、branches、returns、receiveds、payments 多数已从真实表或 metadata join。

所以当前不是“没做”，而是“还没完全闭环 / 还没生产级等价 ACC”。

## 2. 当前仍然存在的硬缺口

### 2.1 独立 API 未迁

仍未迁：

1. `acc/api/Scale.php`
   - 称重设备接口。
   - 建议新路径：`/api/device/scale/*` 或 `/api/customer-api/scale/*`。

2. `acc/api/sumy.php`
   - 第三方推单接口。
   - 建议新路径：`/api/customer-api/external-orders/sumy`。

这两个是当前明确的“入口级硬缺口”。

### 2.2 生产主路径仍未闭环

当前仍然是硬缺口：

1. Submit / quote 生产化：
   - `/api/customer-api/rates/quote` 对 blockers 的语义仍未统一，当前更像 200 body 返回。
   - Submit 非 blocker 异常或 `quote == null` 时仍会 fallback 到 `estimatePrepayAmount()`。
   - Submit 成功后未确认会累加 `channel_account_daily_usage`。
   - Submit 仍主要写 1 条 AR + 1 条 AP，不是按 ACC `Express_Charge.Type` 拆分多费用行。
2. documentcharges AP / 供应商闭环：
   - 客户账单和客户收款已有。
   - 供应商账单 generate / settle 仍未完整实现。
   - AP charges 进入 `partner_invoice_lines` 与付款核销回写仍缺。
3. 真实渠道和面单：
   - Carrier 仍以 Noop/Demo 为主路径。
   - Label 仍是 Noop。

## 3. 已有进展但仍需修的模块

### 3.1 RateEngine 已升级，但仍需核对生产可用性

相关文件：

- `apps/backend/src/main/java/com/xqt/saas/rates/RateEngine.java`
- `apps/backend/src/main/java/com/xqt/saas/rates/RateRepository.java`
- `apps/backend/src/main/java/com/xqt/saas/rates/RateQuoteRequest.java`
- `apps/backend/src/main/java/com/xqt/saas/rates/RateQuoteResponse.java`
- `apps/backend/src/test/java/com/xqt/saas/rates/RateEngineTests.java`
- `db/migrations/029_acc_rate_full_logic.sql`

已补能力：

- 客户专属价。
- 客户组价。
- 普通销售价。
- AP 成本价。
- 佣金规则。
- 渠道账号限额。
- 电池/敏感/仿牌限制。
- 邮编优先级。
- 偏远规则表。
- 多段计费类型。
- 命中证据 `MatchEvidence`。
- blockers 返回阻断原因。

二次复核后的状态：

1. Submit 已对 quote blockers 做硬拒绝。
2. `serviceCode`、`channelAccountCode` 已可从 `metadata.acc_compat` 传入。
3. `/api/customer-api/rates/quote` 仍需要统一 blockers 语义，目前仍可能以 200 body 表达阻断。
4. `channel_account_daily_usage` 仍只看到读取/限制检查，未看到 Submit 成功后的 INSERT/UPDATE。
5. `RateEngineTests` 覆盖主要规则，但仍是 mock 单元测试，缺真实 DB 集成测试。
6. `acc/config/Freight.php::getFee` 的特殊边界，如 ProductCode 到 Product/Channel/ChannelAccount 的完整链路，需要再对一次。

优先修复目标：

- 如果 quote 有 blockers，客户报价接口应明确返回错误；Submit 已阻断，但仍需测试保护。
- Submit 成功后更新 `channel_account_daily_usage`。
- 增加至少一个接近 ACC 真实数据的集成样例。

### 3.2 Submit 已接 RateEngine，但仍有 fallback

文件：

- `apps/backend/src/main/java/com/xqt/saas/customerapi/CustomerApiService.java`

当前状态：

- Submit 会构造 `RateQuoteRequest`。
- Submit 会调用 `rateEngine.quote()`。
- Submit 已读取 `metadata.acc_compat` 中的 `serviceCode` / `channelAccountCode`。
- Submit 已在 quote blockers 非空时拒绝下单。
- quote 成功后，预扣金额使用 `quote.totalAmount()`。
- quote 成功后，会把 AR charge 写入 `charges`。
- quote 有 `costTotal()` 时，会写 AP cost charge。

仍存在问题：

1. 非 blocker 异常或 `quote == null` 时仍 fallback 到 `estimatePrepayAmount()`。
2. fallback 会继续允许下单，可能绕过正式报价规则；目前未看到 `strictRateQuote` 配置。
3. 当前主要写 AR 总费用 + AP 总成本，不是严格按 ACC `Express_Charge.Type` 拆分多费用行。
4. 成本价、佣金、燃油、偏远等只进 evidence 或总额，费用行粒度还不够。
5. PreSubmit 仍使用 `estimatePrepayAmount()`，未接 RateEngine。

Claude 修复目标：

1. 将 fallback 策略改成可配置：
   - `strictRateQuote=true` 时，报价失败直接阻断 Submit。
   - dev/demo 环境才允许 fallback。
2. 费用行拆分：
   - AR Freight
   - AR Fuel
   - AR Remote/Surcharge
   - AR Commission 如需要单列
   - AP Freight
   - AP Fuel
   - AP Remote/Surcharge
3. `estimatePrepayAmount()` 不再作为生产主路径。
4. Submit 成功后更新渠道账号用量。

### 3.3 documentcharges 已落地，但供应商闭环仍弱

相关文件：

- `apps/backend/src/main/java/com/xqt/saas/documentcharges/DocumentChargeController.java`
- `apps/backend/src/main/java/com/xqt/saas/documentcharges/DocumentChargeService.java`
- `apps/backend/src/main/java/com/xqt/saas/documentcharges/DocumentChargeRepository.java`
- `apps/backend/src/test/java/com/xqt/saas/documentcharges/DocumentChargeServiceTests.java`
- `db/migrations/030_acc_documentcharges.sql`

已补能力：

- `POST /api/document/charges/generate-from-order`
- `POST /api/document/charges/{id}/void`
- `POST /api/document/invoices/generate`
- `POST /api/document/invoices/{id}/settle`
- `GET /api/document/profits/summary`
- ESTIMATED → CONFIRMED
- 客户账单生成
- 客户收款/部分核销/反核销
- charge void
- 利润聚合

仍需补齐：

1. 供应商账单生成接口仍未完整落地。
2. 供应商付款/核销闭环仍未完整落地。
3. `GeneratePartnerInvoice` / `SettlePartnerInvoice` 请求 DTO 已存在，但 Controller / Service / Repository 主流程未完整实现。
4. `partner_invoice_lines.charge_id` 已加字段，但业务层未完全使用。
5. customer invoice settle 已有，但 AP settle 还需要对齐 `Pay.php`。
6. 资金流水/账本层仍偏弱，更多是在更新 balance / paid_amount。
7. `AuditService` 注入了，但 `generateFromOrder()` 注释写“审核动作由前端再调 audit-biz”，需要确认是否符合 ACC 旧流程。
8. `fx_rate_snapshots` 新建了，但 service 中未明显使用。

Claude 修复目标：

- 增加供应商账单生成：
  - `POST /api/document/partner-invoices/generate`
- 增加供应商付款核销：
  - `POST /api/document/partner-invoices/{id}/settle`
- AP charges 入账后能进入 partner invoice。
- 付款后回写 AP charges 的 `paid_amount` / `unpaid_amount` / `settlement_status`。
- 所有金额动作写汇率快照或明确复用 `MoneySnapshotService`。

### 3.4 真实渠道仍未生产化

相关文件：

- `apps/backend/src/main/java/com/xqt/saas/customerapi/CarrierGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/CarrierGatewayRegistry.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/NoopCarrierGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/DemoUpsCarrierGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/DemoFedexCarrierGateway.java`

当前状态：

- 有 `UPS_DEMO` / `FEDEX_DEMO`。
- registry 可按 `channels.last_mile_method` 路由到 demo gateway。
- 兜底仍是 `NOOP`。

仍需补齐：

1. 没有真实 UPS/FedEx/EDI API。
2. `channels.metadata->gateway` 注释里提到，但代码里目前直接返回 null。
3. `acc_channel_accounts` / `acc_logistics_interfaces` 尚未真正驱动 gateway。
4. provider request/response evidence 未形成统一存储。

Claude 修复目标：

- 用 `acc_channel_accounts` 或 `acc_logistics_interfaces` 作为 provider 配置源。
- 至少实现一个真实/sandbox adapter。
- 保留 Demo/Noop，但生产 channel 不允许 fallback 到 Noop。
- Submit 的 gateway raw response 写入 shipment/carton evidence。

### 3.5 面单仍是 Noop

相关文件：

- `apps/backend/src/main/java/com/xqt/saas/labels/LabelGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/labels/NoopLabelGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/labels/LabelService.java`

当前状态：

- `NoopLabelGateway` 仍是默认。
- 可以生成最小 PDF/ZPL，适合 demo。
- 真实渠道 label API 未接。

Claude 修复目标：

- `LabelGateway` 像 `CarrierGateway` 一样按渠道路由。
- 接入真实/sandbox label provider。
- method=0 / method=1 保持兼容。
- 非 PDF 图片/ZPL 转 PDF 的 ACC 行为补齐。

## 4. ACC 后台 tab 仍有字段/聚合债

当前 78 个 tab 都有路由，但部分字段仍是空/0。

二次复核后，阶段 2 大部分已经完成，剩余应收尾：

| 文件 | 当前问题 |
| --- | --- |
| `AccStatsController.java` | 已修复为真实聚合 |
| `AccOrdersController.java` | `sellCharge` / `costCharge` / `branch` / `product` 已有真实来源，建议补测试保护 |
| `AccShipmentsController.java` | `supplierName` 已有真实来源，建议补测试保护 |
| `AccCustomersController.java` | 联系人、余额、销售等已有 join，建议补测试保护 |
| `AccSuppliersController.java` | 联系人、产品、余额等已有 join，建议补测试保护 |
| `AccWarehousesController.java` | consignee/company/postcode 已有字段，建议补测试保护 |
| `AccBranchesController.java` | contact/phone/address/remark 已有字段，建议补测试保护 |
| `AccReturnsController.java` | amount 已按 refund - compensate 计算 |
| `AccReceivedsController.java` | bankName 已来自 `financial_accounts` |
| `AccPaymentsController.java` | auditName 已有来源，但需确认 projection 中覆盖顺序无误 |
| `AccBillsController.java` | salesman 空 |

## 5. 审核与前端集成债

### 5.1 `packages` 审核映射已补

`AccAuditController.TAB_TO_TABLE` 已有 `packages -> shipments` 映射，注释说明装箱单审核走 shipment。

仍需：

- 补测试或最小接口验证，避免后续误删。
- 确认 `packages -> shipments` 是否符合业务口径。

### 5.2 前端审核 tab 未覆盖所有可审核模块

`App.vue` 中：

- `auditableTabs`
- `bizAuditTabs`
- `batchAuditTabs`

二次复核后，上一版报告点名的 8 个 tab 已加入 `bizAuditTabs` / `batchAuditTabs`：

- `employees`
- `attendances`
- `social-persons`
- `fund-persons`
- `channel-accounts`
- `logistics-interfaces`
- `templates`
- `packages`

仍需确认：

- `collects` 在 `auditableTabs` 中，但不在 `bizAuditTabs` / `batchAuditTabs`，页面可能没有审核按钮。
- `auditableTabs` 定义是否实际被使用。
- 不是所有 78 个 tab 都应该审核，需按 ACC 旧系统口径确认。

## 6. 文档仍过时

以下文档状态：

- `docs/acc-tab-migration-plan.md`
  - 主进度已更新为 `78/78`。
  - 仍需把 migration 数从 `30` 更新到 `35`。
  - 仍需同步 031-035 的后台字段补丁。
- `docs/acc-remaining-logic-implementation-guide.md`
  - 仍过时。
  - 仍把早期 `50 tab`、`28 tab 未迁`、RateEngine/documentcharges 未实现计划当成当前状态。
- `docs/acc-gap-report-for-claude-2026-05-27.md`
  - 需要被本报告替代

Claude 修复文档时需要改：

- 当前 tab 状态：`78/78`
- migrations：`35`
- 新增 `029_acc_rate_full_logic.sql`
- 新增 `030_acc_documentcharges.sql`
- 新增 `031_acc_customer_fields.sql` 到 `035_acc_payment_bank.sql`
- RateEngine 已升级，但仍需集成/生产化
- documentcharges 已落地，但 AP 闭环和 ledger 仍需补

## 7. 建议 Claude 执行顺序

### 阶段 1：已完成，低风险项回归测试

```text
阶段 1 已基本完成。请只做回归检查和小修：
1. 确认 AccStatsController 聚合 SQL 在空库、有订单、有费用三种场景不报错；
2. 给 AccAuditController 的 packages -> shipments 映射补最小测试或接口验证；
3. 确认 App.vue 中 packages 以及 HR/配置类 tab 的审核按钮可见；
4. 把 docs/acc-tab-migration-plan.md 的 migration 数更新为 35，并记录 031-035。

要求：
- 不改 RateEngine 主逻辑；
- 不改 Submit；
- 不破坏现有 API 响应。
```

### 阶段 2：后台字段收尾

```text
继续根据 docs/acc-gap-report-for-claude-2026-05-28.md 收尾后台 tab 字段：
1. AccBillsController.salesman 仍为空，改为真实来源；
2. AccPaymentsController.auditName 确认 projection 覆盖顺序无误；
3. 为 AccOrdersController / AccShipmentsController / AccCustomersController / AccSuppliersController / AccWarehousesController / AccBranchesController / AccReturnsController / AccReceivedsController / AccPaymentsController 的已修字段补测试或 SQL 验证；
4. 清理 controller 中已经过时的“占位/MVP”注释。

要求：
- 字段必须来自真实表、join 或 metadata；
- 不允许核心字段继续返回空字符串或 0；
- 缺字段时写 migration；
- 补测试或 SQL 验证。
```

### 阶段 3：生产化 RateEngine + Submit

```text
继续根据 docs/acc-gap-report-for-claude-2026-05-28.md 生产化 RateEngine 和 Submit：
1. quote blockers 在 customer-api rates/quote 中明确失败或返回统一错误；
2. Submit 不允许生产环境 fallback 到 estimatePrepayAmount；
3. Submit 按 quote.breakdown 拆分 AR/AP 多费用行；
4. Submit 成功后更新 channel_account_daily_usage；
5. PreSubmit 改为使用 RateEngine 或明确仅 demo fallback；
6. 增加 Submit 调用 RateEngine 并写 AR/AP 多费用行的测试。
```

### 阶段 4：补 AP 账单/付款闭环

```text
继续根据 docs/acc-gap-report-for-claude-2026-05-28.md 补 documentcharges：
1. 增加 supplier/partner invoice 生成；
2. 增加 supplier/partner invoice settle；
3. AP charges 可以进入 partner_invoice_lines；
4. 付款后回写 AP charges paid/unpaid/settlement_status；
5. 金额动作写 fx_rate_snapshots 或 MoneySnapshotService；
6. 补 DocumentChargeServiceTests。
```

### 阶段 5：真实渠道与面单

```text
继续根据 docs/acc-gap-report-for-claude-2026-05-28.md 替换生产主路径中的 Noop：
1. CarrierGatewayRegistry 使用 acc_channel_accounts / acc_logistics_interfaces 路由；
2. 增加至少一个 sandbox/真实 carrier adapter；
3. LabelGateway 支持按渠道路由；
4. 真实 label provider 输出 PDF/ZPL；
5. provider request/response 写 evidence。
```

### 阶段 6：迁 Scale / sumy

```text
最后迁移两个独立 ACC API：
1. acc/api/Scale.php；
2. acc/api/sumy.php。

要求：
- 先写反推文档；
- 再写 controller/service/repository；
- 补契约测试。
```

## 8. 最终验收

完成后需要满足：

1. `./mvnw test` 通过。
2. 78 个 `/api/acc/{tab}` 非 404。
3. `/api/acc/stats` 返回真实值并有回归测试。
4. `/api/acc/orders` 不再返回核心金额 0 和分公司空值，并有回归测试。
5. `AccBillsController.salesman` 不再为空。
6. `RateEngine` quote 的 blockers 语义明确：Submit 和 quote API 都有统一处理。
7. Submit 生产主路径不再 fallback 到简化估算。
8. Submit 生成拆分 AR/AP 费用行。
9. Submit 成功后更新 `channel_account_daily_usage`。
10. 客户账单和供应商账单都能生成并核销。
11. Noop gateway 不再是生产主路径。
12. Scale/sumy 有迁移实现或明确弃用结论。

