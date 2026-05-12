# ACC Rates（运费试算）重构落地

生成日期：2026-05-12
范围：把 `acc/api/APIClass.php?act=Price`、`acc/config/Freight.php::getFee`、`acc/inc/Price.php` 的运费试算能力迁移到 xqt-saas 后端。是 ACC 全量重构的第二个模板模块。

## 1. 模块边界

| ACC 旧入口 | 新接口 | 鉴权 |
| --- | --- | --- |
| `api/APIClass.php?act=Price` | `POST /api/customer-api/rates/quote` | customer-api 签名 |
| 后台费用界面 / 调用计费规则 | `POST /api/document/rates/quote` | Bearer + `flow.document.read` |

后端共用同一个 `com.xqt.saas.rates.RateEngine`，两个 controller 仅做协议层适配。

## 2. 包结构

```
com.xqt.saas.rates
├── RateEngine              核心计费引擎
├── RateRepository          channels / rate_cards / rate_card_lines / fuel_surcharge_rates / remote_zones 只读
├── RateQuoteRequest        请求体（channelCode, country, postal, weight, volume, currency, chargeDate）
├── RateQuoteResponse       Quote + BreakdownLine
└── DocumentRateController  /api/document/rates/quote
```

`com.xqt.saas.customerapi.CustomerApiController` 新增 `POST /api/customer-api/rates/quote` 路由，复用同一引擎。

## 3. MVP 算法

```
1. 查 channels.code         → 拿 channel_id / dim_factor / active
2. 体积重 = volume_cbm × 1_000_000 / dim_factor
3. 可计重 = max(actualWeight, 体积重)             // 精度 3 位
4. 查 rate_cards (side=AR, currency=req.currency, status=ACTIVE,
    effective_from..to 包住 chargeDate)
5. 查 remote_zones (channel, country, postal regex 匹配) → remoteLevel
6. zone_code = "ZONE_A"                          // MVP 默认值
7. 查 rate_card_lines (rate_card_id, zone_code,
    weight_from <= 可计重 < weight_to)            // 取一档
8. freight = unit_price × 可计重；min_amount 兜底
9. fuel = freight × fuel_surcharge_rates.rate (本月份)
10. surcharge = freight × remote_rate (NONE=0, REMOTE=15%, SUPER_REMOTE=25%, EMBARGO=报错)
11. total = freight + fuel + surcharge            // 金额 2 位四舍五入
```

刻意省略的 ACC 分支（待业务确认后逐步补）：

- 成本价 / 客户价 / 客户组价（`pd.Type != 0`、`Product_Customer`、`Product_Group`）
- 渠道账号限量 `Channel_Account.{MaxCount,MaxPiece,MaxWeight}`
- 电池 / 仿牌可装载性过滤（旧字段仍可在 PreOrder.metadata.acc_compat 中保留）
- 邮编优先级匹配（旧逻辑用 `,Country(...)` 字符串）
- 佣金 `Customer_Brokerage.Amount`
- 货代分润（`Brokerage`、`Dividend`）

## 4. 字段对照

| ACC 旧字段 / 表 | 新字段 / 表 | 备注 |
| --- | --- | --- |
| `Product` 的 `Channel` | `channels.code` | 新版以 channel_code 为主入参；旧 product 概念由 services 表承担 |
| `Country.Code2` | `RateQuoteRequest.countryCode` | ISO2 |
| `Postcode` | `RateQuoteRequest.postalCode` | |
| `Item[].Weight` | `RateQuoteRequest.weightKg` | MVP 多件合并为单值 |
| `Item[].Extent/Width/Height` | `RateQuoteRequest.volumeCbm` | 客户端自行算 CBM |
| `Fuel.Fuel`（按 FuelType） | `fuel_surcharge_rates.rate`（按 channel + year_month） | |
| `Logistics_Remote` + `Remote_Level` | `remote_zones.level` | 用 PG enum remote_level |
| 输出 `Name` | `Quote.channelName` | |
| 输出 `Weight` | `Quote.chargeableWeightKg` | |
| 输出 `Freight` | `Quote.freight` | |
| 输出 `Fuel` | `Quote.fuelAmount` | |
| 输出 `Surcharge` | `Quote.surchargeAmount` | |
| 输出 `SubTotal` | `Quote.totalAmount` | |
| — | `Quote.breakdown` | 新增明细，便于审计和前端展示 |

## 5. 演示数据（`db/migrations/017_acc_rates_seed.sql`）

| 渠道 | EU-AIR-UPS（已存在） |
| --- | --- |
| 价表 | `ACC-DEMO-2026Q2`, side=AR, currency=CNY, effective 2026-04-01 起 |
| 价表行 | 0–1 kg @ 30 CNY/kg (min 30) / 1–21 kg @ 25 / 21–70 kg @ 22 / 70+ kg @ 20，zone=ZONE_A |
| 燃油 | 2026-04 / 2026-05 = 18.5%，2026-06 = 19% |
| 偏远 | GB 邮编 `^HS[0-9]` 为 REMOTE 等级 |

## 6. 对照样本（已锁在测试里）

`RateEngineTests` 锁定 7 个场景：

| 场景 | 输入 | 期望 freight | fuel | remote | total |
| --- | --- | --- | --- | --- | --- |
| 5kg US，无偏远 | 25×5 | 125.00 | 23.13 (×0.185) | 0 | 148.13 |
| 5kg GB HS1 偏远 | 25×5，fuel=0 | 125.00 | 0 | 18.75 (×0.15) | 143.75 |
| 体积重压实重 | 0.06 CBM ⇒ 10kg ⇒ 25×10 | 250.00 | 0 | 0 | 250.00 |
| 最小计费 | 0.5kg ⇒ 30×0.5=15，min=30 | 30.00 | 0 | 0 | 30.00 |
| EMBARGO 国家 | — | ApiException | | | |
| 未知渠道 | — | ApiException | | | |
| 缺 weight | — | ApiException | | | |

## 7. 已知缺口

- `services` 与 `channels` 在新模型里是 1..N 关系，目前 customer-api 试算只接 channelCode；ACC 用 ProductCode 间接定位 Channel，需要后续补一层 service → channel 路由
- 多段递减（首重/续重）尚未建模；当前所有 tier 都是线性 `unit_price × 重量`
- 偏远等级目前 hardcoded 百分比，正式生产应改为读 `charge_rules.condition_json`
- 客户专属价 / 合同价没接，所有客户走同一张 AR 价表

## 8. 后续模块

按 `docs/acc-customer-api-migration.md` §9 的顺序，继续做：
1. `labels` — Label / Relabel / 文件下载
2. `tracking` — Track 多源轨迹
3. `shipments` — Submit + 渠道取号 + 状态机
4. `documentcharges` — Express_Charge 预扣 + 账单
