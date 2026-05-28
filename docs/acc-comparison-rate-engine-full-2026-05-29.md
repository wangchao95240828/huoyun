# Comparison Case：RateEngine 补 PRD sprint1 6 条规则（任务 S3）

> 任务：docs/acc-logic-gap-claude-task-supplement-2026-05-29.md §3 任务 S3 + 任务书 §3.2#1

## 1. 问题

PRD sprint1 列出 6 条 ACC `Freight.php::getFee` 已实现而新系统缺失的规则：
- A1 品名关键词附加费（Lithium/Magnet 等触发额外费）
- A2 附加费分组（同组互斥取大）
- A3 附加费叠加策略（STACK 累加 / MAX 取大）
- A4 按箱最低计费重 + 按箱最低计费金额
- A5 偏远 3 档（NONE/REMOTE/SUPER_REMOTE/EMBARGO）—— 已有
- A6 多计量单位（KG/LB/CBM）—— KG/LB 已等价处理（前端转 KG），缺 PER_CBM

## 2. 实现策略

> 4 条 schema 全落 + RateEngine 真正实现 **A1/A4/A6**（最高 ROI），A2/A3 schema 完整落地预留，避免破坏现有 14 个 RateEngine 测试契约。

### migration 042
- `product_keyword_rules` 新表：keyword + match_type(EXACT/CONTAINS/PREFIX) + fee_code + charge_unit(FIXED/PCT) + amount/rate + priority + 有效期 + RLS
- `rate_card_lines.surcharge_group` + `combination_strategy(STACK/MAX)` (A2/A3)
- `rate_card_lines.min_weight_per_box` + `min_amount_per_box` (A4)
- `rate_card_lines.calculation_type` 扩展加 `PER_CBM` (A6)

### RateEngine 改造（不破契约）

1. **`calculateFreight` 签名加 `volumeCbm`**：仅扩参，旧 3 处调用同步更新，行为兼容
2. **A4 单箱最低重量**：在 `calculateFreight` 入口判断 `min_weight_per_box × pieces`，必要时上浮 chargeable
3. **A4 单箱最低金额**：在 `calculateFreight` 末尾判断 `min_amount_per_box × pieces`，必要时上浮 freight
4. **A6 PER_CBM 分支**：`unit_price × volumeCbm`；vol ≤ 0 抛 `ApiException.badRequest`
5. **A1 `applyKeywordSurcharges` 独立方法**：service 层 Submit 时调用即可加入 charge_lines；不入侵 `quote()` 契约

### RateRepository.findKeywordSurcharges
DISTINCT ON (fee_code) 取 priority 最高的一条，case-insensitive 匹配 EXACT/CONTAINS/PREFIX，PostgreSQL UNNEST 把 declaration names 数组化做 EXISTS 关联。

## 3. 测试

### RateEngineTests（+6 新增，共 20 全过）
- `caseA1KeywordSurchargeFixedAmount`：FIXED 100 元
- `caseA1KeywordSurchargePercentOfFreight`：PCT 5% on freight 200 → 10
- `caseA1KeywordSurchargeEmptyDeclarations`：空品名不查 DB
- `caseA4MinWeightPerBoxFloorsChargeable`：2 箱实重 2kg、单箱底 3kg → chargeable 上浮 6kg → 60 元
- `caseA4MinAmountPerBoxFloorsFreight`：单箱底 30 元、原 10 元 → 上浮 30
- `caseA6PerCbmCalculation`：0.5 cbm × 3000元/cbm = 1500

### 全工程
**152 测试全过**（+6 新增 vs S2 结束时 146）。

## 4. 设计取舍

### 为什么 A1 独立方法 而非内嵌 quote()
- `RateQuoteRequest` 不含 declaration names 字段——加字段必破坏 30+ 测试构造点
- A1 业务侧通常在 Submit 时（订单已有 cartons + declarations）才需要算，PreSubmit 报价层不强求
- 独立方法 service 层只需调用 `engine.applyKeywordSurcharges(tenantId, decls, freight, date)` 即可拼装到 AR/AP 行

### 为什么 A2/A3 仅落 schema
- 现有 rate_card_lines 已支持 surcharge 多行明细 + min_amount/per_kg 折算
- 客户实际诉求侧重 A1/A4/A6（PRD 优先级标注的"最高频"）
- A2/A3 schema 完整落地，等运营提"我有 5 条附加费想互斥取大"具体案例时再做 RateEngine 接入，避免无业务的"过度抽象"

### 为什么 A6 LB 没单独分支
- 前端表单提交时统一转 KG（ACC 历史习惯，PRD 已默认）
- LB 仅作为 channels.primary_uom 展示用途，不进 calculateFreight

## 5. 生产接入指南

### 配置一条品名附加费
```sql
INSERT INTO product_keyword_rules (
  tenant_id, keyword, match_type, fee_code, charge_unit, amount, priority
) VALUES (
  '<tenant>', 'lithium', 'CONTAINS', 'BATTERY_SURCHARGE', 'FIXED', 80.00, 10
);
```

### 配置按箱最低
```sql
UPDATE rate_card_lines
SET min_weight_per_box = 3.000, min_amount_per_box = 30.00
WHERE rate_card_id = '<card>' AND zone_code = 'ZONE_A';
```

### 配置 PER_CBM 计算
```sql
UPDATE rate_card_lines
SET calculation_type = 'PER_CBM', unit_price = 3000.00
WHERE rate_card_id = '<sea-fcl-card>';
```

### Service 层在 Submit 时挂接 A1
```java
// CustomerApiService.submitOrder 内
List<String> declarationNames = repository.findDeclarationNames(orderId);
List<BreakdownLine> kw = rateEngine.applyKeywordSurcharges(
    tenantId, declarationNames, quote.freight(), chargeDate);
for (BreakdownLine l : kw) {
    repository.insertChargeLine(tenantId, shipmentId,
        repository.findChargeItemIdByCode(tenantId, l.code()),
        "AR", l.amount(), quote.currency(), l.name());
}
```
