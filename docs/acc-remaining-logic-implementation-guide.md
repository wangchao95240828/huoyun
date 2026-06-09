# ACC 剩余逻辑补齐实施文档

生成日期：2026-05-26

适用仓库：`/Users/chaowang/新航线/xqt-saas`

对照来源：`/Users/chaowang/新航线/acc`

目标：把当前 `xqt-saas` 中已经迁移但仍是简化版或占位版的 ACC 逻辑补齐到可对照旧系统的程度，优先覆盖运费试算、费用预扣/账单闭环、已实现后台 tab 的空字段与统计聚合。

## 1. 总结

当前 ACC 迁移分两层：

1. 客户外部 API：核心端点已实现，但部分业务分支是 MVP 或占位。
2. ACC 后台 tab：已有 50 个 tab 接入后端，但部分字段仍为空值/0，占位逻辑需要补齐。

本文只处理“已经有入口但逻辑不完整”的部分，不包含仍未迁移的 28 个后台 tab，也不包含 `api/Scale.php`、`api/sumy.php` 两个独立入口。

优先级建议：

1. P0：补齐运费试算 ACC 全量分支。
2. P0：补齐 `Express_Charge` 对应的预扣、费用、余额流水和账单闭环。
3. P1：补齐已实现后台 tab 中的空字段和 0 占位。
4. P1：补齐统计聚合和利润相关口径。
5. P2：接入真实渠道取号和真实面单网关。

## 2. 当前已知差异

### 2.1 运费试算不是 ACC 全量逻辑

当前实现位置：

- `apps/backend/src/main/java/com/xqt/saas/rates/RateEngine.java`
- `apps/backend/src/main/java/com/xqt/saas/rates/RateRepository.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/CustomerApiController.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/CustomerApiService.java`
- `db/migrations/017_acc_rates_seed.sql`
- `docs/acc-rates-migration.md`

旧 ACC 对照位置：

- `acc/api/APIClass.php?act=Price`
- `acc/config/Freight.php::getFee`
- `acc/inc/Price.php`
- `acc/inc/ProductSave.php`

当前缺口：

1. 不支持客户专属价。
2. 不支持客户组价。
3. 不支持成本价 / 应付价。
4. 不支持佣金。
5. 不支持货代分润。
6. 不支持渠道账号限量：
   - `MaxCount`
   - `MaxPiece`
   - `MaxWeight`
7. 不支持电池过滤。
8. 不支持仿牌/敏感货过滤。
9. 不支持邮编优先级匹配。
10. 不支持 ACC 旧系统 `ProductCode -> Product -> Channel -> Channel_Account` 的完整选择链路。
11. 多段首重/续重或复杂分段计费未完整建模。
12. 偏远费当前是固定百分比，不是 ACC 旧规则。

### 2.2 `documentcharges` / `Express_Charge` 没有完整闭环

当前相关位置：

- `apps/backend/src/main/java/com/xqt/saas/customerapi/CustomerApiService.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/CustomerApiRepository.java`
- `apps/backend/src/main/java/com/xqt/saas/acc/AccChargesController.java`
- `apps/backend/src/main/java/com/xqt/saas/acc/AccCostsController.java`
- `apps/backend/src/main/java/com/xqt/saas/acc/AccBillsController.java`
- `apps/backend/src/main/java/com/xqt/saas/acc/AccReceivedsController.java`
- `apps/backend/src/main/java/com/xqt/saas/acc/AccPaymentsController.java`
- `db/migrations/010_finance_clone_core.sql`
- `db/migrations/018_finance_core.sql`
- `db/migrations/021_acc_audit_framework.sql`

旧 ACC 对照位置：

- `acc/api/APIClass.php` 中 `PreOrder` / `Submit` / `Price` / `Balance`
- `acc/inc/Charge.php`
- `acc/inc/Cost.php`
- `acc/inc/CBill.php`
- `acc/inc/Received.php`
- `acc/inc/Pay.php`
- ACC 表：
  - `Express_Charge`
  - `Express_Charge_Status`
  - `Customer_Balance`
  - `Customer_Balance_History`
  - 客户账单/收款相关表
  - 供应商账单/付款相关表

当前缺口：

1. Submit 时的预扣金额还是简化估算，未接 `RateEngine` 全量报价结果。
2. `charges` 只保存部分费用事实，尚未完整表达 ACC 的 `Express_Charge.Type` 多费用行。
3. 客户余额扣减没有完整账本闭环。
4. 没有完整生成 `Customer_Balance_History` 等价的流水记录。
5. 费用审核、反审、核销、收款、账单之间的状态关系未完整对齐 ACC。
6. 应收和应付成本的生成链路不完整。
7. 客户账单、供应商账单与费用行的关联不完整。
8. 收款/付款后回写 `paid/unpay/status` 的口径需要统一。
9. 作废、退款、调账、返利等财务变动没有完全回流到账本和账单。

### 2.3 已实现后台 tab 存在空字段或 0 占位

已知代码位置和缺口：

| Controller | 当前占位 | 需要补齐 |
| --- | --- | --- |
| `AccOrdersController` | `sellCharge=0`、`costCharge=0`、`branch=""`、`product=""` | join 费用、成本、分公司、产品 |
| `AccShipmentsController` | `supplierName=""` | 从渠道/供应商关系或成本策略中拿物流商 |
| `AccCustomersController` | contact/mobile/balance/settlement/branch/group/salesman 等空值 | 从客户联系人、结算配置、余额、组织、销售员补齐 |
| `AccSuppliersController` | contact/mobile/phone/product/balance 等空值 | 从 partners、partner_accounts、成本/应付余额补齐 |
| `AccWarehousesController` | consignee/company/postcode 空值 | 扩展 warehouses 或 join addresses |
| `AccBranchesController` | contact/phone/address/remark 空值 | 扩展 organizations 或组织联系方式表 |
| `AccCurrenciesController` | symbol/rate/decimal 兜底 | join 汇率表和币种精度 |
| `AccReturnsController` | `amount=0` | 退件费用、退款或补收逻辑 |
| `AccReceivedsController` | `bankName=""` | join financial_accounts/bank_names |
| `AccPaymentsController` | `auditName=""` | 从审核事件或付款表字段返回 |
| `AccBillsController` | `salesman=""` | join 客户销售归属 |
| `AccStatsController` | 全部零值 | 做真实聚合 |

### 2.4 真实渠道和真实面单仍是占位

当前位置：

- `apps/backend/src/main/java/com/xqt/saas/customerapi/CarrierGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/NoopCarrierGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/DemoUpsCarrierGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/DemoFedexCarrierGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/labels/LabelGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/labels/NoopLabelGateway.java`

旧 ACC 对照位置：

- `acc/api/APIClass.php` 的 `Submit` / `Label`
- `acc/api/getNewLabel.php`
- `acc/inc/online/*`
- `acc/config/api/*`

当前缺口：

1. Submit 生成的是 `NOOP-*` 单号，不是真实渠道单号。
2. Label 生成的是最小占位 PDF 或 ZPL，不是真实渠道面单。
3. UPS/FedEx/自营 EDI 的实际接口、认证参数、错误码和重试机制没有接。
4. 渠道插件返回的 raw response 没有按 ACC 原样保存到 evidence/metadata。
5. 渠道失败时的费用回滚、状态回滚、错误提示还需要和 ACC 对齐。

## 3. 实施任务 P0：补齐运费试算全量逻辑

### 3.1 目标

让以下两个接口输出接近 ACC `act=Price` 的全量业务结果：

- `POST /api/customer-api/rates/quote`
- `POST /api/document/rates/quote`

并让 `Submit` 时的预扣费用使用同一套报价结果，而不是当前简化估算。

### 3.2 建议新增或扩展的数据模型

优先复用现有表：

- `channels`
- `services`
- `service_channel_links`
- `rate_cards`
- `rate_card_lines`
- `fuel_surcharge_rates`
- `remote_zones`
- `charge_items`
- `charge_rules`

如果现有表不够，新增迁移文件，例如：

- `db/migrations/026_acc_rate_full_logic.sql`

建议补充表或字段：

1. `customer_rate_cards`
   - `tenant_id`
   - `customer_id`
   - `service_id`
   - `channel_id`
   - `rate_card_id`
   - `priority`
   - `effective_from`
   - `effective_to`
2. `customer_group_rate_cards`
   - `tenant_id`
   - `customer_group_id`
   - `service_id`
   - `channel_id`
   - `rate_card_id`
   - `priority`
3. `channel_account_limits`
   - `tenant_id`
   - `channel_id`
   - `account_code`
   - `max_count`
   - `max_piece`
   - `max_weight`
   - `active`
4. `service_restrictions`
   - `tenant_id`
   - `service_id`
   - `battery_allowed`
   - `sensitive_allowed`
   - `brand_allowed`
   - `country_code`
   - `postal_pattern`
5. `commission_rules`
   - `tenant_id`
   - `customer_id`
   - `customer_group_id`
   - `service_id`
   - `channel_id`
   - `rule_type`
   - `rate`
   - `fixed_amount`

### 3.3 RateEngine 修改要求

修改 `RateEngine.quote()`，按以下优先级找价格：

1. 客户专属价。
2. 客户组价。
3. 普通销售价。
4. 成本价/应付价作为成本输出，不直接作为客户报价。

入参需要支持：

- `customerId`
- `customerGroupId`
- `serviceCode` 或 ACC `Product`
- `channelCode`
- `countryCode`
- `postalCode`
- `weightKg`
- `volumeCbm`
- `piece`
- `batteryType`
- `specialType`
- `declaredValue`
- `currency`
- `chargeDate`

输出需要支持：

- 销售价：
  - freight
  - fuel
  - remote
  - surcharge
  - commission
  - total
- 成本价：
  - costFreight
  - costFuel
  - costSurcharge
  - costTotal
- 规则命中证据：
  - rateCardId
  - rateCardLineId
  - customerRateMatched
  - groupRateMatched
  - remoteRuleMatched
  - channelAccountMatched
  - restrictionsChecked
- `breakdown`

### 3.4 规则要求

必须补齐以下判断：

1. 产品到渠道的映射：
   - ACC 的 `Product.Code` 不能直接等同 `channels.code`。
   - 新系统需要支持 `serviceCode -> service -> channel`。
2. 客户价优先：
   - 如果客户有专属价格，优先使用。
   - 否则看客户组价格。
   - 否则看普通价格。
3. 电池/敏感货过滤：
   - 不允许时直接返回明确错误码。
4. 渠道账号限制：
   - 单票件数、重量、日票量超过限制时不能选该账号。
5. 邮编匹配优先级：
   - 精确邮编 > 邮编段/regex > 国家默认。
6. 偏远费：
   - 不再写死 15%/25%，优先从规则表读取。
7. 佣金/分润：
   - 按客户、客户组、产品或渠道规则计算。
8. 多段计费：
   - 支持首重/续重或至少支持 `calculation_type`，避免全部按 `unit_price * weight`。

### 3.5 Submit 预扣接入

修改：

- `CustomerApiService.submitOrder()`
- `CustomerApiRepository`

当前 `estimatePrepayAmount()` 是占位，应替换为：

1. 从 `metadata.acc_compat` 读取产品、国家、重量、体积、件数、申报价值。
2. 调用 `RateEngine.quote()`。
3. 使用 quote.total 生成应收费用。
4. 使用 quote.costTotal 生成应付成本。
5. 写入 `charges`：
   - AR 运费
   - AR 燃油
   - AR 偏远
   - AR 附加费
   - AP 成本费
   - AP 燃油/附加费
6. 写入 evidence，保存报价明细和命中规则。
7. 预扣客户余额时使用 AR total。

### 3.6 测试要求

新增或扩展：

- `RateEngineTests`
- `CustomerApiContractTests`
- 新增 `AccRateFullLogicTests`

至少覆盖：

1. 普通价。
2. 客户专属价覆盖普通价。
3. 客户组价覆盖普通价。
4. 客户价优先于客户组价。
5. 偏远邮编。
6. 禁运邮编。
7. 电池不允许。
8. 仿牌/敏感货不允许。
9. 渠道账号超重。
10. 渠道账号超件数。
11. 佣金计算。
12. Submit 调用报价并写 AR/AP 费用。

## 4. 实施任务 P0：补齐 `Express_Charge` / 预扣 / 账单闭环

### 4.1 目标

让 ACC 旧系统中的费用链路在新系统中可追溯：

```text
下单/提交
  -> 运费试算
  -> 生成应收费用 charges(AR)
  -> 生成应付成本 charges(AP)
  -> 客户余额预扣 / 账本流水
  -> 客户账单
  -> 收款/核销
  -> 供应商账单
  -> 付款/核销
  -> 利润统计
```

### 4.2 推荐新增模块

建议新增包：

```text
apps/backend/src/main/java/com/xqt/saas/documentcharges
```

建议类：

```text
DocumentChargeService
DocumentChargeRepository
DocumentChargeController
DocumentChargeResponses
DocumentChargeRequests
```

接口建议：

```text
POST /api/document/charges/generate-from-order/{orderId}
POST /api/document/charges/generate-from-shipment/{shipmentId}
POST /api/document/charges/{id}/audit
POST /api/document/charges/{id}/undo-audit
POST /api/document/charges/{id}/void
POST /api/document/charges/{id}/settle
POST /api/document/invoices/generate
POST /api/document/invoices/{id}/settle
```

客户 API 内部调用不一定暴露全部接口，但 Service 要复用。

### 4.3 数据落地要求

费用事实建议使用现有 `charges` 表，必要时补字段：

- `side`: `AR` / `AP`
- `shipment_id`
- `order_id`
- `charge_item_id`
- `charge_type`
- `currency`
- `amount`
- `paid_amount`
- `unpaid_amount`
- `audit_status`
- `settlement_status`
- `source`
- `source_ref`
- `evidence`
- `rate_snapshot_id`
- `created_by`
- `created_at`

账本建议使用或补齐：

- `financial_accounts`
- `financial_account_records`
- `ledger_entries` / `wallet_transactions`（如果已有同等表，优先复用）

账单建议使用或补齐：

- `customer_invoices`
- `customer_invoice_lines`
- `partner_invoices`
- `partner_invoice_lines`
- `payments`
- `partner_payments`

### 4.4 预扣逻辑要求

ACC 旧逻辑中客户余额方向与展示方向相反，新系统不要继续照搬直接加减余额字段，建议保留：

1. `financial_accounts.balance` 作为当前余额快照。
2. 每次预扣、释放、收款、退款、调账都写流水。
3. `charges.evidence` 保存 ACC 对照字段。
4. 所有资金变动必须有审计记录。

Submit 时：

1. 报价成功。
2. 生成 AR 费用行。
3. 如果客户是预付模式：
   - 检查余额。
   - 冻结或扣减余额。
   - 写资金流水。
4. 如果客户是月结模式：
   - 不扣余额。
   - 费用进入待出账。
5. 取号失败：
   - 不应留下已扣余额。
   - 不应留下已生效费用。
   - 如果保留失败记录，必须是 `VOID`/`FAILED` 状态且可追溯。

### 4.5 账单闭环要求

客户账单：

1. 从已审核 AR charges 生成。
2. 一条费用只能进入一个有效账单。
3. 账单金额 = lines sum。
4. 收款后更新：
   - paid
   - unpaid
   - settlement_status
5. 支持部分收款。
6. 支持反核销。

供应商账单：

1. 从 AP charges/costs 生成。
2. 付款后更新 paid/unpaid。
3. 支持汇率快照。
4. 支持供应商维度汇总。

利润：

```text
profit = AR 已确认金额 - AP 已确认金额 - 退款/赔偿/返利/调账影响
```

需要明确口径：

- 是否按订单。
- 是否按 shipment。
- 是否按账单。
- 是否按审核时间。
- 是否按业务日期。

建议 `AccProfitsController` 默认按 shipment 聚合，支持 `groupBy=branch/customer/channel/month`。

### 4.6 测试要求

新增测试：

- `DocumentChargeServiceTests`
- `CustomerApiSubmitChargeTests`
- `InvoiceSettlementTests`
- `ProfitCalculationTests`

至少覆盖：

1. Submit 成功生成 AR/AP 费用。
2. 预付客户余额扣减。
3. 月结客户不扣余额。
4. 余额不足失败。
5. 取号失败回滚费用和余额。
6. 客户账单生成。
7. 客户部分收款。
8. 客户全额核销。
9. 供应商账单生成。
10. 供应商付款。
11. 反审核/反核销。
12. 利润聚合。

## 5. 实施任务 P1：补齐已实现 tab 的空字段和占位字段

### 5.1 `AccOrdersController`

文件：

- `apps/backend/src/main/java/com/xqt/saas/acc/AccOrdersController.java`

当前问题：

- `product` 空。
- `sellCharge=0`。
- `costCharge=0`。
- `branch` 空。

修复要求：

1. `product`：
   - 优先从 `orders.metadata.acc_compat.product` 读取。
   - 如果已落 shipment/service，则 join `services` 或 `channels`。
2. `sellCharge`：
   - 聚合 `charges.side='AR'`。
   - 只统计有效状态，不统计 void/failed。
3. `costCharge`：
   - 聚合 `charges.side='AP'`。
4. `branch`：
   - 如果 `orders.branch_id` 已有，join `organizations`。
   - 如果没有字段，新增字段或从 customer profile 反推。
5. raw 详情应能返回费用明细。

验收：

- 订单列表显示真实销售产品、应收、成本、分公司。
- 与 `AccChargesController` / `AccCostsController` 的金额合计一致。

### 5.2 `AccShipmentsController`

文件：

- `apps/backend/src/main/java/com/xqt/saas/acc/AccShipmentsController.java`

当前问题：

- `supplierName` 空。

修复要求：

1. 从 `channels` 找承运商/供应商关系。
2. 如果 `channel_cost_policies` 或 `partners` 有关系，优先 join。
3. 如果当前 DB 没关系，补迁移字段：
   - `channels.partner_id`
   - 或 `service_channel_links.partner_id`

验收：

- 出货列表能显示物流商。
- 应付成本与物流商可追溯。

### 5.3 `AccCustomersController`

文件：

- `apps/backend/src/main/java/com/xqt/saas/acc/AccCustomersController.java`

当前问题：

- contact/mobile/balance/settlement/branch/group/salesman 等为空。

修复要求：

1. contact/mobile：
   - join `customer_contacts` 默认联系人。
2. settlement：
   - join `customer_settlement_profiles`。
3. balance：
   - 聚合 `financial_accounts owner_type='CUSTOMER'`。
4. group：
   - join `customer_groups`。
5. branch/salesman：
   - join organizations/users 或补字段。

验收：

- 客户列表核心运营字段不再为空。
- 与 ACC 客户管理页面字段语义一致。

### 5.4 `AccSuppliersController`

文件：

- `apps/backend/src/main/java/com/xqt/saas/acc/AccSuppliersController.java`

当前问题：

- contact/mobile/phone/product/balance 空。

修复要求：

1. contact/mobile/phone：
   - 从 `partner_accounts` 或新增 partner contacts 表读取。
2. product：
   - 统计该供应商关联渠道/产品。
3. balance：
   - 聚合 AP 账单/付款或供应商账户余额。

验收：

- 物流商列表展示联系人、电话、关联产品、余额。

### 5.5 `AccWarehousesController`

文件：

- `apps/backend/src/main/java/com/xqt/saas/acc/AccWarehousesController.java`

当前问题：

- consignee/company/postcode 空。

修复要求：

1. 扩展 `warehouses` 表字段，或关联 `addresses`。
2. 返回公司、收件人、电话、邮编、地址。

验收：

- 仓库管理列表字段与 ACC 仓库页面一致。

### 5.6 `AccBranchesController`

文件：

- `apps/backend/src/main/java/com/xqt/saas/acc/AccBranchesController.java`

当前问题：

- contact/phone/address/remark 空。

修复要求：

1. 扩展 `organizations`：
   - contact
   - phone
   - address
   - remark
2. 或新增 `organization_profiles`。

验收：

- 分店管理可展示联系人、电话、地址、备注。

### 5.7 `AccCurrenciesController`

文件：

- `apps/backend/src/main/java/com/xqt/saas/acc/AccCurrenciesController.java`

当前问题：

- symbol/rate/decimal 兜底。

修复要求：

1. `symbol` 从 `finance_currency` 或补字段。
2. `rate` 从 `exchange_rates` / `finance_currency_exchange` 取当前有效汇率。
3. `decimal` 从币种配置读取。

验收：

- 货币汇率页面显示真实汇率、符号、小数位。

### 5.8 `AccReturnsController`

文件：

- `apps/backend/src/main/java/com/xqt/saas/acc/AccReturnsController.java`

当前问题：

- `amount=0`。

修复要求：

1. 从退件关联费用读取退款/补收金额。
2. 如果没有费用表，新增退件费用关系。

验收：

- 退件金额真实。
- 退件金额能进入费用/账单/利润口径。

### 5.9 `AccReceivedsController` / `AccPaymentsController`

文件：

- `apps/backend/src/main/java/com/xqt/saas/acc/AccReceivedsController.java`
- `apps/backend/src/main/java/com/xqt/saas/acc/AccPaymentsController.java`

当前问题：

- 收款银行名空。
- 付款审核人空。

修复要求：

1. 收款 join `financial_accounts` / `bank_names`。
2. 付款审核人从审核事件、audit columns 或 payment 表字段读取。

验收：

- 收付款列表能展示银行、审核人、审核时间。

### 5.10 `AccBillsController`

文件：

- `apps/backend/src/main/java/com/xqt/saas/acc/AccBillsController.java`

当前问题：

- `salesman` 空。

修复要求：

1. 从客户销售归属读取。
2. 如果无字段，给 customers 或 settlement profile 增加 `salesman_id`。

验收：

- 客户账单能按销售员筛选和展示。

### 5.11 `AccStatsController`

文件：

- `apps/backend/src/main/java/com/xqt/saas/acc/AccStatsController.java`

当前问题：

- 返回零值占位。

修复要求：

实现真实统计：

1. 今日订单数。
2. 今日票数/件数。
3. 今日应收。
4. 今日应付。
5. 今日利润。
6. 待审核订单。
7. 待收款账单。
8. 待付款账单。
9. 异常件数量。
10. 按分公司/渠道/客户聚合。

验收：

- Dashboard/ACC 统计不再是零值。
- 与订单、费用、账单列表合计能对上。

## 6. 实施任务 P2：真实渠道和真实面单

### 6.1 真实渠道取号

新增实现：

```text
UpsCarrierGateway
FedexCarrierGateway
SelfEdiCarrierGateway
```

或按实际 ACC 插件目录命名。

要求：

1. 从 `channels.metadata` 或 `channel_accounts` 读取凭证。
2. 支持 sandbox/prod。
3. 保存 request/response 到 evidence。
4. 失败返回可读错误码。
5. 支持重试和幂等。
6. 取号成功后写：
   - `cartons.tracking_no`
   - `tracking_events`
   - `label_files` 或 shipment metadata

### 6.2 真实面单

新增实现：

```text
UpsLabelGateway
FedexLabelGateway
SelfEdiLabelGateway
```

要求：

1. 按渠道返回 PDF/ZPL/图片。
2. 支持批量。
3. 支持 method=0 base64。
4. 支持 method=1 URL。
5. 支持子单号回写。
6. 支持 `getNewLabel.php` 的换标查询逻辑。
7. 非 PDF 图片如需合并，按 ACC 旧逻辑转 PDF。

## 7. 建议 Claude 执行顺序

给 Claude 的推荐 prompt：

```text
请根据 docs/acc-remaining-logic-implementation-guide.md，按 P0 优先级修改 xqt-saas。

第一阶段只做：
1. 补齐 RateEngine 的 ACC 全量费率分支；
2. Submit 接入 RateEngine 结果；
3. 生成 AR/AP charges；
4. 余额预扣写流水；
5. 补测试。

要求：
- 先读 acc/api/APIClass.php、acc/config/Freight.php、acc/inc/Price.php、docs/acc-rates-migration.md；
- 不要破坏现有 API 响应结构；
- 保留 metadata.acc_compat；
- 所有新增 DB 结构写 migration；
- 新增或修改测试，确保 mvn test 通过；
- 完成后更新 docs/ACC-API-完成状态报告.docx 或对应 markdown 状态。
```

第二阶段 prompt：

```text
继续根据 docs/acc-remaining-logic-implementation-guide.md 的第 4 章，补齐 documentcharges / Express_Charge 闭环。

要求：
- 新增 documentcharges service/repository/controller；
- Submit 成功后生成 AR/AP 费用；
- 支持预付/月结两种客户；
- 写 financial account records 或 ledger entries；
- 客户账单与供应商账单可以从费用生成；
- 补齐核销、反核销、利润统计测试；
- 不要改坏已有 /api/acc/charges、/api/acc/costs、/api/acc/bills 行为。
```

第三阶段 prompt：

```text
继续根据 docs/acc-remaining-logic-implementation-guide.md 的第 5 章，补齐已实现 ACC tab 的空字段和 0 占位。

优先处理：
1. AccOrdersController
2. AccShipmentsController
3. AccCustomersController
4. AccSuppliersController
5. AccStatsController

要求：
- 每个字段必须说明来源表；
- 没有表就新增 migration；
- 不要只返回空字符串或 0；
- 增加最小测试或至少接口契约验证；
- 更新完成状态报告。
```

## 8. 验收清单

完成后必须满足：

1. `./mvnw test` 通过。
2. `POST /api/customer-api/rates/quote` 支持客户价、客户组价、普通价。
3. `POST /api/customer-api/orders/{no}/submit` 不再使用简化预扣估算。
4. Submit 后能查到 AR/AP 费用行。
5. 客户余额或账本流水有预扣记录。
6. 客户账单可以从费用生成。
7. 收款后账单 paid/unpaid/status 正确。
8. 供应商账单和付款链路可追溯。
9. `AccOrdersController` 的运费、成本、分公司不再是占位。
10. `AccStatsController` 不再返回全零。
11. 关键金额都保存汇率快照。
12. 所有写操作有 audit event。

## 9. 风险提醒

1. 不要直接照搬 ACC 的余额方向。旧系统 `Customer_Balance.Balance` 与 API 展示方向相反，新系统应该用账本流水表达。
2. 不要把 ACC 的所有字段都塞进主表。兼容字段可放 `metadata.acc_compat`，核心字段应进入规范表。
3. 不要破坏现有 `/api/customer-api/*` 响应形状，旧客户 API 需要契约稳定。
4. 不要把真实渠道凭证写死在代码里，应放配置或数据库加密字段。
5. 费率规则要保留命中证据，否则后续金额对账会很难排查。
6. 审核、反审、核销、反核销必须保证资金流水可逆或有冲正记录。

