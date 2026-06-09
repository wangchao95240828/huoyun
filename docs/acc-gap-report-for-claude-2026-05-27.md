# ACC 当前差距报告（给 Claude 修复用）

生成日期：2026-05-27

仓库：`/Users/chaowang/新航线/xqt-saas`

旧系统对照：`/Users/chaowang/新航线/acc`

## 1. 当前结论

新 merge 后，ACC 后台 tab 的“有没有接口”问题已经基本解决：

- 前端 `accTabs`：78 个。
- 后端 `/api/acc/{tab}`：78 个 tab 均有对应 Controller。
- 额外后端接口：`/api/acc/stats`。
- DB migrations：从 25 个增加到 28 个，新增 `026_acc_hr.sql`、`027_acc_logistics.sql`、`028_acc_customer_product.sql`。

因此，旧报告里“剩余 28 个 ACC tab 未迁”的结论已经过时。

当前真正差距已经从“缺页面/缺路由”变成“业务规则不够 ACC 1:1”。

## 2. 当前硬缺口

### 2.1 仍未迁的独立 ACC API

以下两个旧 ACC 独立入口当前仍未迁：

1. `acc/api/Scale.php`
   - 能力：称重设备接口。
   - 建议新接口：`/api/customer-api/scale/*` 或 `/api/device/scale/*`。
   - 建议优先级：P2，除非客户现场已经依赖称重设备。

2. `acc/api/sumy.php`
   - 能力：第三方推单接口。
   - 建议新接口：`/api/customer-api/external-orders/sumy` 或独立 adapter。
   - 建议优先级：P2，除非有真实客户正在使用。

### 2.2 `/api/acc/stats` 仍是零值占位

文件：

- `apps/backend/src/main/java/com/xqt/saas/acc/AccStatsController.java`

当前问题：

```text
orderCount = 0
customerCount = 0
shipmentCount = 0
revenue = 0
cost = 0
profit = 0
```

需要改为真实聚合。

## 3. 主要非 1:1 逻辑差距

### 3.1 运费试算仍是 MVP

文件：

- `apps/backend/src/main/java/com/xqt/saas/rates/RateEngine.java`
- `apps/backend/src/main/java/com/xqt/saas/rates/RateRepository.java`
- `apps/backend/src/main/java/com/xqt/saas/rates/DocumentRateController.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/CustomerApiController.java`
- `apps/backend/src/test/java/com/xqt/saas/rates/RateEngineTests.java`
- `docs/acc-rates-migration.md`

旧 ACC 对照：

- `acc/api/APIClass.php?act=Price`
- `acc/config/Freight.php::getFee`
- `acc/inc/Price.php`
- `acc/inc/ProductSave.php`

当前代码已明确写明差异：

- 不支持成本价。
- 不支持客户组价。
- 不支持客户专属价。
- 不支持佣金。
- 不支持渠道账号限量：`MaxCount`、`MaxPiece`、`MaxWeight`。
- 不支持电池过滤。
- 不支持仿牌/敏感货过滤。
- 偏远附加费仍是固定百分比，不读取完整规则。
- `zone_code` 仍偏 MVP。
- 多段首重/续重尚未完整建模。

Claude 修复目标：

1. 读取 ACC 旧费率逻辑：`acc/api/APIClass.php`、`acc/config/Freight.php`、`acc/inc/Price.php`、`acc/inc/ProductSave.php`。
2. 扩展新系统费率模型，不要破坏已有 `RateQuoteRequest` / `RateQuoteResponse` 兼容性。
3. 支持价格优先级：客户专属价、客户组价、普通销售价、成本价/应付价。
4. 支持渠道账号限制和货物限制。
5. 输出命中规则证据，写入 quote breakdown。
6. 补测试。

验收：

- `RateEngineTests` 覆盖客户价、客户组价、普通价、偏远、禁运、渠道限制、电池/仿牌限制。
- `POST /api/customer-api/rates/quote` 和 `POST /api/document/rates/quote` 都走同一套完整逻辑。

### 3.2 Submit 预扣仍用简化估算

文件：

- `apps/backend/src/main/java/com/xqt/saas/customerapi/CustomerApiService.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/CustomerApiRepository.java`

当前问题：

`CustomerApiService.estimatePrepayAmount()` 仍是：

```text
base 30 + weight * 25 + declaredValue * 0.1%
```

这不是 ACC 全量计费，也没有接 `RateEngine` 的真实报价结果。

Claude 修复目标：

1. `submitOrder()` 中根据 `metadata.acc_compat` 构造 `RateQuoteRequest`。
2. 调用 `RateEngine.quote()`。
3. 用 quote 结果生成费用行。
4. 预扣金额使用 quote total，不再使用 `estimatePrepayAmount()`。
5. 取号失败时回滚费用和余额。

验收：

- `estimatePrepayAmount()` 被删除或只作为明确 fallback，不再是主路径。
- Submit 成功后可查到真实 AR/AP charges。
- Submit 失败不会留下已扣余额或有效费用。

### 3.3 `Express_Charge` / 余额 / 账单闭环仍不完整

当前相关文件：

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

旧 ACC 对照：

- `Express_Charge`
- `Express_Charge_Status`
- `Customer_Balance`
- `Customer_Balance_History`
- `acc/inc/Charge.php`
- `acc/inc/Cost.php`
- `acc/inc/CBill.php`
- `acc/inc/Received.php`
- `acc/inc/Pay.php`

当前缺口：

1. 费用行没有完整表达 ACC `Express_Charge.Type` 多费用类型。
2. 预扣、释放、退款、调账、账单核销没有完整资金流水链。
3. 客户账单和供应商账单与费用行的关联仍不完整。
4. 收款/付款后 paid/unpaid/status 回写口径需要统一。
5. 利润聚合口径还没和费用闭环打通。

Claude 修复目标：

1. 新增或完善 `documentcharges` 业务层。
2. Submit 后生成 AR 运费、燃油、偏远、附加费，以及 AP 成本、燃油、附加费。
3. 写入 evidence，保留 ACC 对照字段和报价命中规则。
4. 预付客户写余额扣减和流水。
5. 月结客户只入待出账费用，不扣余额。
6. 客户账单可从 AR charges 生成。
7. 供应商账单可从 AP charges 生成。
8. 收款/付款支持部分核销、全额核销、反核销。

验收：

- Submit 成功后 `/api/acc/charges`、`/api/acc/costs` 能看到对应费用。
- 账单生成后费用不能重复入账。
- 收款后账单 paid/unpaid/status 正确。
- 利润 = AR - AP - 退款/赔偿/调账影响。

### 3.4 真实渠道取号仍是 Noop / Demo

文件：

- `apps/backend/src/main/java/com/xqt/saas/customerapi/CarrierGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/NoopCarrierGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/DemoUpsCarrierGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/DemoFedexCarrierGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/CarrierGatewayRegistry.java`

当前问题：

- Submit 能跑通，但默认生成 `NOOP-*` 单号。
- 没有真实 UPS/FedEx/自营 EDI 出单。
- `acc_channel_accounts` 和 `acc_logistics_interfaces` 已有配置表，但尚未真正驱动 gateway。

Claude 修复目标：

1. 让 `CarrierGatewayRegistry` 根据 `channels.metadata`、`acc_channel_accounts` 或 `acc_logistics_interfaces` 路由。
2. 实现至少一个真实或可配置的 provider adapter。
3. 保存 request/response 到 evidence。
4. 渠道失败时回滚状态、费用和余额。

验收：

- 对指定 channel 不再走 `NOOP`。
- Submit 返回真实或 sandbox provider 单号。
- provider raw response 可追溯。

### 3.5 真实面单仍是 Noop

文件：

- `apps/backend/src/main/java/com/xqt/saas/labels/LabelGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/labels/NoopLabelGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/labels/LabelService.java`
- `apps/backend/src/main/java/com/xqt/saas/labels/LabelController.java`

当前问题：

- 默认生成最小合法 PDF 或 ZPL，占位性质。
- 非 PDF 图片/ZPL 转 PDF 的真实 ACC 行为没有完全复刻。
- 真实渠道面单未接。

Claude 修复目标：

1. 按渠道路由 `LabelGateway`。
2. 接入真实 provider label API 或本地模拟 provider。
3. method=0 返回 base64，method=1 落文件并返回 URL。
4. 子单号写 cartons。
5. 保持 `getNewLabel.php` 的 relabel 查询逻辑。

验收：

- 真实渠道或 sandbox 能生成可下载面单。
- 批量生成时单条失败不破坏整批。
- `relabel` 多页 PDF 分页仍通过测试。

## 4. 后台 tab 当前状态

### 4.1 已解决

以下旧结论已过时：

```text
剩余 28 个 ACC tab 未迁
```

当前 78 个 tab 都有后端路由。

### 4.2 仍需注意：新增 28 个多为 CRUD/视图级实现

新增模块基本有：

- DB 表
- list/raw/create/update/delete
- audit_status
- FieldGate
- CascadeChecker

但大多不是旧 ACC 复杂业务逻辑 1:1。

典型例子：

1. `AccEmployeesController`
   - 有员工 CRUD。
   - 未完整复刻旧 `Employee.php` 的复杂权限、薪资、关联逻辑。

2. `AccCommissionsController`
   - 有提成记录 CRUD。
   - 未自动按利润/销售额生成提成。

3. `AccProductsController`
   - 是 `rate_cards + services` 聚合只读视图。
   - 不等价旧 ACC 产品/价格维护全流程。

4. `AccZonesController`
   - 是 `rate_card_lines.zone_code` 去重聚合视图。
   - 不等价完整价格分区维护。

5. `AccQuickOrdersController`
   - 是 orders 的简化只读视图。
   - 不是完整快速下单流程。

6. `AccLogisticsInterfacesController`
   - 能维护物流接口配置。
   - 但配置尚未真正驱动渠道取号/面单。

## 5. 已实现 tab 中仍有空字段 / 0 占位

需要优先修以下文件：

| 文件 | 当前问题 | 修复方向 |
| --- | --- | --- |
| `AccOrdersController.java` | `sellCharge=0`、`costCharge=0`、`branch=""`、`product` 不完整 | join charges/costs/organizations/metadata |
| `AccShipmentsController.java` | `supplierName=""`，费用聚合不足 | join partner/channel/account/cost |
| `AccCustomersController.java` | 联系人、余额、结算、分公司、销售等字段不足 | join contacts/accounts/settlement/org/users |
| `AccSuppliersController.java` | 联系人、产品、余额不足 | join partner accounts/channels/AP |
| `AccWarehousesController.java` | consignee/company/postcode 空 | 扩展 warehouses 或 join addresses |
| `AccBranchesController.java` | contact/phone/address/remark 空 | 扩展 organizations |
| `AccReturnsController.java` | amount=0 | join 退件费用/退款费用 |
| `AccReceivedsController.java` | bankName 空 | join financial_accounts/bank_names |
| `AccPaymentsController.java` | auditName 空 | join audit_events 或付款审核字段 |
| `AccBillsController.java` | salesman 空 | join 客户销售归属 |
| `AccStatsController.java` | 全部 0 | 真实聚合 |

## 6. 审核和前端集成小缺口

### 6.1 `packages` 审核映射可能缺失

需要检查：

- `apps/backend/src/main/java/com/xqt/saas/acc/AccAuditController.java`
- `apps/web/src/App.vue`

问题：

- `packages` 有 controller。
- 但如果 `TAB_TO_TABLE` 没有映射，点击审核可能报 `unknown ACC tab`。

Claude 修复目标：

1. 给 `packages` 增加审核映射。
2. 确认前端 `bizAuditTabs` / `batchAuditTabs` 是否包含。
3. 确认审核、反审、批量审核按钮对该 tab 可用。

### 6.2 新增 28 个 tab 的审核 UI 可能未全部开放

后端已接 `audit_status` 和 `AccAuditController`，但前端可能未将全部 tab 放入审核按钮列表。

Claude 修复目标：

1. 检查 `apps/web/src/App.vue`：
   - `bizAuditTabs`
   - `batchAuditTabs`
   - 审核按钮显示条件
2. 确保 026–028 新增 tab 的审核 UI 与后端一致。

## 7. 文档需要更新

以下文档已经过时：

- `docs/ACC-API-完成状态报告.docx`
- `docs/acc-tab-migration-plan.md`
- `docs/acc-remaining-logic-implementation-guide.md`

需要更新的点：

1. ACC tab 状态从 `50/78` 改为 `78/78`。
2. 删除或改写“剩余 28 个 tab 未迁”的描述。
3. 增加 `026_acc_hr.sql`、`027_acc_logistics.sql`、`028_acc_customer_product.sql`。
4. 增加 `PreSubmit` 已实现。
5. 保留深层逻辑债：
   - RateEngine 全量
   - Express_Charge / 财务闭环
   - Noop 渠道/面单
   - 空字段/0 占位
   - stats 零值
   - Scale/sumy 未迁

## 8. 建议 Claude 修复顺序

### 阶段 1：修小而确定的缺口

Prompt：

```text
请根据 docs/acc-gap-report-for-claude-2026-05-27.md 先修小缺口：
1. AccStatsController 从零值改为真实聚合；
2. AccAuditController 补 packages 映射；
3. App.vue 审核按钮覆盖 026–028 新增 tab；
4. 更新 docs/acc-tab-migration-plan.md，说明 78/78 tab 已有后端路由。

要求：
- 不改大业务模型；
- 不破坏现有 /api/acc/* 响应；
- 增加必要测试或至少手动验证说明；
- 完成后汇总修改点。
```

### 阶段 2：修订单/费用列表占位字段

Prompt：

```text
继续根据 docs/acc-gap-report-for-claude-2026-05-27.md 修复已实现 tab 的占位字段。

优先：
1. AccOrdersController: sellCharge/costCharge/branch/product；
2. AccShipmentsController: supplierName、费用聚合；
3. AccCustomersController: 联系人、余额、结算、分公司、销售；
4. AccSuppliersController: 联系人、产品、余额；
5. AccReceivedsController / AccPaymentsController / AccBillsController 的 bankName/auditName/salesman。

要求：
- 每个字段必须来自真实表或 metadata，不允许继续返回空字符串/0；
- 缺字段时写 migration；
- 保持 RLS 和 tenant 隔离；
- 补测试或给出 SQL 验证。
```

### 阶段 3：修 RateEngine 和 Submit 预扣

Prompt：

```text
继续根据 docs/acc-gap-report-for-claude-2026-05-27.md 修复 RateEngine 和 Submit 预扣。

要求：
1. 对照 acc/api/APIClass.php、acc/config/Freight.php、acc/inc/Price.php；
2. RateEngine 支持客户专属价、客户组价、成本价、佣金、渠道账号限制、电池/仿牌过滤、偏远规则；
3. CustomerApiService.submitOrder 不再使用 estimatePrepayAmount 主路径；
4. Submit 用 RateEngine quote 结果生成 AR/AP charges；
5. 取号失败要回滚费用和余额；
6. 补 RateEngineTests 和 CustomerApiContractTests。
```

### 阶段 4：修费用/账单/利润闭环

Prompt：

```text
继续根据 docs/acc-gap-report-for-claude-2026-05-27.md 修复 Express_Charge / 账单闭环。

要求：
1. 建立 documentcharges service/repository/controller 或完善现有 charges 业务层；
2. 支持 AR/AP 费用生成；
3. 支持客户账单、供应商账单从费用生成；
4. 支持收款/付款、部分核销、反核销；
5. 利润按 AR - AP - 调账/退款/赔偿影响计算；
6. 所有资金变动写审计和汇率快照。
```

### 阶段 5：接真实渠道和面单

Prompt：

```text
继续根据 docs/acc-gap-report-for-claude-2026-05-27.md 替换 NoopCarrierGateway / NoopLabelGateway。

要求：
1. 使用 acc_channel_accounts / acc_logistics_interfaces 配置驱动 provider；
2. 至少实现一个真实或 sandbox adapter；
3. 保存 request/response evidence；
4. 出错时返回可读错误码；
5. 保持 Label / Relabel 现有契约不变。
```

### 阶段 6：迁 Scale / sumy

Prompt：

```text
最后补两个仍未迁的 ACC 独立 API：
1. acc/api/Scale.php
2. acc/api/sumy.php

要求：
- 先反推旧 PHP 入参、出参、鉴权、表依赖；
- 写新接口设计文档；
- 再实现 controller/service/repository；
- 补契约测试。
```

## 9. 验收清单

修复完成后必须满足：

1. `./mvnw test` 通过。
2. 78 个 `/api/acc/{tab}` 都返回非 404。
3. `/api/acc/stats` 返回真实统计。
4. `/api/acc/orders` 不再用 0/空字符串展示核心金额和分公司。
5. `/api/customer-api/rates/quote` 覆盖 ACC 主要价格规则。
6. Submit 不再用简化预扣估算。
7. Submit 后有 AR/AP 费用。
8. 客户余额流水可追溯。
9. 客户账单/供应商账单可从费用生成并核销。
10. Noop 渠道和 Noop 面单不再是生产主路径。
11. `Scale.php` / `sumy.php` 有明确迁移结论或已实现。
12. 文档不再写“50/78”或“剩余 28 个 tab 未迁”。



# ACC 当前差距报告（给 Claude 修复用）

生成日期：2026-05-27  
仓库：`/Users/chaowang/新航线/xqt-saas`  
旧系统对照：`/Users/chaowang/新航线/acc`

## 1. 当前结论

新 merge 后，ACC 后台 tab 的“有没有接口”问题已经基本解决：

- 前端 `accTabs`：78 个。
- 后端 `/api/acc/{tab}`：78 个 tab 均有对应 Controller。
- 额外后端接口：`/api/acc/stats`。
- DB migrations：从 25 个增加到 28 个，新增：
  - `026_acc_hr.sql`
  - `027_acc_logistics.sql`
  - `028_acc_customer_product.sql`

因此，旧报告里“剩余 28 个 ACC tab 未迁”的结论已经过时。

当前真正差距已经从“缺页面/缺路由”变成“业务规则不够 ACC 1:1”。

## 2. 当前硬缺口

### 2.1 仍未迁的独立 ACC API

以下两个旧 ACC 独立入口当前仍未迁：

1. `acc/api/Scale.php`
   - 能力：称重设备接口。
   - 建议新接口：`/api/customer-api/scale/*` 或 `/api/device/scale/*`。
   - 建议优先级：P2，除非客户现场已经依赖称重设备。

2. `acc/api/sumy.php`
   - 能力：第三方推单接口。
   - 建议新接口：`/api/customer-api/external-orders/sumy` 或独立 adapter。
   - 建议优先级：P2，除非有真实客户正在使用。

### 2.2 `/api/acc/stats` 仍是零值占位

文件：

- `apps/backend/src/main/java/com/xqt/saas/acc/AccStatsController.java`

当前问题：

```text
orderCount = 0
customerCount = 0
shipmentCount = 0
revenue = 0
cost = 0
profit = 0
```

需要改为真实聚合。

## 3. 主要非 1:1 逻辑差距

### 3.1 运费试算仍是 MVP

文件：

- `apps/backend/src/main/java/com/xqt/saas/rates/RateEngine.java`
- `apps/backend/src/main/java/com/xqt/saas/rates/RateRepository.java`
- `apps/backend/src/main/java/com/xqt/saas/rates/DocumentRateController.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/CustomerApiController.java`
- `apps/backend/src/test/java/com/xqt/saas/rates/RateEngineTests.java`
- `docs/acc-rates-migration.md`

旧 ACC 对照：

- `acc/api/APIClass.php?act=Price`
- `acc/config/Freight.php::getFee`
- `acc/inc/Price.php`
- `acc/inc/ProductSave.php`

当前代码已明确写明差异：

- 不支持成本价。
- 不支持客户组价。
- 不支持客户专属价。
- 不支持佣金。
- 不支持渠道账号限量：
  - `MaxCount`
  - `MaxPiece`
  - `MaxWeight`
- 不支持电池过滤。
- 不支持仿牌/敏感货过滤。
- 偏远附加费仍是固定百分比，不读取完整规则。
- `zone_code` 仍偏 MVP。
- 多段首重/续重尚未完整建模。

Claude 修复目标：

1. 读取 ACC 旧费率逻辑：
   - `acc/api/APIClass.php`
   - `acc/config/Freight.php`
   - `acc/inc/Price.php`
   - `acc/inc/ProductSave.php`
2. 扩展新系统费率模型，不要破坏已有 `RateQuoteRequest` / `RateQuoteResponse` 兼容性。
3. 支持价格优先级：
   - 客户专属价
   - 客户组价
   - 普通销售价
   - 成本价/应付价
4. 支持渠道账号限制和货物限制。
5. 输出命中规则证据，写入 quote breakdown。
6. 补测试。

验收：

- `RateEngineTests` 覆盖客户价、客户组价、普通价、偏远、禁运、渠道限制、电池/仿牌限制。
- `POST /api/customer-api/rates/quote` 和 `POST /api/document/rates/quote` 都走同一套完整逻辑。

### 3.2 Submit 预扣仍用简化估算

文件：

- `apps/backend/src/main/java/com/xqt/saas/customerapi/CustomerApiService.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/CustomerApiRepository.java`

当前问题：

`CustomerApiService.estimatePrepayAmount()` 仍是：

```text
base 30 + weight * 25 + declaredValue * 0.1%
```

这不是 ACC 全量计费，也没有接 `RateEngine` 的真实报价结果。

Claude 修复目标：

1. `submitOrder()` 中根据 `metadata.acc_compat` 构造 `RateQuoteRequest`。
2. 调用 `RateEngine.quote()`。
3. 用 quote 结果生成费用行。
4. 预扣金额使用 quote total，不再使用 `estimatePrepayAmount()`。
5. 取号失败时回滚费用和余额。

验收：

- `estimatePrepayAmount()` 被删除或只作为明确 fallback，不再是主路径。
- Submit 成功后可查到真实 AR/AP charges。
- Submit 失败不会留下已扣余额或有效费用。

### 3.3 `Express_Charge` / 余额 / 账单闭环仍不完整

当前相关文件：

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

旧 ACC 对照：

- `Express_Charge`
- `Express_Charge_Status`
- `Customer_Balance`
- `Customer_Balance_History`
- `acc/inc/Charge.php`
- `acc/inc/Cost.php`
- `acc/inc/CBill.php`
- `acc/inc/Received.php`
- `acc/inc/Pay.php`

当前缺口：

1. 费用行没有完整表达 ACC `Express_Charge.Type` 多费用类型。
2. 预扣、释放、退款、调账、账单核销没有完整资金流水链。
3. 客户账单和供应商账单与费用行的关联仍不完整。
4. 收款/付款后 paid/unpaid/status 回写口径需要统一。
5. 利润聚合口径还没和费用闭环打通。

Claude 修复目标：

1. 新增或完善 `documentcharges` 业务层。
2. Submit 后生成：
   - AR 运费、燃油、偏远、附加费
   - AP 成本、燃油、附加费
3. 写入 evidence，保留 ACC 对照字段和报价命中规则。
4. 预付客户写余额扣减和流水。
5. 月结客户只入待出账费用，不扣余额。
6. 客户账单可从 AR charges 生成。
7. 供应商账单可从 AP charges 生成。
8. 收款/付款支持部分核销、全额核销、反核销。

验收：

- Submit 成功后 `/api/acc/charges`、`/api/acc/costs` 能看到对应费用。
- 账单生成后费用不能重复入账。
- 收款后账单 paid/unpaid/status 正确。
- 利润 = AR - AP - 退款/赔偿/调账影响。

### 3.4 真实渠道取号仍是 Noop / Demo

文件：

- `apps/backend/src/main/java/com/xqt/saas/customerapi/CarrierGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/NoopCarrierGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/DemoUpsCarrierGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/DemoFedexCarrierGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/customerapi/CarrierGatewayRegistry.java`

当前问题：

- Submit 能跑通，但默认生成 `NOOP-*` 单号。
- 没有真实 UPS/FedEx/自营 EDI 出单。
- `acc_channel_accounts` 和 `acc_logistics_interfaces` 已有配置表，但尚未真正驱动 gateway。

Claude 修复目标：

1. 让 `CarrierGatewayRegistry` 根据 `channels.metadata`、`acc_channel_accounts` 或 `acc_logistics_interfaces` 路由。
2. 实现至少一个真实或可配置的 provider adapter。
3. 保存 request/response 到 evidence。
4. 渠道失败时回滚状态、费用和余额。

验收：

- 对指定 channel 不再走 `NOOP`。
- Submit 返回真实或 sandbox provider 单号。
- provider raw response 可追溯。

### 3.5 真实面单仍是 Noop

文件：

- `apps/backend/src/main/java/com/xqt/saas/labels/LabelGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/labels/NoopLabelGateway.java`
- `apps/backend/src/main/java/com/xqt/saas/labels/LabelService.java`
- `apps/backend/src/main/java/com/xqt/saas/labels/LabelController.java`

当前问题：

- 默认生成最小合法 PDF 或 ZPL，占位性质。
- 非 PDF 图片/ZPL 转 PDF 的真实 ACC 行为没有完全复刻。
- 真实渠道面单未接。

Claude 修复目标：

1. 按渠道路由 `LabelGateway`。
2. 接入真实 provider label API 或本地模拟 provider。
3. method=0 返回 base64，method=1 落文件并返回 URL。
4. 子单号写 cartons。
5. 保持 `getNewLabel.php` 的 relabel 查询逻辑。

验收：

- 真实渠道或 sandbox 能生成可下载面单。
- 批量生成时单条失败不破坏整批。
- `relabel` 多页 PDF 分页仍通过测试。

## 4. 后台 tab 当前状态

### 4.1 已解决

以下旧结论已过时：

```text
剩余 28 个 ACC tab 未迁
```

当前 78 个 tab 都有后端路由。

### 4.2 仍需注意：新增 28 个多为 CRUD/视图级实现

新增模块基本有：

- DB 表
- list/raw/create/update/delete
- audit_status
- FieldGate
- CascadeChecker

但大多不是旧 ACC 复杂业务逻辑 1:1。

典型例子：

1. `AccEmployeesController`
   - 有员工 CRUD。
   - 未完整复刻旧 `Employee.php` 的复杂权限、薪资、关联逻辑。

2. `AccCommissionsController`
   - 有提成记录 CRUD。
   - 未自动按利润/销售额生成提成。

3. `AccProductsController`
   - 是 `rate_cards + services` 聚合只读视图。
   - 不等价旧 ACC 产品/价格维护全流程。

4. `AccZonesController`
   - 是 `rate_card_lines.zone_code` 去重聚合视图。
   - 不等价完整价格分区维护。

5. `AccQuickOrdersController`
   - 是 orders 的简化只读视图。
   - 不是完整快速下单流程。

6. `AccLogisticsInterfacesController`
   - 能维护物流接口配置。
   - 但配置尚未真正驱动渠道取号/面单。

## 5. 已实现 tab 中仍有空字段 / 0 占位

需要优先修以下文件：

| 文件 | 当前问题 | 修复方向 |
| --- | --- | --- |
| `AccOrdersController.java` | `sellCharge=0`、`costCharge=0`、`branch=""`、`product` 不完整 | join charges/costs/organizations/metadata |
| `AccShipmentsController.java` | `supplierName=""`，费用聚合不足 | join partner/channel/account/cost |
| `AccCustomersController.java` | 联系人、余额、结算、分公司、销售等字段不足 | join contacts/accounts/settlement/org/users |
| `AccSuppliersController.java` | 联系人、产品、余额不足 | join partner accounts/channels/AP |
| `AccWarehousesController.java` | consignee/company/postcode 空 | 扩展 warehouses 或 join addresses |
| `AccBranchesController.java` | contact/phone/address/remark 空 | 扩展 organizations |
| `AccReturnsController.java` | amount=0 | join 退件费用/退款费用 |
| `AccReceivedsController.java` | bankName 空 | join financial_accounts/bank_names |
| `AccPaymentsController.java` | auditName 空 | join audit_events 或付款审核字段 |
| `AccBillsController.java` | salesman 空 | join 客户销售归属 |
| `AccStatsController.java` | 全部 0 | 真实聚合 |

## 6. 审核和前端集成小缺口

### 6.1 `packages` 审核映射可能缺失

需要检查：

- `apps/backend/src/main/java/com/xqt/saas/acc/AccAuditController.java`
- `apps/web/src/App.vue`

问题：

- `packages` 有 controller。
- 但如果 `TAB_TO_TABLE` 没有映射，点击审核可能报 `unknown ACC tab`。

Claude 修复目标：

1. 给 `packages` 增加审核映射。
2. 确认前端 `bizAuditTabs` / `batchAuditTabs` 是否包含。
3. 确认审核、反审、批量审核按钮对该 tab 可用。

### 6.2 新增 28 个 tab 的审核 UI 可能未全部开放

后端已接 `audit_status` 和 `AccAuditController`，但前端可能未将全部 tab 放入审核按钮列表。

Claude 修复目标：

1. 检查 `apps/web/src/App.vue`：
   - `bizAuditTabs`
   - `batchAuditTabs`
   - 审核按钮显示条件
2. 确保 026–028 新增 tab 的审核 UI 与后端一致。

## 7. 文档需要更新

以下文档已经过时：

- `docs/ACC-API-完成状态报告.docx`
- `docs/acc-tab-migration-plan.md`
- `docs/acc-remaining-logic-implementation-guide.md`

需要更新的点：

1. ACC tab 状态从 `50/78` 改为 `78/78`。
2. 删除或改写“剩余 28 个 tab 未迁”的描述。
3. 增加 `026_acc_hr.sql`、`027_acc_logistics.sql`、`028_acc_customer_product.sql`。
4. 增加 `PreSubmit` 已实现。
5. 保留深层逻辑债：
   - RateEngine 全量
   - Express_Charge / 财务闭环
   - Noop 渠道/面单
   - 空字段/0 占位
   - stats 零值
   - Scale/sumy 未迁

## 8. 建议 Claude 修复顺序

### 阶段 1：修小而确定的缺口

Prompt：

```text
请根据 docs/acc-gap-report-for-claude-2026-05-27.md 先修小缺口：
1. AccStatsController 从零值改为真实聚合；
2. AccAuditController 补 packages 映射；
3. App.vue 审核按钮覆盖 026–028 新增 tab；
4. 更新 docs/acc-tab-migration-plan.md，说明 78/78 tab 已有后端路由。

要求：
- 不改大业务模型；
- 不破坏现有 /api/acc/* 响应；
- 增加必要测试或至少手动验证说明；
- 完成后汇总修改点。
```

### 阶段 2：修订单/费用列表占位字段

Prompt：

```text
继续根据 docs/acc-gap-report-for-claude-2026-05-27.md 修复已实现 tab 的占位字段。

优先：
1. AccOrdersController: sellCharge/costCharge/branch/product；
2. AccShipmentsController: supplierName、费用聚合；
3. AccCustomersController: 联系人、余额、结算、分公司、销售；
4. AccSuppliersController: 联系人、产品、余额；
5. AccReceivedsController / AccPaymentsController / AccBillsController 的 bankName/auditName/salesman。

要求：
- 每个字段必须来自真实表或 metadata，不允许继续返回空字符串/0；
- 缺字段时写 migration；
- 保持 RLS 和 tenant 隔离；
- 补测试或给出 SQL 验证。
```

### 阶段 3：修 RateEngine 和 Submit 预扣

Prompt：

```text
继续根据 docs/acc-gap-report-for-claude-2026-05-27.md 修复 RateEngine 和 Submit 预扣。

要求：
1. 对照 acc/api/APIClass.php、acc/config/Freight.php、acc/inc/Price.php；
2. RateEngine 支持客户专属价、客户组价、成本价、佣金、渠道账号限制、电池/仿牌过滤、偏远规则；
3. CustomerApiService.submitOrder 不再使用 estimatePrepayAmount 主路径；
4. Submit 用 RateEngine quote 结果生成 AR/AP charges；
5. 取号失败要回滚费用和余额；
6. 补 RateEngineTests 和 CustomerApiContractTests。
```

### 阶段 4：修费用/账单/利润闭环

Prompt：

```text
继续根据 docs/acc-gap-report-for-claude-2026-05-27.md 修复 Express_Charge / 账单闭环。

要求：
1. 建立 documentcharges service/repository/controller 或完善现有 charges 业务层；
2. 支持 AR/AP 费用生成；
3. 支持客户账单、供应商账单从费用生成；
4. 支持收款/付款、部分核销、反核销；
5. 利润按 AR - AP - 调账/退款/赔偿影响计算；
6. 所有资金变动写审计和汇率快照。
```

### 阶段 5：接真实渠道和面单

Prompt：

```text
继续根据 docs/acc-gap-report-for-claude-2026-05-27.md 替换 NoopCarrierGateway / NoopLabelGateway。

要求：
1. 使用 acc_channel_accounts / acc_logistics_interfaces 配置驱动 provider；
2. 至少实现一个真实或 sandbox adapter；
3. 保存 request/response evidence；
4. 出错时返回可读错误码；
5. 保持 Label / Relabel 现有契约不变。
```

### 阶段 6：迁 Scale / sumy

Prompt：

```text
最后补两个仍未迁的 ACC 独立 API：
1. acc/api/Scale.php
2. acc/api/sumy.php

要求：
- 先反推旧 PHP 入参、出参、鉴权、表依赖；
- 写新接口设计文档；
- 再实现 controller/service/repository；
- 补契约测试。
```

## 9. 验收清单

修复完成后必须满足：

1. `./mvnw test` 通过。
2. 78 个 `/api/acc/{tab}` 都返回非 404。
3. `/api/acc/stats` 返回真实统计。
4. `/api/acc/orders` 不再用 0/空字符串展示核心金额和分公司。
5. `/api/customer-api/rates/quote` 覆盖 ACC 主要价格规则。
6. Submit 不再用简化预扣估算。
7. Submit 后有 AR/AP 费用。
8. 客户余额流水可追溯。
9. 客户账单/供应商账单可从费用生成并核销。
10. Noop 渠道和 Noop 面单不再是生产主路径。
11. `Scale.php` / `sumy.php` 有明确迁移结论或已实现。
12. 文档不再写“50/78”或“剩余 28 个 tab 未迁”。

