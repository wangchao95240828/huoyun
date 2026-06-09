# PRD：Sprint 1 — 计费引擎强化

**版本**: 1.0  
**日期**: 2026-05-14  
**关联主 PRD**: `prd-code-adjustment-2026Q2.md`  
**迁移文件**: `db/migrations/019_billing_engine_v2.sql`  
**优先级**: P1（全部为阻塞业务的核心计费逻辑）  
**排期**: 2 周

---

## 背景与目标

当前费率引擎（`rates/RateEngine.java`）在以下场景计费不准或缺失：

| 问题 | 业务影响 |
|------|---------|
| 无品名附加费匹配 | 带电池/液体品类漏收附加费，利润损失 |
| 附加费只能叠加，无取大逻辑 | 重复收取同类费用，客户纠纷 |
| 计费按票不按箱 | 多箱低重货漏收最低计费费用 |
| 偏远只有一档 | 超远/禁运区域处理缺失，可能违规操作 |
| 只支持 KG 单位 | 海运/整柜渠道无法按 CBM 计费 |
| Dim Factor 公式固定 | 无法配置不同渠道的体积重混合策略 |

**目标**：以上六类场景在报价和制单时全部自动计算，操作员无需手工干预。

---

## 范围与假设

**本期做**：
- A1 品名关键词附加费规则 CRUD 及报价集成
- A2/A3 附加费叠加 vs 取大策略
- A4 每箱最低计费重/最低计费金额
- A5 偏远三档（REMOTE / SUPER_REMOTE / EMBARGO）
- A6 多计费单位（KG / LB / CBM / PIECE）
- A7 Dim Factor 混合比例参数化

**本期不做**：
- A8 燃油月度自动化（依赖 finance_waybill_audit 改造）
- A9–A12（保险/报关/黑名单）
- 已有价卡 CRUD 的 UI 重构（本期只新增字段）

**前置假设**：
- `rate_cards` / `rate_card_lines` / `remote_zones` 表已存在
- `finance_fee_type` 表已存在并有初始费用科目数据
- 前端使用 Vue 3 + Element Plus；API 走 `/api/` 前缀，Bearer JWT 鉴权

---

## 用户与场景

| 角色 | 场景 |
|------|------|
| **运营/报价员** | 维护品名规则、偏远区域、价卡参数；查看报价明细 |
| **客服/制单员** | 下单时看到附加费明细，确认无误后提交 |
| **系统（自动）** | 调用 `RateEngine.quote()` 计算报价，返回完整费用行列表 |

---

## 一、A1 品名附加费自动匹配

### 1.1 数据模型

新建表 `product_keyword_rules`（在 `019_billing_engine_v2.sql`）：

```sql
CREATE TABLE product_keyword_rules (
    id             bigserial    PRIMARY KEY,
    tenant_id      uuid         NOT NULL,
    keyword        text         NOT NULL,
    match_type     text         NOT NULL DEFAULT 'CONTAINS',
    -- CONTAINS: 包含子串（不区分大小写）
    -- EXACT:    全等（不区分大小写）
    -- PREFIX:   前缀匹配

    fee_code       text         NOT NULL,
    -- 对应 finance_fee_type.code，如 'BATTERY_SURCHARGE'

    charge_unit    text         NOT NULL DEFAULT 'FIXED',
    -- FIXED: 固定金额（使用 amount 字段）
    -- PCT:   占运费比例（使用 rate 字段，0.05 = 5%）

    amount         numeric(12,2),
    rate           numeric(6,4),
    currency       text         NOT NULL DEFAULT 'CNY',
    priority       int          NOT NULL DEFAULT 0,
    -- 同 fee_code 多条规则命中时取 priority 最高的一条

    is_active      boolean      NOT NULL DEFAULT true,
    effective_from date,
    effective_to   date,
    -- NULL 表示无限期有效

    created_at     timestamptz  NOT NULL,
    created_by     text         NOT NULL,
    updated_at     timestamptz  NOT NULL,
    updated_by     text         NOT NULL
);

CREATE INDEX idx_pkr_tenant_active
    ON product_keyword_rules (tenant_id, is_active);

ALTER TABLE product_keyword_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_keyword_rules FORCE  ROW LEVEL SECURITY;
CREATE POLICY pkr_tenant_isolation ON product_keyword_rules
    USING (app_tenant_matches(tenant_id))
    WITH CHECK (app_tenant_matches(tenant_id));
```

### 1.2 后端接口

**规则 CRUD**

```
POST   /api/rate-cards/keyword-rules          创建规则
GET    /api/rate-cards/keyword-rules?page&size 分页查询
PUT    /api/rate-cards/keyword-rules/{id}      更新规则
DELETE /api/rate-cards/keyword-rules/{id}      删除规则
```

请求体示例（POST/PUT）：
```json
{
  "keyword": "锂电池",
  "matchType": "CONTAINS",
  "feeCode": "BATTERY_SURCHARGE",
  "chargeUnit": "FIXED",
  "amount": 50.00,
  "currency": "CNY",
  "priority": 10,
  "isActive": true,
  "effectiveFrom": "2026-01-01",
  "effectiveTo": null
}
```

**RateEngine 集成**

`RateEngine.quote(QuoteRequest req)` 新增品名匹配步骤：

1. 查询 `product_keyword_rules`（当前 tenant，`is_active=true`，`effective_from ≤ today ≤ effective_to or null`）
2. 遍历规则，按 `match_type` 对 `req.productDescription` 做匹配（大小写不敏感）
3. 命中规则按 `fee_code` 分组，每组取 `priority` 最高的规则
4. 按 `charge_unit` 计算金额（FIXED 直接取 amount；PCT 乘以基础运费）
5. 将每个 fee_code 对应的金额追加到 `QuoteResponse.chargeLines`

### 1.3 前端交互

**路径**：价卡管理 → 品名附加费规则（新增 Tab）

- 列表：关键词 / 匹配方式 / 费用科目 / 金额 / 生效日期 / 状态开关
- 新增/编辑：侧抽屉表单，字段同请求体
- 报价详情：命中规则时，费用行显示来源标注，如：  
  `电池附加费 ¥50.00  [品名匹配: "锂电池"]`

### 1.4 验收标准（GWT）

```
场景1：规则命中，固定金额附加
Given 存在规则 keyword="电池" matchType=CONTAINS feeCode=BATTERY_SURCHARGE amount=50 is_active=true
When  调用 quote，productDescription="18650锂电池"
Then  chargeLines 包含 {feeCode:"BATTERY_SURCHARGE", amount:50, source:"KEYWORD_RULE"}

场景2：规则已过期，不应用
Given 同上规则，effectiveTo=2025-12-31
When  今天（2026-05-14）调用 quote
Then  chargeLines 不包含 BATTERY_SURCHARGE

场景3：同一 feeCode 多条规则命中，取优先级高的
Given 规则A priority=5 amount=30，规则B priority=10 amount=50，两者 keyword 都匹配
When  调用 quote
Then  chargeLines 中 BATTERY_SURCHARGE.amount = 50（取规则B）

场景4：productDescription 为空
When  调用 quote，productDescription=""
Then  无品名附加费，quote 正常返回其他费用行

场景5：规则按比例计费
Given 规则 chargeUnit=PCT rate=0.05，基础运费=1000
When  调用 quote
Then  chargeLines 包含 {feeCode:..., amount:50}（1000×0.05）
```

### 1.5 边界情况

- 同一品名命中不同 fee_code 的多条规则 → 全部追加（不同费用科目可并存）
- 大小写不敏感："Battery" 和 "battery" 和 "电池battery" 均视为命中 "battery"
- `match_type=EXACT` 时，"锂电池组" 不命中 "锂电池"（精确匹配）

---

## 二、A2/A3 附加费叠加 vs 取大策略

### 2.1 数据模型

在 `019_billing_engine_v2.sql` 中修改 `rate_card_lines`：

```sql
ALTER TABLE rate_card_lines
    ADD COLUMN surcharge_group    text,
    -- NULL = 独立行，不参与分组策略；
    -- 相同字符串 = 同组，如 'FUEL', 'REMOTE'

    ADD COLUMN combination_strategy text NOT NULL DEFAULT 'STACK';
    -- STACK: 同组所有命中行金额累加
    -- MAX:   同组所有命中行取最大金额
    -- 仅在 surcharge_group IS NOT NULL 时生效
```

### 2.2 RateEngine 逻辑

```
1. 计算所有命中的 rate_card_lines 的金额（原有逻辑不变）
2. 将命中行按 surcharge_group 分组：
   - surcharge_group IS NULL → 直接加入结果（STACK 行为）
   - surcharge_group IS NOT NULL → 按组聚合：
       STACK: sum(amounts in group)
       MAX:   max(amounts in group)
3. 输出聚合后的费用行列表
```

### 2.3 后端接口

`rate_card_lines` 的 CRUD 接口新增字段（已有接口扩展，无新增路径）：

```json
{
  "surchargeName": "燃油附加费-旺季",
  "surchargeGroup": "FUEL",
  "combinationStrategy": "MAX",
  "amount": 80.00
}
```

### 2.4 前端交互

价卡行编辑表单新增：
- **附加费组**：文本输入（同组填相同名称，留空=不分组）
- **取值策略**：下拉选择（叠加 / 取大），仅在填写了附加费组时可见
- 价卡行列表增加"附加费组"列，同组行高亮同色

### 2.5 验收标准（GWT）

```
场景1：同组 MAX，取高值
Given 两条 surcharge_group='FUEL' combination_strategy='MAX'的价卡行，分别计算金额 30 和 50，两者都命中
When  调用 quote
Then  FUEL 组合并后金额 = 50

场景2：同组 STACK，累加
Given 两条 surcharge_group='REMOTE' combination_strategy='STACK'，金额 20 和 30 均命中
When  调用 quote
Then  REMOTE 组金额 = 50

场景3：不同组互不影响
Given FUEL 组 MAX=50，REMOTE 组 STACK=50，各自命中
When  调用 quote
Then  chargeLines 包含 FUEL 费用行 50 + REMOTE 费用行 50，合计 100

场景4：surcharge_group=NULL 的行直接叠加
Given 三条 surcharge_group=NULL 的行，金额分别 10、20、30，均命中
When  调用 quote
Then  三行各自出现在 chargeLines，合计 60
```

---

## 三、A4 按箱最低计费重 / 最低计费金额

### 3.1 数据模型

```sql
ALTER TABLE rate_card_lines
    ADD COLUMN min_weight_per_box numeric(8,3),
    -- 每箱最低计费重（KG），NULL = 不设下限
    ADD COLUMN min_amount_per_box numeric(12,2);
    -- 每箱最低计费金额，NULL = 不设下限
```

订单/报价请求中需携带每箱信息（已有字段确认）：

```json
{
  "boxes": [
    { "actualWeight": 0.2, "lengthCm": 10, "widthCm": 10, "heightCm": 10 },
    { "actualWeight": 0.3, "lengthCm": 12, "widthCm": 8,  "heightCm": 8  }
  ]
}
```

### 3.2 RateEngine 逻辑

```
对每个 box：
  dim_weight = (length × width × height) / dim_factor
  chargeable_weight_this_box = MAX(
      actual_weight,
      dim_weight,
      min_weight_per_box ?? 0
  )

total_chargeable_weight = SUM(chargeable_weight_this_box for all boxes)
base_amount = total_chargeable_weight × unit_rate

if min_amount_per_box IS NOT NULL:
    floor_amount = min_amount_per_box × box_count
    base_amount = MAX(base_amount, floor_amount)
```

### 3.3 前端交互

- 价卡行编辑新增"每箱最低计费重(KG)"和"每箱最低计费金额"两个数字输入框
- 报价详情：当最低计费重生效时，在费用行旁显示小标注  
  `基础运费 ¥120（3箱×最低0.5KG计费）`
- 订单填写表单：箱数/每箱重量/尺寸为必填项（当选中的渠道价卡有 min_weight_per_box 配置时前端校验提示）

### 3.4 验收标准（GWT）

```
场景1：每箱实重低于最低计费重，以最低计费重计
Given rate_card_line.min_weight_per_box = 0.5
When  调用 quote，3 个箱子各 0.2 KG 实重，体积重 0.1 KG
Then  total_chargeable_weight = 0.5 × 3 = 1.5 KG

场景2：某箱实重超过最低计费重，以实重计
Given min_weight_per_box = 0.5，1个箱子 0.8 KG 实重
When  调用 quote
Then  该箱计费重 = 0.8 KG（实重胜出）

场景3：min_weight_per_box = NULL，行为不变
When  调用 quote
Then  按原始 MAX(actual, dim) 逻辑计算，无变化

场景4：最低计费金额兜底
Given min_amount_per_box = 20，3 个箱子，计算得基础运费 ¥30
When  调用 quote
Then  floor_amount = 20 × 3 = 60；base_amount = MAX(30, 60) = ¥60

场景5：boxes 数组为空（仅传 total_weight）
When  调用 quote，无 boxes 字段
Then  API 返回 400 "按箱计费渠道必须传入 boxes 明细"
```

---

## 四、A5 偏远三档（REMOTE / SUPER_REMOTE / EMBARGO）

### 4.1 数据模型

```sql
ALTER TABLE remote_zones
    ADD COLUMN zone_type text NOT NULL DEFAULT 'REMOTE';
    -- REMOTE:       普通偏远，收偏远附加费
    -- SUPER_REMOTE: 超远，收超远附加费（通常更贵）
    -- EMBARGO:      禁运，拒绝下单
```

`rate_card_lines` 新增偏远类型的费用科目映射（通过 `fee_type` 字段区分，已有机制）：

- `REMOTE_SURCHARGE` → 对应 REMOTE 区域
- `SUPER_REMOTE_SURCHARGE` → 对应 SUPER_REMOTE 区域

### 4.2 RateEngine / 下单逻辑

**报价阶段**（`RateEngine.quote()`）：
1. 查询 `remote_zones` 是否命中目的地邮编
2. `EMBARGO` → 抛出 `EmbargoZoneException("该邮编 {postcode} 属于禁运区域，无法报价")`
3. `SUPER_REMOTE` → 在 chargeLines 追加 SUPER_REMOTE_SURCHARGE 行
4. `REMOTE` → 在 chargeLines 追加 REMOTE_SURCHARGE 行
5. 未命中 → 不追加偏远费

**下单阶段**（`OrderService.create()`）：
- 同样校验，`EMBARGO` 时下单接口返回 HTTP 400

### 4.3 前端交互

**偏远区域管理**（已有页面扩展）：
- 列表新增"区域类型"列，显示 普通偏远 / 超远 / 禁运
- 新增/编辑行：区域类型下拉（三选一）
- 批量导入 Excel 支持 zone_type 列

**报价/下单页**：
- EMBARGO：红色警告框 `⛔ 目的地邮编 {postcode} 为禁运区域，无法下单`，提交按钮禁用
- SUPER_REMOTE：黄色提示 `⚠️ 超远附加费 ¥{amount}` 显示在费用明细中
- REMOTE：普通提示 `ℹ️ 偏远附加费 ¥{amount}`

### 4.4 验收标准（GWT）

```
场景1：EMBARGO 区域，报价被拒
Given remote_zones 中邮编 "EX123" zone_type = 'EMBARGO'
When  调用 POST /api/rate-cards/quote，destinationPostcode = "EX123"
Then  HTTP 400，body.message = "该邮编 EX123 属于禁运区域，无法报价"

场景2：EMBARGO 区域，下单被拒
Given 同上
When  调用 POST /api/orders，destinationPostcode = "EX123"
Then  HTTP 400，body.message = "该邮编 EX123 属于禁运区域，无法下单"

场景3：SUPER_REMOTE 区域，正常计费
Given 邮编 "SR456" zone_type = 'SUPER_REMOTE'
  And rate_card_line for SUPER_REMOTE_SURCHARGE amount = 120
When  调用 quote
Then  HTTP 200，chargeLines 包含 {feeCode:"SUPER_REMOTE_SURCHARGE", amount:120}

场景4：普通 REMOTE，正常计费
Given 邮编 "R789" zone_type = 'REMOTE'
  And rate_card_line for REMOTE_SURCHARGE amount = 50
When  调用 quote
Then  chargeLines 包含 {feeCode:"REMOTE_SURCHARGE", amount:50}

场景5：邮编无匹配，无偏远费
Given 邮编 "NR000" 不在 remote_zones 中
When  调用 quote
Then  chargeLines 不包含任何偏远相关费用科目

场景6：同一邮编同时存在 REMOTE 和 SUPER_REMOTE 记录（数据异常）
When  调用 quote
Then  以 zone_type 严重程度最高者为准（SUPER_REMOTE > REMOTE）
```

---

## 五、A6 多计费单位（KG / LB / CBM / PIECE）

### 5.1 数据模型

```sql
ALTER TABLE rate_card_lines
    ADD COLUMN uom text NOT NULL DEFAULT 'KG';
    -- KG:    按公斤（默认，现有逻辑）
    -- LB:    按磅（1 LB = 0.453592 KG，转换后按 KG 逻辑计算）
    -- CBM:   按立方米（amount = cbm × unit_rate）
    -- PIECE: 按件/箱（amount = piece_count × unit_rate）
```

报价请求扩展字段（若渠道为 CBM/PIECE 时需前端提供）：

```json
{
  "totalWeightKg": 10,
  "totalVolumeCbm": 0.5,
  "totalPieces": 3,
  "boxes": [...]
}
```

### 5.2 RateEngine 路由逻辑

```java
switch (line.getUom()) {
    case "KG"    -> chargeableWeight = computeKgWeight(req);   // 现有逻辑
    case "LB"    -> chargeableWeight = computeKgWeight(req);   // 同 KG，输入时已转换
    case "CBM"   -> amount = req.getTotalVolumeCbm() * line.getUnitRate();
    case "PIECE" -> amount = req.getTotalPieces()    * line.getUnitRate();
}
```

LB 输入转换：前端负责将磅转为公斤后传入（或后端接收 LB 字段时自动转换，接口文档注明）。

### 5.3 前端交互

- 价卡行编辑表单：计费单位下拉（KG / LB / CBM / PIECE）
- 订单/报价表单：检测选中渠道的 uom：
  - CBM → 显示"总体积（CBM）"必填输入框
  - PIECE → 显示"件数"必填输入框
  - LB → 显示"总重量（磅）"输入框，旁边实时换算为 KG 提示
- 报价详情费用行显示单位，如 `基础运费 ¥400（0.5 CBM × ¥800/CBM）`

### 5.4 验收标准（GWT）

```
场景1：CBM 计费
Given rate_card_line.uom = 'CBM', unit_rate = 800
When  quote 请求 totalVolumeCbm = 0.5
Then  base_amount = 0.5 × 800 = 400

场景2：PIECE 计费
Given rate_card_line.uom = 'PIECE', unit_rate = 20
When  quote 请求 totalPieces = 5
Then  base_amount = 5 × 20 = 100

场景3：LB 计费（前端转换后传 KG）
Given rate_card_line.uom = 'KG', unit_rate = 5
When  前端将 10 LB → 4.536 KG，totalWeightKg = 4.536
Then  base_amount = 4.536 × 5 = 22.68

场景4：CBM 计费但请求中 totalVolumeCbm 为空
When  调用 quote，totalVolumeCbm = null
Then  HTTP 400，"CBM 计费渠道必须提供 totalVolumeCbm"

场景5：PIECE 计费但 totalPieces = 0
When  调用 quote，totalPieces = 0
Then  HTTP 400，"件数必须大于 0"
```

---

## 六、A7 Dim Factor 混合比例参数化

### 6.1 数据模型

```sql
ALTER TABLE rate_card_lines
    ADD COLUMN dim_split_ratio numeric(4,3) NOT NULL DEFAULT 1.0;
    -- 取值范围 [0.0, 1.0]
    -- 0.0 = 纯实重（dim weight 完全不参与）
    -- 1.0 = 纯体积重（现有默认行为）
    -- 0.5 = 实重和体积重各取一半后再与实重取大
```

`dim_factor`（用于计算体积重的除数，如 5000）保留在 `rate_cards` 层级不变。

### 6.2 计算公式

```
dim_weight  = (length_cm × width_cm × height_cm) / dim_factor
blended     = actual_weight × (1 - dim_split_ratio) + dim_weight × dim_split_ratio
chargeable  = MAX(actual_weight, blended)
```

当 `dim_split_ratio = 1.0` 时，公式退化为原有逻辑：`chargeable = MAX(actual, dim)`。

### 6.3 前端交互

- 价卡行编辑：新增"体积重混合比例"数字输入（0.000–1.000），Tooltip 说明三档典型值（0/0.5/1）
- 报价详情：当 dim_split_ratio ≠ 1.0 时，展示混合计算过程：  
  `体积重 2.0 KG × 50% + 实重 1.5 KG × 50% = 混合 1.75 KG → 与实重 1.5 取大 = 1.75 KG`

### 6.4 验收标准（GWT）

```
场景1：纯体积重（默认）dim_split_ratio = 1.0
Given dim_factor = 5000，箱子 10×10×10 cm，actual = 0.1 KG
  dim_weight = 1000/5000 = 0.2 KG
When  调用 quote
Then  blended = 0.1×0 + 0.2×1 = 0.2；chargeable = MAX(0.1, 0.2) = 0.2 KG

场景2：纯实重 dim_split_ratio = 0.0
Given 同上尺寸和重量
When  调用 quote
Then  blended = 0.1×1 + 0.2×0 = 0.1；chargeable = MAX(0.1, 0.1) = 0.1 KG

场景3：混合比例 dim_split_ratio = 0.5
Given actual = 2 KG，dim_weight = 4 KG
When  调用 quote
Then  blended = 2×0.5 + 4×0.5 = 3；chargeable = MAX(2, 3) = 3 KG

场景4：混合后仍以实重胜出
Given actual = 5 KG，dim_weight = 2 KG，dim_split_ratio = 0.5
When  调用 quote
Then  blended = 5×0.5 + 2×0.5 = 3.5；chargeable = MAX(5, 3.5) = 5 KG（实重胜出）

场景5：dim_split_ratio 超出范围
When  创建/更新价卡行，dim_split_ratio = 1.5
Then  HTTP 400，"dim_split_ratio 必须在 0.0 到 1.0 之间"
```

---

## 七、非功能需求

| 类别 | 要求 |
|------|------|
| **性能** | `RateEngine.quote()` 端到端 < 500ms（含品名规则扫描，规则数 < 500 条） |
| **安全** | 所有新表均启用 RLS（见各表建表 SQL）；接口须 Bearer JWT 鉴权 |
| **数据完整性** | `rate_card_lines.uom` 加 CHECK 约束：`IN ('KG','LB','CBM','PIECE')` |
| **向后兼容** | 新字段均有 DEFAULT，存量价卡行不需迁移数据即可正常运行 |
| **静态检查** | 新代码须通过 `./mvnw verify`（Checkstyle + PMD P3C + SpotBugs） |

---

## 八、数据库迁移文件概览

`db/migrations/019_billing_engine_v2.sql` 执行顺序：

```sql
-- 1. 新建品名规则表
CREATE TABLE product_keyword_rules (...);
-- RLS 策略

-- 2. rate_card_lines 扩展字段
ALTER TABLE rate_card_lines
    ADD COLUMN surcharge_group       text,
    ADD COLUMN combination_strategy  text NOT NULL DEFAULT 'STACK',
    ADD COLUMN min_weight_per_box    numeric(8,3),
    ADD COLUMN min_amount_per_box    numeric(12,2),
    ADD COLUMN uom                   text NOT NULL DEFAULT 'KG',
    ADD COLUMN dim_split_ratio       numeric(4,3) NOT NULL DEFAULT 1.0;

ALTER TABLE rate_card_lines
    ADD CONSTRAINT ck_uom
        CHECK (uom IN ('KG','LB','CBM','PIECE')),
    ADD CONSTRAINT ck_combination_strategy
        CHECK (combination_strategy IN ('STACK','MAX')),
    ADD CONSTRAINT ck_dim_split_ratio
        CHECK (dim_split_ratio >= 0.0 AND dim_split_ratio <= 1.0);

-- 3. remote_zones 扩展字段
ALTER TABLE remote_zones
    ADD COLUMN zone_type text NOT NULL DEFAULT 'REMOTE';

ALTER TABLE remote_zones
    ADD CONSTRAINT ck_zone_type
        CHECK (zone_type IN ('REMOTE','SUPER_REMOTE','EMBARGO'));
```

---

## 九、排期与分工建议

| 天数 | 任务 |
|------|------|
| Day 1 | 写迁移 SQL（019）；品名规则 CRUD 接口（A1） |
| Day 2 | RateEngine 集成品名匹配逻辑（A1）；按箱计费逻辑（A4） |
| Day 3 | 偏远三档（A5）；EMBARGO 下单拦截 |
| Day 4 | 多计费单位路由（A6）；Dim Factor 参数化（A7） |
| Day 5 | 叠加/取大策略（A2/A3）；全链路联调 |
| Day 6–7 | 前端：价卡行表单新增字段；报价明细展示 |
| Day 8 | 前端：偏远区域管理扩展；EMBARGO 提示 |
| Day 9–10 | 联调测试；按 GWT 逐条验收；`./mvnw verify` |
