# ACC 逻辑缺口与 Claude 实施任务书

> 生成日期：2026-05-28  
> 新系统仓库：`/Users/chaowang/新航线/xqt-saas`  
> ACC 旧系统对照：`/Users/chaowang/新航线/acc`  
> 目标读者：Claude / 后续代码实现 Agent  

---

## 0. 给 Claude 的第一句话

请不要只看路由是否存在。当前新系统已经有 `78/78` 个 ACC 后台 tab 路由，但很多地方只是 CRUD、聚合视图或 Demo 逻辑。你的任务是继续补齐“ACC 旧系统业务行为”，重点是报价、提交取号、费用、账本、账单、渠道、面单、轨迹、称重和第三方推单这些生产主路径。

开始实现前，先阅读：

1. `docs/acc-gap-report-for-claude-2026-05-28.md`
2. `docs/acc-api-reverse-db-design.md`
3. `docs/acc-xqt-full-match-roadmap.md`
4. `docs/prd-sprint1-billing-engine.md`
5. 本文档

注意：`docs/acc-remaining-logic-implementation-guide.md` 部分内容已过时，不能作为唯一依据。

---

## 1. 当前真实状态

### 1.1 已经完成或基本完成

- ACC 后台 tab 路由：`78/78` 均已有 `/api/acc/{tab}`。
- `/api/acc/stats` 已从零值占位改成真实聚合。
- `AccAuditController` 已有多 tab 审核、反审、批量审核、历史记录框架。
- `packages -> shipments` 审核映射已有。
- `RateEngine` 已从 MVP 升级到客户价、组价、成本价、佣金、渠道账号限制、偏远规则、多段计费等框架。
- `documentcharges` 已有客户账单、收款核销、费用确认、利润聚合基础能力。
- 部分后台 tab 字段已从真实表或 metadata join，不再是完全空字段。
- `CustomerApiService.submitOrder` 已能调用 `RateEngine.quote()`，并能在 blocker 存在时拒绝。
- Submit 已可写部分 AR/AP 费用行。
- `channel_account_daily_usage` 已有部分读取/更新逻辑，需要继续验证和补测试。

### 1.2 当前核心问题

当前差距不是“没有接口”，而是：

- 有接口但业务逻辑不等价。
- 有字段但状态流和金额口径没闭环。
- 有费用表但缺完整账本流水。
- 有 Demo 渠道但缺真实渠道取号。
- 有 Noop 面单但缺真实 provider。
- 有仓库/扫描表但缺 ACC `Scale.php` 等设备入口。
- 有文档但部分内容和当前代码不一致。

---

## 2. 不要重复做的事情

Claude 实现前必须先确认这些点，避免重复造轮子：

| 不要重复做 | 当前状态 |
|---|---|
| 不要再新增 ACC tab 路由 | 78/78 已有 |
| 不要再把 `AccStatsController` 当成零值占位 | 已改为真实聚合，补测试即可 |
| 不要再把所有后台字段都当成空占位 | orders/shipments/customers/suppliers/warehouses 等已有部分真实 join |
| 不要再使用 `036_ai_iot_new_modules.sql` | `036` 已被 `036_acc_currency_fields.sql` 占用 |
| 不要只照搬 ACC 表结构 | 新系统目标是业务行为对齐，不是旧库表结构克隆 |
| 不要让 Controller 继续堆业务 SQL | 新增复杂逻辑应进入 Service/Repository |

---

## 3. 总体缺口清单

按业务模块划分，当前仍缺以下 ACC 逻辑。

## 3.1 订单 / 客户 API

### 已有

- `PreOrder` / `Modify` / `Submit` / `Cancel` / `Query` / `Status` 等客户 API 主路径已有实现基础。
- `CustomerApiService.submitOrder` 会创建 `shipments`、`cartons`、`declarations`、`charges`。
- Submit 可调用 `RateEngine.quote()`。

### 缺口

1. `preSubmitOrder` 仍未完全接入 `RateEngine.quote()`，口径可能和 Submit 不一致。
2. Submit 在非 blocker 异常或 `quote == null` 时仍可能 fallback 到 `estimatePrepayAmount()`。
3. 生产环境默认应严格报价，不能绕过正式计费规则。
4. Submit 失败、渠道取号失败时，余额、费用、运单状态需要显式冲正或回滚策略。
5. Submit 后应确保 `shipment_order_links` 写入，不能只靠 `customer_ref` 软关联。
6. `orders` 后台 create/update 仍偏薄 CRUD，不等价 ACC 后台制单完整流程。

### 重点文件

- `apps/backend/src/main/java/com/xqt/saas/customerapi/CustomerApiService.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/CustomerApiRepository.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/CustomerApiController.java`
- `apps/backend/src/main/java/com/xqt/saas/acc/AccOrdersController.java`
- `apps/backend/src/main/java/com/xqt/saas/acc/AccShipmentsController.java`

---

## 3.2 报价 / 计费

### 已有

- `RateEngine` 已有客户价、组价、销售价、成本价、佣金、渠道账号限制、偏远、多段计费等框架。
- `db/migrations/029_acc_rate_full_logic.sql` 已落地一批价格相关表。

### 缺口

1. `/api/customer-api/rates/quote` blocker 语义需要统一：不能只是 200 body 表达阻断。
2. `PreSubmit` 与 `Submit` 报价口径必须完全一致。
3. 费用行粒度还不够，需要对齐 ACC `Express_Charge.Type`。
4. `prd-sprint1-billing-engine.md` 中的能力仍需实现或确认：
   - 品名关键词附加费
   - 附加费取大
   - 按箱最低计费
   - 偏远三档
   - KG / LB / CBM / PIECE 多计费单位
   - Dim Factor
5. 需要真实 DB 集成测试，不能只靠 mock 单测。
6. 需要对照 `acc/config/Freight.php::getFee` 做边界样本。

### 重点文件

- `apps/backend/src/main/java/com/xqt/saas/rates/RateEngine.java`
- `apps/backend/src/main/java/com/xqt/saas/rates/RateRepository.java`
- `apps/backend/src/main/java/com/xqt/saas/rates/RateQuoteRequest.java`
- `apps/backend/src/main/java/com/xqt/saas/rates/RateQuoteResponse.java`
- `apps/backend/src/test/java/com/xqt/saas/rates/RateEngineTests.java`
- `db/migrations/029_acc_rate_full_logic.sql`

---

## 3.3 费用 / Express_Charge 等价

### 已有

- `charges` 表已有 AR/AP 基础。
- Submit 已可写部分 AR/AP 费用。
- `AccChargesController`、`AccCostsController` 有列表和审核基础。

### 缺口

1. ACC `Express_Charge.Type` 类型未完整映射。
2. Freight、Fuel、Remote、Surcharge、Commission、Adjustment 等应明确是否独立费用行。
3. 成本价、佣金、燃油、偏远等不能只放 evidence，需要明确费用行策略。
4. 费用审核、反审、作废、调账、退款、返利对账单和利润的影响未闭环。
5. `AccProfitsController` 当前主要是 shipment 级 AR - AP 聚合，未完整扣除退款、调账、返利、罚款等。

### 重点文件

- `apps/backend/src/main/java/com/xqt/saas/acc/AccChargesController.java`
- `apps/backend/src/main/java/com/xqt/saas/acc/AccCostsController.java`
- `apps/backend/src/main/java/com/xqt/saas/acc/AccProfitsController.java`
- `apps/backend/src/main/java/com/xqt/saas/documentcharges/DocumentChargeService.java`
- `apps/backend/src/main/java/com/xqt/saas/documentcharges/DocumentChargeRepository.java`

---

## 3.4 财务 / 账单 / 账本

### 已有

- `documentcharges` 有客户账单、收款核销、费用确认、void、profit summary。
- 供应商账单和付款能力已有部分实现或 DTO/Service 基础，需要以代码为准复查。
- `AccBillsController`、`AccReceivedsController`、`AccPaymentsController` 有 ACC 后台视图。

### 缺口

1. 缺等价 `Customer_Balance_History` 的资金流水层。
2. 当前余额变化更多是快照式更新，缺可追溯 ledger。
3. `AccFinanceTxnsBase` 及退款、调账、返利、借款、分红、转账等 CRUD 未写余额流水。
4. `AccFinanceTxnsBase` 审核通过后未联动 `charges`、账单和利润。
5. documentcharges 与 `/api/acc/bills|receiveds|payments` 可能双轨，需要统一口径。
6. `fx_rate_snapshots` 需要覆盖所有金额动作，不应只覆盖部分 settle。
7. `/api/finance/*` 高级 search API 仍需从 roadmap 迁入 Spring Boot。

### 建议新增或复用

优先确认是否已有等价表；如果没有，新增类似：

- `financial_account_records`
- 或复用现有 ledger 表并补业务类型

流水至少覆盖：

- 预扣
- 预扣释放
- 收款
- 付款
- 退款
- 调账
- 返利
- 罚款
- 赔偿
- 作废
- 汇率差

### 重点文件

- `apps/backend/src/main/java/com/xqt/saas/documentcharges/*`
- `apps/backend/src/main/java/com/xqt/saas/acc/AccBillsController.java`
- `apps/backend/src/main/java/com/xqt/saas/acc/AccReceivedsController.java`
- `apps/backend/src/main/java/com/xqt/saas/acc/AccPaymentsController.java`
- `apps/backend/src/main/java/com/xqt/saas/acc/AccFinanceTxnsBase.java`
- `apps/backend/src/main/java/com/xqt/saas/finance/*`
- `db/migrations/030_acc_documentcharges.sql`
- `db/migrations/018_finance_core.sql`

---

## 3.5 渠道 / 取号 / 面单

### 已有

- `CarrierGateway` 抽象存在。
- `NoopCarrierGateway`、`DemoUpsCarrierGateway`、`DemoFedexCarrierGateway` 存在。
- `LabelGateway` 和 `NoopLabelGateway` 存在。
- 可以生成 Demo 单号和占位面单。

### 缺口

1. 生产 channel 不能 fallback 到 `NOOP-*`。
2. `CarrierGatewayRegistry` 未真正由 `acc_channel_accounts` / `acc_logistics_interfaces` 驱动。
3. 缺真实 UPS/FedEx/EDI/Sandbox adapter。
4. provider request/response evidence 需要保存。
5. Label provider 需要按渠道路由，不应只有 Noop。
6. 非 PDF 图片/ZPL 转 PDF 等 ACC 行为未补齐。
7. 渠道失败时费用、余额、状态、错误提示需要和 ACC 对齐。

### 重点文件

- `apps/backend/src/main/java/com/xqt/saas/customerapi/CarrierGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/CarrierGatewayRegistry.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/NoopCarrierGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/DemoUpsCarrierGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/DemoFedexCarrierGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/labels/LabelGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/labels/LabelService.java`
- `apps/backend/src/main/java/com/xqt/saas/labels/NoopLabelGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/acc/AccChannelAccountsController.java`
- `apps/backend/src/main/java/com/xqt/saas/acc/AccLogisticsInterfacesController.java`

---

## 3.6 轨迹 / 状态过程

### 已有

- `tracking_events` 表存在。
- Customer API tracking query 有基础。
- `AccTracksController` 存在。

### 缺口

1. `AccTracksController` 更像轨迹字典 CRUD，不是 ACC 多源轨迹聚合。
2. ACC 旧系统轨迹来自 `Express_Process`、`Transit_Process`、`Stowage_Process`、`Online_TrackNo`、`Express_TrackNo` 等多源。
3. 新系统需要建立统一轨迹聚合服务。
4. 状态映射、时间线、客户可见性、内部操作轨迹需要分层。

### 重点文件

- `db/migrations/006_full_painpoint_modules.sql`
- `apps/backend/src/main/java/com/xqt/saas/acc/AccTracksController.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/*tracking*`
- 后续可新增 `tracking` 包

---

## 3.7 仓库 / 称重 / 设备

### 已有

- 仓配表已存在：
  - `warehouse_receipts`
  - `warehouse_receipt_items`
  - `picklists`
  - `picklist_items`
  - `ladings`
  - `pallets`
  - `scan_events`
- `AccWarehousesController` 存在。

### 缺口

1. ACC `acc/api/Scale.php` 未迁。
2. 仓配核心表缺业务 API。
3. `scan_events` 缺设备幂等和 IoT 推送逻辑。
4. 与新设计文档中的 `/api/iot/warehouse/parcel` 还未落地。

### 建议

如果本轮目标是 ACC 等价，先补 `Scale.php` 对应能力；如果同时推进仓库 IoT，再参考：

- `docs/warehouse-iot-database-change-plan-2026-05-28.md`
- `docs/ai-iot-new-modules-design-2026-05-28.md`

注意 migration 编号应使用 `037_warehouse_iot_schema.sql` 或更高编号，不能用已占用的 `036`。

---

## 3.8 第三方推单 / sumy.php

### 缺口

ACC 独立入口 `acc/api/sumy.php` 尚未迁移。

### 需要 Claude 做

1. 阅读旧系统 `acc/api/sumy.php`。
2. 反推请求字段、签名方式、业务校验、写表逻辑。
3. 在新系统设计接口，例如：

```http
POST /api/customer-api/external-orders/sumy
```

4. 复用现有 Customer API 下单服务，不要另写一套重复订单创建逻辑。
5. 写 comparison case。

---

## 3.9 配载 / Stowage

### 已有

- Customer API `stowages/sync` 已实现。
- `AccStowagesController`、`AccStowageStepsController`、`AccTransitsController`、`AccDispatchesController`、`AccForecastsController` 存在。

### 缺口

1. 后台 tab 多数是 CRUD。
2. 缺 ACC `Stowage_Process` 级状态推进。
3. 装箱单、配载、转运、轨迹之间的自动联动不完整。
4. 配载同步后的费用、状态、轨迹影响需要确认。

---

## 3.10 审核 / 权限

### 已有

- `AccAuditController` 审核框架完整度较高。
- `audit_events` 表存在。

### 缺口

1. 哪些 tab 应支持审核、反审、批量审核仍需业务确认。
2. `collects` 等 tab 前端审核按钮需确认并补齐。
3. 审核通过后的业务副作用不足，例如：
   - 费用确认
   - 退款生效
   - 调账入账
   - 退件回流利润
   - 付款核销
4. 员工、分公司、销售、客服的数据权限未完全复刻 ACC。

### 重点文件

- `apps/backend/src/main/java/com/xqt/saas/acc/AccAuditController.java`
- `apps/backend/src/main/java/com/xqt/saas/framework/fieldgate/FieldGate.java`
- `apps/backend/src/main/java/com/xqt/saas/framework/cascade/CascadeChecker.java`
- 前端审核 tab 配置文件

---

## 4. Claude 实施顺序

## P0：先修生产主路径

### 任务 1：统一 Quote / PreSubmit / Submit 口径

目标：

- `quote`
- `preSubmit`
- `submit`

三者必须使用同一套 `RateEngine.quote()` 结果。

要求：

1. 增加或确认配置：

```yaml
rates:
  strict-quote: true
```

或等价配置。

2. 生产默认 `strict-quote=true`。
3. 报价失败时 Submit 不得 fallback 到 `estimatePrepayAmount()`。
4. Demo/dev 环境如保留 fallback，必须显式配置。
5. 补测试：
   - quote 成功
   - quote blocker
   - quote 异常 strict 模式阻断
   - preSubmit 与 submit 金额一致

验收：

- 搜索 `estimatePrepayAmount()`，确认它不再是生产主路径。
- Submit 不会在 quote 失败时继续创建正式运单。

---

### 任务 2：费用行对齐 ACC `Express_Charge.Type`

目标：

将当前总额式费用改为更接近 ACC 的多费用行。

至少覆盖：

- AR Freight
- AR Fuel
- AR Remote
- AR Surcharge
- AP Freight
- AP Fuel
- AP Remote
- Commission
- Adjustment

要求：

1. 建立 ACC Type 到新系统 `charge_items.code` / `charges` 字段的映射。
2. RateEngine evidence 中的金额项应能落成 charge 行。
3. documentcharges 生成账单时能正确纳入这些行。
4. profit summary 使用同一套费用行计算。

验收：

- 一个 Submit 至少能产生多条 AR/AP 费用行。
- 客户账单、供应商账单、利润聚合使用相同费用来源。

---

### 任务 3：补完整资金流水

目标：

建立 ACC `Customer_Balance_History` 等价能力。

要求：

1. 先搜索现有 ledger/financial/account record 表，能复用就复用。
2. 若不能复用，新增迁移。
3. 所有余额变化必须写流水：
   - 预扣
   - 释放
   - 收款
   - 付款
   - 退款
   - 调账
   - 返利
   - 罚款
   - 赔偿
   - 作废
4. 流水必须包含：
   - `tenant_id`
   - 业务对象类型
   - 业务对象 ID
   - 客户/供应商
   - 币种
   - 金额
   - 方向
   - before/after balance
   - 操作人
   - created_at

验收：

- 调账/退款/返利审核后，不只是 CRUD 记录，而是产生资金流水。
- 客户余额查询能追溯来源。

---

## P1：补财务闭环和渠道能力

### 任务 4：统一 documentcharges 与 ACC 财务 tab 口径

要求：

1. `/api/document/*` 和 `/api/acc/bills|receiveds|payments` 不应各算各的。
2. 账单生成、核销、作废、利润聚合使用同一批 charges / invoice / payment 表。
3. AP partner invoice generate / settle 以当前代码为准复查，缺什么补什么。
4. `fx_rate_snapshots` 覆盖所有跨币种金额动作。

验收：

- 客户账单 generate + settle E2E 通过。
- 供应商账单 generate + settle E2E 通过。
- 利润 summary 与 bills/payments 口径一致。

---

### 任务 5：真实渠道和面单生产化

要求：

1. `CarrierGatewayRegistry` 读取 `acc_channel_accounts` 或 `acc_logistics_interfaces`。
2. 至少实现一个 sandbox adapter。
3. 生产 channel 禁止 fallback 到 Noop。
4. Provider request/response 存入 evidence。
5. `LabelGateway` 按渠道路由。
6. 面单生成失败要有明确错误码和重试策略。

验收：

- 一个非 Noop 渠道能完成取号。
- 一个非 Noop 面单 provider 能生成或拉取标签。
- 失败时不产生错误费用或脏状态。

---

## P2：补独立 API 和后台 tab 行为

### 任务 6：迁移 `Scale.php`

要求：

1. 阅读 `acc/api/Scale.php`。
2. 确认设备认证、参数、返回格式。
3. 新系统提供等价接口。
4. 能查箱号/运单状态，必要时写 `scan_events`。

建议路径：

```http
POST /api/device/scale/*
```

或与 IoT 设计合并：

```http
POST /api/iot/warehouse/parcel
```

验收：

- 至少 3 条旧 Scale 请求样本能映射到新接口。

---

### 任务 7：迁移 `sumy.php`

要求：

1. 阅读 `acc/api/sumy.php`。
2. 反推第三方推单 body、签名、字段默认值。
3. 复用现有 Customer API 下单服务。
4. 写入新系统标准 `orders/shipments/cartons/declarations/charges`。

建议路径：

```http
POST /api/customer-api/external-orders/sumy
```

验收：

- 旧 sumy 样本能成功创建新系统运单。
- 失败场景有明确 errorCode。

---

### 任务 8：后台 tab 从 CRUD 升级为业务行为

优先处理：

- `AccReturnsController`
- `AccCustomerRefundsController`
- `AccSupplierRefundsController`
- `AccCustomerAdjustsController`
- `AccSupplierAdjustsController`
- `AccCustomerRebatesController`
- `AccSupplierRebatesController`
- `AccCustomerFinesController`
- `AccSupplierFinesController`
- `AccCommissionsController`

要求：

1. CRUD 仍保留。
2. 审核通过时必须触发财务/费用/利润副作用。
3. 反审时必须可冲回或标记冲正。
4. 所有动作写审计和资金流水。

---

## 5. Comparison Case 要求

每个 P0 模块至少建立 3 条 ACC 对照样本。

建议样本：

### 报价

1. 普通渠道，首重续重。
2. 偏远邮编。
3. 电池/敏感货 blocker。
4. 客户专属价。
5. 客户组价。

### Submit

1. 余额充足，成功取号。
2. 余额不足，阻断。
3. 渠道 blocker，阻断。
4. 渠道取号失败，状态和费用回滚。

### 财务

1. 客户账单生成。
2. 客户部分核销。
3. 供应商账单生成。
4. 供应商付款核销。
5. 调账/退款后利润变化。

### 面单

1. PDF 面单。
2. ZPL 面单。
3. 换标/重新获取面单。

每条 case 记录：

- ACC 旧请求或截图
- 新系统请求
- 旧系统结果
- 新系统结果
- diff
- 是否通过

---

## 6. 实现约束

### 6.1 代码结构

新增复杂逻辑不要继续塞进 ACC Controller。

推荐：

- Controller：只处理 HTTP 入参/出参
- Service：业务流程、状态机、幂等、事务
- Repository：SQL
- DTO：请求/响应
- Tests：契约测试和集成测试

### 6.2 数据库

- 新 migration 编号必须大于当前最大编号。
- 不要使用已占用编号。
- 新表必须有 `tenant_id`。
- 新表如涉及业务数据，必须启用 RLS。
- 金额字段必须有 currency。
- 重要状态字段必须有 check 约束或应用层枚举。

### 6.3 错误码

所有新 API 必须有稳定 errorCode，例如：

- `RATE_BLOCKED`
- `RATE_QUOTE_FAILED`
- `BALANCE_NOT_ENOUGH`
- `CARRIER_GATEWAY_FAILED`
- `LABEL_GENERATION_FAILED`
- `FINANCE_LEDGER_FAILED`
- `ACC_COMPAT_MAPPING_MISSING`

---

## 7. 最终验收清单

Claude 完成一轮实现后，必须检查：

- `./mvnw test` 通过。
- 78 个 ACC tab 非 404。
- Quote / PreSubmit / Submit 使用同一计费口径。
- 生产 Submit 不再 silent fallback 到估算金额。
- Submit 能产生多费用行。
- 费用能进入客户账单和供应商账单。
- 收款/付款/调账/退款有资金流水。
- 利润口径能追溯到 AR/AP/调整项。
- 至少一个非 Noop 渠道 adapter 可运行。
- 至少一个非 Noop label provider 可运行。
- `Scale.php` 和 `sumy.php` 有实现或明确书面弃用结论。
- 每个 P0 模块至少 3 条 comparison case。

---

## 8. 推荐 Claude 首轮任务

如果只能先做一轮，建议按以下顺序：

1. 修 `Quote / PreSubmit / Submit` 口径统一，关掉生产 fallback。
2. 补费用行拆分，对齐 ACC `Express_Charge.Type`。
3. 建资金流水，覆盖预扣、收款、退款、调账。
4. 统一 documentcharges 与 ACC bills/receiveds/payments 口径。
5. 实现 `Scale.php` 或明确并入 IoT 接口。
6. 实现 `sumy.php` 第三方推单。
7. 接一个 sandbox 渠道和 label provider。

这 7 件事完成后，新系统才更接近“可替代 ACC 生产主流程”，而不是只有页面和 CRUD。

