# XQT-SaaS 全 Tab 功能参考手册

_共 144 个有效 tab, 9 大菜单组_  _生成时间: 2026-06-27_

⭐ = R-X 新需求 或 新加功能

---

## 🚚 委托运输 / 制单中心  (13 个)

| Tab key | 中文名 | API | 业务说明 |
|---|---|---|---|
| `orders` | **快件订单** | `/api/acc/orders` | 快件订单主列表 — 所有运输委托单 |
| `orders-draft` | **未提交** | `/api/acc/orders` | 未提交订单 — 草稿状态 |
| `orders-history` | **历史制单** | `/api/acc/orders` | 历史制单 — 已完成生命周期 |
| `orders-cancelled` | **取消订单** | `/api/acc/orders` | 取消订单 — 可彻底删除 |
| `orders-void` | **作废订单** | `/api/acc/orders` | 作废订单 — 可批量恢复/审核 |
| `quick-orders` | **快速下单** | `/api/acc/quick-orders` | 快速下单 — 简化版下单 |
| `orders-queue` | **制单队列** | `/api/acc/orders` | 制单队列 — 待批量制单 |
| `orders-batch-print` | **批量打印** | `/api/acc/orders` | 批量打印面单/发票 |
| `orders-update-tracking` | **更新转单号** | `/api/acc/orders` | 批量改承运商单号 |
| `orders-update-weight` | **更新计费重** | `/api/acc/orders` | 批量改计费重 |
| `orders-change-customer` | **变更客户** | `/api/acc/orders` | 批量改客户 |
| `orders-batch-charge` | **批量计费** | `/api/acc/orders` | 批量重算费用 |
| `orders-batch-track` | **追踪快递** | `/api/acc/orders` | 批量追踪 tracking |

## 📦 配载中心  (20 个)

| Tab key | 中文名 | API | 业务说明 |
|---|---|---|---|
| `shipments` | **出货管理** | `/api/acc/shipments` | 出货单列表 |
| `shipments-query` | **快件查询** | `/api/acc/shipments` | _待补充_ |
| `shipments-today` | **今日快件** | `/api/acc/shipments` | 今日出货 |
| `stowages` | **配载管理** | `/api/acc/stowages` | 配载管理 — 集装箱方案列表 |
| `stowage-plans` | **3D 配载方案** | `/api/acc/stowage/plan` | 3D 配载方案 — Bin Packing 立体视图 ⭐ |
| `stowages-exception` | **异常提单** | `/api/acc/stowages` | 异常提单 |
| `packages` | **装箱单** | `/api/acc/packages` | 装箱单 — carton 详情 |
| `transits` | **转运管理** | `/api/acc/transits` | 中转管理 |
| `shipments-channel-stats` | **渠道统计** | `/api/acc/shipments/channel-stats` | _待补充_ |
| `shipments-pickup-today` | **今日提取** | `/api/acc/shipments` | 今日揽收 |
| `shipments-pickup-week` | **本周提取** | `/api/acc/shipments` | 本周揽收 |
| `shipments-intransit` | **在途订单** | `/api/acc/shipments` | 在途订单 |
| `shipments-exception` | **异常订单** | `/api/acc/shipments` | 异常订单 |
| `shipments-delivered-today` | **今日签收** | `/api/acc/shipments` | 今日签收 |
| `ports` | **港口管理** | `/api/acc/ports` | _待补充_ |
| `warehouses` | **仓库管理** | `/api/acc/warehouses` | 仓库管理 |
| `stowage-categories` | **配载分类** | `/api/acc/stowage-categories` | _待补充_ |
| `stowage-steps` | **配载步骤** | `/api/acc/stowage-steps` | _待补充_ |
| `forecasts` | **预报包裹** | `/api/acc/forecasts` | 预报对账 |
| `tracks` | **轨迹项目** | `/api/acc/tracks` | 物流跟踪流水 |

## 🛎️  客服中心  (21 个)

| Tab key | 中文名 | API | 业务说明 |
|---|---|---|---|
| `dispatches` | **上门揽收** | `/api/acc/dispatches` | 上门揽收 |
| `inbound-parcels` | **入仓预报** | `/api/acc/inbound-parcels` | 入仓预报 |
| `dws-scans` | **DWS 扫描流水** | `/api/acc/dws-scans` | DWS 扫描流水 |
| `dws-discrepancies` | **重量差异** | `/api/acc/dws-discrepancies` | 重量差异 |
| `collects` | **总单/留仓** | `/api/acc/collects` | 总单/留仓 — 母单/留仓待提取 |
| `returns` | **退件管理** | `/api/acc/returns` | 退件管理 |
| `detains` | **扣件管理** | `/api/acc/detains` | _待补充_ |
| `asks` | **问题件** | `/api/acc/asks` | 问题件总览 |
| `asks-customer` | **客户查询件** | `/api/acc/asks` | 客户问题件 |
| `asks-supplier` | **服务商反馈** | `/api/acc/asks` | 供应商问题件 |
| `asks-processing` | **处理中问题** | `/api/acc/asks` | 处理中 |
| `asks-pending` | **未处理问题** | `/api/acc/asks` | 待处理 |
| `asks-new` | **发起新问题** | `/api/acc/asks` | 新问题件 |
| `asks-history` | **历史问题件** | `/api/acc/asks` | 历史问题件 |
| `ai-cs-sessions` | **AI 会话历史** | `/api/acc/ai-cs/sessions` | AI 客服会话 |
| `ai-cs-kb` | **AI 知识库** | `/api/acc/ai-cs/kb` | AI 客服知识库 |
| `reparations` | **赔偿管理** | `/api/acc/reparations` | 赔偿管理总览 |
| `reparations-apply` | **申请赔偿** | `/api/acc/reparations` | 申请赔偿 |
| `reparations-pending` | **待审赔偿** | `/api/acc/reparations` | 待审赔偿 |
| `reparations-history` | **历史赔偿** | `/api/acc/reparations` | 历史赔偿 |
| `received-sms` | **收款短信** | `/api/acc/received-sms` | 收款短信 |

## 💼 销售中心  (21 个)

| Tab key | 中文名 | API | 业务说明 |
|---|---|---|---|
| `customers` | **客户管理** | `/api/acc/customers` | 客户主档 |
| `customer-groups` | **客户分组** | `/api/acc/customer-groups` | 客户分组 |
| `customer-adjusts` | **客户调账** | `/api/acc/customer-adjusts` | 客户调账 |
| `customer-receivables` | **应收款项目** | `/api/acc/customer-receivables` | 应收款项目 — 按客户/币种汇总 |
| `bills` | **客户账单** | `/api/acc/bills` | 客户账单 |
| `customer-rebates` | **客户返利** | `/api/acc/customer-rebates` | 客户返利 |
| `customer-fines` | **客户罚款** | `/api/acc/customer-fines` | 客户罚款 |
| `receiveds` | **客户收款** | `/api/acc/receiveds` | 客户收款 |
| `customer-refunds` | **客户退款** | `/api/acc/customer-refunds` | 客户退款 |
| `api-credentials` | **API 凭证** | `/api/acc/api-credentials` | API 凭证 — 客户密钥 |
| `customer-logins` | **客户登陆号** | `/api/acc/customer-logins` | 客户登录审计 |
| `customer-rate-cards` | **客户专价绑定** | `/api/acc/customer-rate-cards` | 客户费率绑定 |
| `customer-rate-strategies` | **客户价格策略** | `/api/acc/customer-rate-strategies` | 客户价格策略 (R-12) — 佣金率/折扣 ⭐ |
| `charge-items` | **费用类目** | `/api/acc/charge-items` | 费用类目 (R-13) — Ground/Fuel/Surcharge 字典 |
| `cost-pre-estimates` | **预估报价池** | `/api/acc/cost-pre-estimates` | 预报价池 (R-6) |
| `potentials` | **潜在客户** | `/api/acc/potentials` | 潜在客户 |
| `suppliers` | **物流商** | `/api/acc/suppliers` | 供应商主档 |
| `channels` | **渠道管理** | `/api/acc/channels` | 渠道类型 — 31 渠道 ⭐ |
| `channel-accounts` | **渠道账号** | `/api/acc/channel-accounts` | 渠道账号 — UPS/FedEx 账号 |
| `products` | **价格表** | `/api/acc/products` | 销售产品 |
| `product-items` | **品名管理** | `/api/acc/product-items` | _待补充_ |

## 📊 核算中心  (31 个)

| Tab key | 中文名 | API | 业务说明 |
|---|---|---|---|
| `charges` | **应收运费** | `/api/acc/charges` | 应收运费 — AR side |
| `charges-history` | **历史费用** | `/api/acc/charges` | 历史应收 |
| `charges-pending` | **待核费用** | `/api/acc/charges` | 待审应收 |
| `charges-pending-return` | **待核退件** | `/api/acc/charges` | 退件待审应收 |
| `charges-pending-reparation` | **待核赔偿** | `/api/acc/charges` | 赔偿待审应收 |
| `charges-import` | **导入费用** | `/api/acc/charges` | 导入费用 CSV |
| `costs` | **应付成本** | `/api/acc/costs` | 应付成本 — AP side |
| `costs-pending` | **待核成本** | `/api/acc/costs` | 待审成本 |
| `costs-estimate` | **预估成本** | `/api/acc/costs` | 预估成本 (R-6 池) |
| `costs-recent` | **近期成本** | `/api/acc/costs` | 最近成本 |
| `costs-history` | **历史成本** | `/api/acc/costs` | 历史成本 |
| `costs-import` | **导入成本** | `/api/acc/costs` | 导入成本 CSV |
| `costs-transit` | **转运成本** | `/api/acc/costs` | 中转成本 |
| `costs-zhonggang` | **中港成本** | `/api/acc/costs` | 中港运费 |
| `costs-air` | **航空成本** | `/api/acc/costs` | 空运成本 |
| `swb-cost-pending` | **待核成本 (UPS 单)** | `/api/acc/settlement-workbench/cost-pending` | 待核成本 |
| `swb-pending-pay` | **待付供应商** | `/api/acc/settlement-workbench/pending-pay` | 待付供应商 |
| `swb-paid` | **已付成本** | `/api/acc/settlement-workbench/paid` | 已付成本 |
| `swb-profit` | **利润分析** | `/api/acc/settlement-workbench/profit-list` | 利润分析 (结算台) |
| `swb-aging` | **账龄分析** | `/api/acc/settlement-workbench/aging` | 账龄分析 |
| `swb-monthly` | **月度财报** | `/api/acc/settlement-workbench/monthly-report` | 月度财报 |
| `gl-income` | **利润表 (GL)** | `/api/acc/gl/reports/income-statement` | 利润表 (GL) |
| `gl-balance` | **资产负债表 (GL)** | `/api/acc/gl/reports/balance-sheet` | 资产负债表 (GL) |
| `gl-cash` | **现金流量表 (GL)** | `/api/acc/gl/reports/cash-flow` | 现金流量表 (GL) |
| `gl-trial` | **试算平衡 (GL)** | `/api/acc/gl/reports/trial-balance` | 试算平衡 (GL) |
| `approval-pending` | **审批待办** | `/api/acc/../admin/approval/pending` | _待补充_ |
| `swb-commissions` | **业绩提成** | `/api/acc/settlement-workbench/commissions` | 业绩提成 |
| `bills` | **客户账单** | `/api/acc/bills` | 客户账单 |
| `profits` | **利润查询** | `/api/acc/profits` | 利润查询 — 按 shipment |
| `commissions` | **员工提成** | `/api/acc/commissions` | 业绩提成 |
| `commission-rules` | **提成规则** | `/api/acc/commission-rules` | 提成规则 |

## 💰 财务中心  (26 个)

| Tab key | 中文名 | API | 业务说明 |
|---|---|---|---|
| `banks` | **银行账户** | `/api/acc/banks` | 账户管理 — 公司银行账号 |
| `transfers` | **转账记录** | `/api/acc/transfers` | 资金转账 |
| `account-transactions` | **往来账户** | `/api/acc/account-transactions` | _待补充_ |
| `currencies` | **货币汇率** | `/api/acc/currencies` | _待补充_ |
| `customer-receivables` | **应收款项目** | `/api/acc/customer-receivables` | 应收款项目 — 按客户/币种汇总 |
| `ar-vs-received` | **应收实收对比** | `/api/acc/reconciliation/ar-vs-received` | 应收实收对比 |
| `charges` | **应收运费** | `/api/acc/charges` | 应收运费 — AR side |
| `receiveds` | **客户收款** | `/api/acc/receiveds` | 客户收款 |
| `receiveds-pending` | **待审收款** | `/api/acc/receiveds` | _待补充_ |
| `customer-refunds` | **客户退款** | `/api/acc/customer-refunds` | 客户退款 |
| `customer-refunds-pending` | **待审退款(应收)** | `/api/acc/customer-refunds` | 待审客户退款 |
| `bills` | **客户账单** | `/api/acc/bills` | 客户账单 |
| `fwb-prepay` | **预扣明细** | `/api/acc/finance-workbench/prepay-details` | 预扣明细 |
| `fwb-pending` | **待财务审核** | `/api/acc/finance-workbench/pending-audit` | 待财务审核 |
| `fwb-invoiced` | **已出账** | `/api/acc/finance-workbench/invoiced` | 已出账 |
| `fwb-needs-verify` | **待二审账单** | `/api/acc/finance-workbench/needs-verify` | 待二审账单 |
| `carrier-invoice-recon` | **渠道账单对比** | `/api/acc/reconciliation/carrier-invoice` | 渠道账单对比 (R-4) — xlsx 上传 ⭐ |
| `costs` | **应付成本** | `/api/acc/costs` | 应付成本 — AP side |
| `payments` | **供应商付款** | `/api/acc/payments` | 供应商付款 |
| `payments-pending` | **待审付款** | `/api/acc/payments` | _待补充_ |
| `supplier-refunds` | **物流商退款** | `/api/acc/supplier-refunds` | 供应商退款 |
| `supplier-refunds-pending` | **待审退款(应付)** | `/api/acc/supplier-refunds` | 待审供应商退款 |
| `profits` | **利润查询** | `/api/acc/profits` | 利润查询 — 按 shipment |
| `profits-unfinished` | **未完结快件** | `/api/acc/profits` | 未完结快件 |
| `profits-overdue` | **逾期未结** | `/api/acc/profits` | 逾期未结 |
| `profits-lowprofit` | **低利快件** | `/api/acc/profits` | 低利快件 |

## 📋 基础信息  (19 个)

| Tab key | 中文名 | API | 业务说明 |
|---|---|---|---|
| `acc-branches` | **分店管理** | `/api/acc/branches` | 分公司管理 |
| `customer-groups` | **客户分组** | `/api/acc/customer-groups` | 客户分组 |
| `districts` | **行政区域** | `/api/acc/districts` | 地区管理 |
| `postcodes` | **邮编库** | `/api/acc/postcodes` | 邮编管理 |
| `remotes` | **偏远邮编** | `/api/acc/remotes` | 偏远邮编 |
| `fee-types` | **附加费类型** | `/api/acc/fee-types` | 杂费类型 |
| `fuels` | **燃油费率** | `/api/acc/fuels` | 燃油费用 |
| `channel-accounts` | **渠道账号** | `/api/acc/channel-accounts` | 渠道账号 — UPS/FedEx 账号 |
| `products` | **价格表** | `/api/acc/products` | 销售产品 |
| `rate-lookup` | **价目表查询** | `/api/acc/rate-lookup/channels` | 价目表查询 — 31 渠道 4711 阶梯 ⭐ |
| `zones` | **价格分区** | `/api/acc/zones` | 价格分区 |
| `channels` | **渠道管理** | `/api/acc/channels` | 渠道类型 — 31 渠道 ⭐ |
| `suppliers` | **物流商** | `/api/acc/suppliers` | 供应商主档 |
| `supplier-adjusts` | **物流商调账** | `/api/acc/supplier-adjusts` | 供应商调账 |
| `bills` | **客户账单** | `/api/acc/bills` | 客户账单 |
| `supplier-rebates` | **物流商返利** | `/api/acc/supplier-rebates` | 供应商返利 |
| `supplier-fines` | **物流商罚款** | `/api/acc/supplier-fines` | 供应商罚款 |
| `payments` | **供应商付款** | `/api/acc/payments` | 供应商付款 |
| `supplier-refunds` | **物流商退款** | `/api/acc/supplier-refunds` | 供应商退款 |

## 👥 人事组织  (1 个)

| Tab key | 中文名 | API | 业务说明 |
|---|---|---|---|
| `acc-branches` | **分店管理** | `/api/acc/branches` | 分公司管理 |

## ⚙️  数据管理  (10 个)

| Tab key | 中文名 | API | 业务说明 |
|---|---|---|---|
| `countries` | **国家地区** | `/api/acc/countries` | 国家字典 |
| `districts` | **行政区域** | `/api/acc/districts` | 地区管理 |
| `postcodes` | **邮编库** | `/api/acc/postcodes` | 邮编管理 |
| `remotes` | **偏远邮编** | `/api/acc/remotes` | 偏远邮编 |
| `fuels` | **燃油费率** | `/api/acc/fuels` | 燃油费用 |
| `hscodes` | **HS编码** | `/api/acc/hscodes` | HS 编码字典 |
| `zones` | **价格分区** | `/api/acc/zones` | 价格分区 |
| `bank-names` | **银行名称** | `/api/acc/bank-names` | 银行字典 |
| `logistics-interfaces` | **物流接口** | `/api/acc/logistics-interfaces` | 物流接口配置 |
| `importer-templates` | **进口商模板** | `/api/acc/importer-templates` | 进口商预设模板 |

---

## 数据快照 (2026-06-27)

- 全部 tab: **144** 个
- 菜单组里出现的 tab: **162** 个
- 已删: 24 个 (HR/工资/资产 + 死链)
- 真有数据 tab: **84** 个 (扫所有 GET endpoint)
- 业务正常空 tab: **40+ 个** (返件/赔偿/退款等待真业务激活)

### 财务全链路对齐 ✅

- `charges` AR USD 391.00 = `profit_snapshots` USD 391.00
- `charges` AR CNY 1237.90 = `profit_snapshots` CNY 1237.90
- `invoice.total_amount` = SUM(`invoice_lines.amount`)
- 0 重复 payments, 0 孤儿 charges

### RateEngine 渠道 (31 个)

- 9 个老 ACC (UPS-GROUND-US, FedEx-A/B 等)
- 22 个 XQT 新启天 (FedEx/UPS 末端 + 8 海派 + 10 卡派 + 美转加)
- 价格阶梯: 4711 行
- 3 种 zone 解析: UPS Zone 邮编表 / 海派区域 USE/USM/USW / 卡派仓库代码

### 计费方式

`chargeable = max(实重 weightKg, 体积重 volumeCbm × 1_000_000 / dimFactor)`

- 末端派送 dimFactor = 5000
- 海派/卡派 dimFactor = 6000

