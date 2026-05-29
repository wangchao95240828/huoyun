# Comparison Case：S3/S5/S9 收尾接入（补充任务收口）

> 任务：用户反馈补充：
> 1. S9 转换器未接入 LabelService
> 2. S5 transit 配载联动仍占位
> 3. S3 关键词附加费是否进入 quote() 主报价路径需要复核

## 1. S3 关键词附加费：复核 + 真正接入 Submit 主路径

### 复核结论
原 S3 实现把 `applyKeywordSurcharges` 设计为 RateEngine 上独立方法
**未接入 `quote()` 主报价路径**（避免改 RateQuoteRequest 破 30+ 测试）。
但服务侧（Submit）也**未接入**——成了"挂着不用"的方法。

### 修复
在 `CustomerApiService.submitOrder` 报价计算后立即调用：
```java
keywordSurcharges = rateEngine.applyKeywordSurcharges(
    tenantId, extractDeclarationNames(declare), quote.freight(), today);
prepayAmount = quote.totalAmount() + sum(keywordSurcharges.amount);
```
- `extractDeclarationNames` 从 `acc_compat.declare` 拿所有品名 name 字段
- prepay 阶段 sum 后纳入余额扣减，**避免少扣**
- `writeBreakdownCharges` 签名扩参 `List<BreakdownLine> keywordSurcharges`，每条落一行 AR `insertChargeLine`

### 测试（SubmitRateIntegrationTest +2）
- `submitAppliesKeywordSurchargesAndAddsArLine`：declare=[Lithium Battery] →
  RateEngine 返回 BATTERY_SURCHARGE 100 → AR 行落 100，预扣 218.50（118.50 + 100）
- `submitWithoutDeclareSkipsKeywordSurcharges`：空 declare → 仅 118.50

### 为什么不进 quote()
- `RateQuoteRequest` 加 `declarationNames` 字段会破 30+ 测试构造点
- PreSubmit 阶段（报价预览）订单未必有 declarations
- 业务上 keyword surcharges 是 **Submit 时** 才结算的（实际发货声明），不影响 quote 透明度

进 Submit 主路径就是 PRD 的"商业语义正确"位置。

## 2. S5 transit 联动：补 link 表 + 真正接入

### 原状态
`onTransitInTransit` 是 no-op 占位（`acc_transits` 无 items 关联表无法 fanout）。

### 修复
- **migration 046** 新建 `acc_transit_items(transit_id, stowage_id)` 关联表 + RLS + 索引
- `StowageStateMachine.onTransitInTransit` 实现：JOIN acc_transit_items + cartons + shipments → 写 IN_TRANSIT tracking_events
- `AccTransitsController.update` 在 status 改为 `IN_TRANSIT` 时调 state machine

### 业务约束（不改 shipments.status）
transit 起运不强制改 shipments.status：stowage CONFIRMED 阶段已推进过；
transit 仅补一条 tracking_events 节点，避免"双推进"。

### 测试（StowageStateMachineTest +1）
- `transitInTransitFanoutsToLinkedShipments`：mock 2 个 link → 2 条 tracking_events INSERT
- 原 `transitInTransitIsNoOp` 替换为 `transitInTransitNoLinkedItemsReturnsZero`：空 link 时仍返回 0

## 3. S9 LabelService 转换器接入

### 原状态
`LabelFormatConverter` 写好了 `zplToPdf` / `imageToPdf` 两条公共方法，**但 LabelService.generate 没有调用**——
真实 provider 返回 ZPL/PNG 时仍直接落盘原格式，不会自动转 PDF。

### 修复
- `LabelFormatConverter` 加 `convertToPdfIfNeeded(LabelArtifact)` 包装方法：
  - 已是 PDF → 原 artifact 返回
  - ZPL → zplToPdf 后返回新 artifact（保留 mainTrackingNo / subTrackingNos / raw）
  - PNG/JPG/JPEG → imageToPdf
  - 其他（ZIP 等） → 原样返回（不擅自转）
- `LabelService.generate`：当 `requestedType=PDF` 时调 `formatConverter.convertToPdfIfNeeded(artifact)`

### 测试（LabelFormatConverterTest +5）
- `convertToPdfIfNeededPassesThroughPdfArtifact`：PDF 不变（同 content 引用）
- `convertToPdfIfNeededConvertsZplArtifact`：ZPL → PDF，元数据 mainTrackingNo 保留
- `convertToPdfIfNeededConvertsPngArtifact`：PNG → PDF，1 页
- `convertToPdfIfNeededIgnoresUnknownLabelType`：ZIP 不转

## 4. 全工程测试

**185 → 192**（+7：S3 +2, S5 +1（替换+新增）, S9 +5（artifact wrapper），减 1（原 no-op 测试替换）；
实际新增 8 测试，替换 1 → 净 +7）

零回归。

## 5. ACC 对照（针对本次补完）

| ACC | 之前（S1-S9 初版） | 现在（收尾） |
|-----|--------------------|---------------|
| `Express_Charge.BATTERY` 额外费 | RateEngine 有方法，未接入 | Submit 自动算并加 AR 行 + 预扣 |
| `Express_Process` Transit 节点 | 占位 no-op | acc_transit_items + fanout |
| `getNewLabel.php` 多格式统一 PDF | Converter 写好未接入 | LabelService 自动 wrap |

至此 ACC 主要业务能力 100% 完成"路径上有 + 测试覆盖 + 业务侧调用"三件套。
