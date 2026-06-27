# XQT-SaaS 全 Tab 功能详细说明手册

_共 144 个 tab, 按业务流分 9 大菜单组_  
_生成: 2026-06-27_  
_对应系统版本: feat/acc-full-migration-2026-05-26_

---

# 🚚 委托运输 / 制单中心

业务定位:**接单 → 制单 → 出货前**的所有操作。这是货代日常最高频的菜单, 包括订单录入、查询、批量操作、状态流转。

## 1. 快件订单 (`orders`)

**用途**: 委托运输的主台账, 所有"客户来下单 → 我们要发运"的单据都在这里。是制单中心的入口页, 默认显示所有状态。

**核心字段**:
- 客户单号 (customerOrderNo) — 客户自己的内部单号, 不能跟其他客户单号重复 (R-1 同单去重)
- 服务商单号 (trackNo) — 我们提交给承运商后拿到的 tracking
- 客户 / 销售产品 / 目的地 / 件数 / 计费重 / 运费 / 成本 / 分公司
- 添加时间, 审核状态

**可用操作 (顶部工具栏)**:
- **+ 新增** — 弹下单对话框, 必填客户/渠道/收件人/重量
- **A4 所有文档** / **打印标签** / **打印发票** / **打印电池信** / **简易标签** — 选中订单后批量出 PDF
- **合并制单** — 同客户多单合并成 1 个 master shipment
- **批量提交** — 选中订单 batch POST 给承运商 (走 KARRIO / UPS / FedEx gateway)
- **批量查询** — 轮询承运商查 tracking 状态
- **批量作废** — 标 VOID, 不真删
- **导出结果 / 导出选中 (N)** — CSV 中文表头
- **📥 xlsx 批量导单 (R-2)** ⭐ — 弹窗选客户 + 拖拽 xlsx, 后端走 EasyExcel 解析, 客户编码/渠道/收件人 24 列中文表头, 失败按行返错
- **只看缺成本** — 筛选 cost_charge IS NULL 的单
- **批量审核 (N)** — 选中后改 audit_status = AUDITED

**业务规则**:
- 同客户 + 同 customerOrderNo → 第二次会弹"已存在, 是否合并/覆盖" (R-1)
- 客户必须有有效信用额度才能下单
- 草稿订单 (DRAFT) 不能加成本 (R-1 add-cost 业务规则)

**关联 tab**: 未提交 / 历史制单 / 取消订单 / 作废订单 (子页过滤)。

---

## 2. 未提交 (`orders-draft` filter=DRAFT)

**用途**: 已录入但尚未提交承运商的草稿订单。下完单还在做最后审核/补充申报信息时停留在这。

**典型业务**: 制单员下完客户单, 主管检查申报价值/HS 编码后再批量提交。

**操作**: 跟"快件订单"一样, 但额外有「批量提交」高频按钮 (一次性把所有草稿推给承运商)。

---

## 3. 历史制单 (`orders-history` filter=HISTORY)

**用途**: 已签收/已结案的订单查询, 不可再编辑。

**典型业务**: 月末报表, 客户问"3 月份我发了多少单", 在这里按月筛选导出。

---

## 4. 取消订单 (`orders-cancelled` filter=CANCELLED)

**用途**: 客户取消但还没作废的单。可一键"彻底删除"。

**操作**: 只有 1 个按钮 `彻底删除` (红色), 需二次确认。删除会清干净 charges + shipment + 子表。

---

## 5. 作废订单 (`orders-void` filter=VOID_AUDIT)

**用途**: 主管标作废后等审核的单。可批量恢复或彻底删除。

**操作 (5 按钮)**:
- 彻底删除
- 待核订单 — 筛选 audit_status=PENDING
- 批量审核 — 同意作废
- 批量恢复 — 撤销作废, 回 DRAFT
- 导出结果

---

## 6. 总单 / 留仓 (`collects`)

**用途**: 母单 + 留仓待提取场景。一个 master 单号下挂多个子单 (FBA 大批次出货常见)。

**典型业务**: 客户一次发 100 个 carton 同 master 单号, 我们分批提仓再分次发运。

---

## 7. 快速下单 (`quick-orders`)

**用途**: 简化的下单流程 — 只填客户/渠道/重量/邮编, 跳过详细申报。适合内部同事代客户快速建单。

---

## 8. 制单队列 (`orders-queue` filter=QUEUE)

**用途**: 等待批量处理的订单池。客户用 API 大量上单时会先进队列, 我们定时批量提交承运商。

---

## 9. 批量打印 (`orders-batch-print`)

**用途**: 一次性打印多单的面单/发票/电池信/交接清单。

**操作**: 选中订单 + 点 `批量打印面单 (N)` → 后端返回 PDF 清单, 前端开新窗口打印预览。

---

## 10. 更新转单号 (`orders-update-tracking`)

**用途**: 当承运商真单号变了 (例如 UPS 重新生成 tracking), 批量修改。

**操作**: 选中订单 + 点 `批量改转单号` → 弹窗输入新单号映射 → POST 批量更新。

---

## 11. 更新计费重 (`orders-update-weight`)

**用途**: 承运商实际过称后给出真实计费重, 批量修改 chargeable weight。重要因为这会**直接影响应收/应付金额**。

**业务流**:
1. 承运商发 EDI / xlsx 含真单号 + 实际计费重
2. 选中所有相关订单
3. 点 `批量改计费重` → 弹窗选 xlsx / 手输
4. 系统自动重算 charges, 并写 R-9 RESTATE charge (补收差值)

---

## 12. 变更客户 (`orders-change-customer`)

**用途**: 误把订单挂到错客户头上, 批量改归属。

**操作**: 选中订单 + 点 `变更选中 (N) 到新客户` → 选目标客户 → POST 批量改 customer_id。

---

## 13. 批量计费 (`orders-batch-charge`)

**用途**: 批量重新调用 RateEngine 计费。当价目表更新 / 客户佣金率变了, 用这个一次性重算应收。

**操作**: 选中订单 + 点 `重算选中 (N) 费用`。

---

## 14. 追踪快递 (`orders-batch-track`)

**用途**: 批量调用承运商 tracking API。当客户问"我的单到哪了", 一键全部更新。

---

# 📦 配载中心

业务定位:**出货前的集装箱配载 + 已出货跟踪**。海运/卡运需要把多个 carton 装进 40HQ/20GP 集装箱, 这里管堆叠方案 + 跟踪。

## 15. 出货单 (`shipments`)

**用途**: 所有已生成 shipment (出货单) 的列表。订单提交承运商后会生成 1 个 shipment, 1 个 shipment 可能含多个 carton。

**核心字段**: shipment_no, customer_ref, destination_country, 总重, 总件数, 状态, 揽收时间, 签收时间。

**典型业务**: 业务员查"昨天发了哪些出货单", 客户问"我的 shipment 在哪", 都在这查。

---

## 16. 今日出货 (`shipments-today` filter=TODAY)

**用途**: 当日已发运的 shipment, 用于日报。

---

## 17. 今日揽收 (`shipments-pickup-today` filter=PICKUP_TODAY)

**用途**: 当日由承运商上门取货的 shipment。

---

## 18. 本周揽收 (`shipments-pickup-week` filter=PICKUP_WEEK)

**用途**: 本周累计揽收量, 周报用。

---

## 19. 在途订单 (`shipments-intransit` filter=INTRANSIT)

**用途**: 正在运输路上的单。

---

## 20. 异常订单 (`shipments-exception` filter=EXCEPTION)

**用途**: 承运商报告"地址错误/无人签收/拒收/异常滞留"的单, 需要客服跟进。

---

## 21. 今日签收 (`shipments-delivered-today` filter=DELIVERED_TODAY)

**用途**: 当日 POD 签收的单。

---

## 22. 配载管理 (`stowages`)

**用途**: 集装箱配载方案的主列表。每个 stowage = 1 个 40HQ 或 20GP 的装柜方案, 含多个 carton 的堆叠。

**核心字段**: 柜号, 容量 (L×W×H cm), 已装 carton 数, 体积利用率, 重心 (x/y/z)。

---

## 23. 异常提单 (`stowages-exception` filter=EXCEPTION)

**用途**: 容量不够 / 重心偏移过大 / 货物冲突的配载方案, 需要人工调整。

---

## 24. ⭐ 3D 配载方案 (`stowage-plans`) — 立体视图

**用途**: 用 3D Bin Packing 算法自动生成最优堆叠方案, 含**真实 3D 立体视图** (three.js)。

**核心字段**: 方案号, 柜规格, 求解状态, 装入件数, 未装件数, 体积利用率%, 重心 (x/y/z cm), 4 象限重量分布。

**操作**:
- 点行 → 打开 3D 立体视图 (鼠标拖拽旋转, 滚轮缩放, 不同客户用不同颜色块标识)
- 看 carton 在柜内的真实摆放位置
- 看重心偏移警告 (4 象限重量比应接近 25%/25%/25%/25%)

**业务流**:
1. 客户发来 N 个 carton (含 L×W×H + 重量)
2. 选柜规格 (20GP / 40GP / 40HQ)
3. 系统跑 3D Bin Packing 求解 (LIFO 启用时按后入先出排列)
4. 输出方案 + 立体视图 + 重心警告
5. 主管批准后落 stowage_plans 表

---

## 25. 装箱单 (`packages`)

**用途**: carton 维度的详情列表 — 每个 carton 的尺寸/重量/SKU 清单。

---

## 26. 中转管理 (`transits`)

**用途**: 多段运输的转运记录。例如"深圳 → 美西港口 (海运) → FBA 仓库 (卡派)" 是 2 段 transit。

---

## 27. 上门揽收 (`dispatches`)

**用途**: 派车上门取货安排。下完单后, 客户需要承运商派车取件, 在这里安排时间/车型/司机。

---

# 🛎️ 客服中心 (收货)

业务定位:**出货后的客户服务**, 处理异常件、退件、赔偿、入仓预报、DWS 称重等。

## 28. 问题件总览 (`asks`)

**用途**: 所有客户/供应商提交的疑问件汇总。包括"我的货为什么没动""怎么计费这么贵"。

**核心字段**: 单号, 问题类型, 提交人, 处理状态, 当前处理人, SLA 剩余时间。

---

## 29-34. 问题件子页

- **客户问题件** (`asks-customer`) — 由客户提交的
- **供应商问题件** (`asks-supplier`) — 承运商主动反馈的
- **处理中** (`asks-processing`) — 已有客服跟进
- **待处理** (`asks-pending`) — 还没分配处理人
- **新问题件** (`asks-new`) — 24h 内新提交
- **历史问题件** (`asks-history`) — 已结案

---

## 35. 退件管理 (`returns`)

**用途**: 客户/承运商退回的快件管理。

**业务流**: 承运商发现地址错或拒收 → 退回 → 在这里登记 → 通知客户 → 客户选择"重新发运"或"销毁"。

---

## 36-39. 赔偿管理

- **赔偿管理总览** (`reparations`)
- **申请赔偿** (`reparations-apply`) — 客户提交索赔申请 (含图片/单据)
- **待审赔偿** (`reparations-pending`) — 财务审核中
- **历史赔偿** (`reparations-history`) — 已赔付完成

**业务流**: 客户报丢 → 申请赔偿 → 财务核查 → 同意 → 写 RESTATE charge (退还应收) + 写 supplier_fine (向承运商索赔)。

---

## 40. 物流跟踪 (`tracks`)

**用途**: 所有 tracking event 的原始流水。每次承运商 webhook 回调 / 我们主动 poll, 都在这里写一行。

---

## 41. 入仓预报 (`inbound-parcels`)

**用途**: 客户在货到我们仓库前预告"我要到货 X 件, 大约 Y kg"。我们提前安排入库。

---

## 42. DWS 扫描流水 (`dws-scans`)

**用途**: Dynamic Weight Sensor (动态称重扫描枪) 的逐件扫描记录。仓库收到货后用扫描枪过称, 每扫一件写一行。

---

## 43. 重量差异 (`dws-discrepancies`)

**用途**: DWS 实际称重 vs 客户预报的差异列表。差异>10% 触发预警。

**典型业务**: 客户预报 10kg 实际 15kg → 自动写 R-9 RESTATE charge (补收 5kg 差额)。

---

## 44. 入库单 (`warehouse-receipts`)

**用途**: 仓库实际收货确认单。每次入仓产生 1 个 receipt + 多个 receipt_item (carton 级)。

---

# 💼 销售中心

业务定位:**客户管理 + 销售/价格策略 + 应收账款**。CRM + AR 都在这里。

## 45. 客户主档 (`customers`)

**用途**: 所有客户的资料库。

**核心字段**: 客户编码, 中文名, 联系人, 信用额度 (credits), 结算方式 (MONTHLY/QUARTERLY), 分公司归属, 销售员。

**操作**: 新增/编辑/启用停用, 调整信用额度, 重置 API key (客户用我们 API 上单时用)。

---

## 46. 客户罚款 (`customer-fines`)

**用途**: 客户违规扣款记录 (例如客户重复退件/虚假申报)。

---

## 47. 客户返利 (`customer-rebates`)

**用途**: 季度/月度返点。

---

## 48. ⭐ 客户价格策略 (`customer-rate-strategies`) — R-12

**用途**: 客户个性化的价格策略 — **commission_rate** (佣金率/折扣) + **floor_amount** (最低收费保护)。

**核心字段**:
- customer_id, channel_id
- commission_rate (例 0.9 = 9 折 / 1.15 = 加 15%)
- floor_amount (低于这个就按这个收, 防止超低运费)
- effective_from / effective_to

**业务流**:
1. 销售跟客户谈 "UPS-GROUND-US 给你 9 折"
2. 在这里建一条 commission_rate=0.9
3. 客户下单时 RateEngine 自动: `final = max(base × 0.9, floor)`

**真验证**: SELLER-DEMO UPS-GROUND-US 9 折 → 基价 13.85 → 12.47 USD ✓ (R-12 已生效)

---

## 49. 应收款项目 (`customer-receivables`)

**用途**: 按客户 × 币种汇总的未付账款总览。

**真值 (2026-06-27)**:
- DOC-DEMO USD 225.19 / CNY 294.40
- SELLER-DEMO USD 130.37 (paid 18.77, out 111.60) / CNY 943.50
- TEST-US-001 USD 35.44

点行打开「应收款明细」modal 看该客户全部 charges 拆分。

---

## 50. 客户费率绑定 (`customer-rate-cards`)

**用途**: 给客户绑定一张完整 rate_card (整套阶梯价目表)。跟 R-12 commission_rate (整体调价) 不同维度。

---

## 51. 客户分组 (`customer-groups`)

**用途**: 批量管理 — 把客户分组 (例如"VIP 客户"/"亚马逊大卖家"), 然后给整组绑 rate_card。

---

## 52-53. 客户调账 / 退款

- **客户调账** (`customer-adjusts`) — 手动调整客户余额 (差异核销)
- **客户退款** (`customer-refunds`) + 待审 (`customer-refunds-pending`)

---

## 54-55. 提成

- **业绩提成** (`commissions`) — 销售人员个人提成结算单
- **提成规则** (`commission-rules`) — 提成计算公式 (按金额 / 按毛利 / 阶梯)

---

## 56. 客户登录审计 (`customer-logins`)

**用途**: 客户用我们 API 登录的安全日志。

---

## 57. 客户余额账户 (`customer-accounts`)

**用途**: 客户预存款余额管理。客户提前打钱进来, 下单从余额扣。

---

## 58. 收货地址簿 (`sold-tos`)

**用途**: 客户的常用收货地址簿。下次下单可以直接选地址不用重输。

---

## 59. 潜在客户 (`potentials`)

**用途**: CRM 销售线索, 还没成交的潜在客户。

---

# 📊 核算中心

业务定位:**应收 (AR) / 应付 (AP) / 成本 / 利润核算**。所有跟"钱"相关的明细在这里。

## 60. 应收运费 (`charges`) — AR side

**用途**: 所有"我们要向客户收"的费用明细。每个 carton/shipment 产生多条 charge (Ground 基础运费 + Fuel 燃油 + Surcharge 附加费 etc.)。

**核心字段**:
- amount, paid_amount, currency
- side='AR'
- status (DRAFT/ESTIMATED/LOCKED/ADJUSTED/VOID)
- **source_type (R-11) ⭐** — ESTIMATE/MANUAL/INVOICE/LIVE_QUOTE/**RESTATE**/BATCH_IMPORT (前端用 emoji+色 badge: 🤖✋📄⚡🔁📥)
- **source_charge_id (R-9) ⭐** — RESTATE 类型的指向源 charge id
- **restate_added (R-9) ⭐** — 该 charge 被补收过多少 (聚合列)
- **restate_returned (R-9) ⭐** — 该 charge 被回退过多少

**操作**: 单笔补收 (R-8 PATCH /restate) — 弹窗输入新金额 / DELTA 模式 / 备注。

---

## 61. 应付成本 (`costs`) — AP side

**用途**: 所有"我们要付给承运商"的成本明细。

---

## 62-65. charges 子页

- **历史应收** (`charges-history`) — status=AUDITED
- **待审应收** (`charges-pending`)
- **退件待审应收** (`charges-pending-return`)
- **赔偿待审应收** (`charges-pending-reparation`)
- **导入费用 CSV** (`charges-import`) — 上传承运商 CSV 批量入账 doImportActualBill

---

## 66-72. costs 子页

- **待审成本** (`costs-pending`)
- **预估成本** (`costs-estimate` R-6 池)
- **最近成本** (`costs-recent`)
- **历史成本** (`costs-history`)
- **导入成本 CSV** (`costs-import`)
- **中转成本** (`costs-transit`) — 多段运输的中转费
- **中港运费** (`costs-zhonggang`) — 中国 → 香港 段
- **空运成本** (`costs-air`)

---

## 73. ⭐ 费用类目 (`charge-items`) — R-13

**用途**: 费用分类字典。Ground/Fuel/Surcharge/Insurance/Battery 等所有费用项的定义中心。

**核心字段**: code, name, category, default_uom (KG/LB/CBM/PIECE/SHIPMENT/CARTON/PERCENT), default_side (AR/AP/SELLER_COST/SELLER_COMMISSION), default_unit_price, default_currency, sort_order, active。

**权限 (R-13)**: 只有 `finance.charge_item.write` / ROLE_ADMIN / ROLE_FINANCE_MANAGER 能新增/删除, 业务员只能看。

**真实**: 系统现有 15 个费用类目 (含 ADJUST/FUEL/GROUND_COMM/FUEL_SURCHARGE/RESIDENTIAL_SURCHARGE etc.)。

---

## 74. ⭐ 预报价池 (`cost-pre-estimates`) — R-6

**用途**: 报价前的预估成本池。客户问"如果我发这个有多少钱", 我们用 RateEngine 算出来存这里。当真实账单回来时**自动核销** (R-6 auto-reconcile)。

**业务流**:
1. 销售给客户报价 → 算的成本写预报价池
2. 客户下单 → 货物发出
3. 承运商真实账单回来 → 写 cost charge
4. 系统自动 UPDATE cost_pre_estimates SET reconciled_count++, 当核销次数 >= 预报次数 → status='EXHAUSTED'

---

## 75. ⭐ 价目表查询 (`rate-lookup`) — 新加

**用途**: 类似 ACC 老的"价格查询", 一键看 31 个渠道的所有阶梯价。

**核心字段**: 渠道代码, 渠道名称, 运输方式 (US-LAST-MILE/US-OCEAN-EXPRESS/US-OCEAN-TRUCK), 价格阶梯数, Zone 数, 最低价, 最高价。

**子页/操作**:
- GET /api/acc/rate-lookup/channels — 渠道列表
- GET /api/acc/rate-lookup/lines/{code}?zoneCode=X&warehouseCode=Y — 单渠道全阶梯
- GET /api/acc/rate-lookup/quote-simulate?channelCode=X&weightKg=Y&postalCode=Z — 不污染 rate_quotes 表的"试算"

**真数据**: 31 渠道 (9 老 ACC + 22 新启天 XQT), 共 4711 阶梯条目。

---

## 76. 利润查询 (`profits`)

**用途**: 按 shipment 算的毛利汇总 (`profit_snapshots` 表)。

**真值**: USD 14 个 shipment / 总利润 315.14, CNY 1 shipment / 利润 666.70。

---

## 77-79. profits 子页

- **未完结快件** (`profits-unfinished` filter=UNFINISHED)
- **逾期未结** (`profits-overdue` filter=OVERDUE)
- **低利快件** (`profits-lowprofit` filter=LOWPROFIT)

---

## 80-84. 供应商往来

- **供应商返利** (`supplier-rebates`)
- **供应商罚款** (`supplier-fines`) — 服务质量差扣款
- **供应商调账** (`supplier-adjusts`)
- **供应商退款** (`supplier-refunds`) + 待审 (`supplier-refunds-pending`)
- **供应商主档** (`suppliers`)
- **预报对账** (`forecasts`) — 客户预报 vs 实际差异统计

---

# 💰 财务中心

业务定位:**资金账户 + 账单 + 收付款 + 报表**。CFO 视角。

## 85. 账户管理 (`banks`)

**用途**: 公司的银行账号管理 (实际表 `financial_accounts`)。

**核心字段**: 账户名, 开户行, 账号, 币种, 当前余额, 类型 (BASIC/DEPOSIT)。

---

## 86. 银行字典 (`bank-names`)

**用途**: 银行名称字典 (中国银行/招商/工商等), 给"账户管理"提供下拉选项。

---

## 87. 客户账单 (`bills`)

**用途**: 出账后的客户对账单。一个 bill 包含一段时期内该客户的所有 charges。

**操作**: 
- **生成账单** — 按客户/币种/时间范围批量生成
- **xlsx 导出 (奥沃星样式)** — 27 列订单 + 23 列货件明细, 含差异列 (R-10) ⭐
- **导出 FedEx-B 价账单样式** — 14 列承运商风格
- **导出成本拆分明细** — 13 列细拆 Ground/Fuel/Incentive

---

## 88. 供应商付款 (`payments`)

**用途**: 我们付给承运商的款项记录。

---

## 89. 客户收款 (`receiveds`)

**用途**: 客户付款的真实记录。

**真值**: SELLER-DEMO USD 18.77 (AUDITED, 真有 balance_ledger 对账)。

---

## 90. 资金转账 (`transfers`)

**用途**: 公司内部账户间转账 (例如 USD 账号 → CNY 账号)。

---

## 91. 收款短信 (`received-sms`)

**用途**: 银行收款短信流水, 用 NLP 自动识别"谁付了多少"匹配客户账单。

---

## 92. ⭐ 渠道账单对比 (`carrier-invoice-recon`) — R-4

**用途**: 承运商月底发账单 (xlsx), 我们传上去跟内部 cost charge 对比, 找出差异行。

**核心字段**: 16 条对比记录, 含 partner_code, currency, 应付, 内部成本, 差异。

**操作**: 
- 上传渠道账单 xlsx — 选 partnerCode + currency
- 系统自动 INSERT partner_invoices + 子表 partner_invoice_lines
- 自动跟 cost charges 按 carton_id 对账, 输出差异表

---

## 93. ⭐ 应收实收对比 (`ar-vs-received`)

**用途**: 按客户对比"应该收多少" vs "真实收到多少"。

**真值**: SELLER-DEMO USD 应收 130.37, 实收 18.77, 还应收 111.60。

---

## 94-100. 结算工作台 SWB (Settlement Workbench)

- **待核成本 (UPS 单)** (`swb-cost-pending`) — 承运商账单 vs 内部 cost 差异行待人工核
- **待付供应商** (`swb-pending-pay`) — 需要打款的供应商列表
- **已付成本** (`swb-paid`)
- **利润分析** (`swb-profit`) — 结算视角看利润
- **账龄分析** (`swb-aging`) — 应收 30/60/90 天分段
- **月度财报** (`swb-monthly`)
- **业绩提成** (`swb-commissions`)

---

## 101-104. 财务工作台 FWB (Finance Workbench)

- **预扣明细** (`fwb-prepay`) — 客户预存款扣款流水
- **待财务审核** (`fwb-pending`)
- **已出账** (`fwb-invoiced`)
- **待二审账单** (`fwb-needs-verify`)

---

## 105-108. 总账 GL (General Ledger)

- **试算平衡** (`gl-trial`) — 借贷平衡表
- **资产负债表** (`gl-balance`)
- **利润表** (`gl-income`)
- **现金流量表** (`gl-cash`)

---

# 📋 基础信息

业务定位:**主数据维护**。渠道/客户/产品/价格的字典型数据。

## 109. 分公司管理 (`acc-branches`)

**用途**: 多分公司管理。每个订单挂一个分公司, 用于权限隔离 / 业绩归属。

---

## 110-113. 地区数据

- **地区管理** (`districts`)
- **邮编管理** (`postcodes`) — 邮编 ↔ 城市映射
- **偏远邮编** (`remotes`) — 偏远地址 (UPS Extended Area) 加收费
- **杂费类型** (`fee-types`)

---

## 114. 燃油费用 (`fuels`)

**用途**: UPS/FedEx 每周更新的燃油附加费率。建一条 weekly rate 系统按 effective_date 算 fuel surcharge。

---

## 115. 渠道账号 (`channel-accounts`)

**用途**: 我们在承运商那边的真实账号 (例 UPS J602B0)。每个 channel_account 关联 1 个 channel + 1 个 provider。

**真值**: 32 个账号 — 9 老 ACC (1 真 UPS + 7 NOOP) + 22 XQT NOOP 占位。

---

## 116-117. 产品 + 价格分区

- **销售产品** (`products`)
- **价格分区** (`zones`)

---

## 118. ⭐ 渠道类型 (`channels`)

**用途**: 所有渠道的主档。

**真值**: **31 个渠道**:
- 9 个老 ACC (UPS-GROUND-US, EU-AIR-UPS, FedEx-A/B 等)
- 22 个 XQT (新启天 4 末端派送 + 8 海派 + 10 卡派)

**字段**: 代码, 名称, 运输方式 (lane), 末端方式, 维度因子 (dim_factor), 是否有燃油。

---

## 119-121. 价格管理

- **费率卡** (`rate-cards`) — rate_cards 表
- **定价主控** (`pricing-master`)
- **保险费率** (`insurance-rates`)

---

## 122-127. 物流商往来 (复制核算中心)

物流商列表 (`suppliers`) / 应付款项目 (账单视图) / 物流商返利 / 罚款 / 付款记录 / 退款记录。

---

# 👥 人事组织

## 128. 分公司管理 (`acc-branches`)

(同基础信息 109, 这里只显示分公司视图)

---

# ⚙️ 数据管理 / 系统设置

业务定位:**字典数据 + 接口配置 + Webhook + AI 客服**。

## 129. 国家字典 (`countries`)

**用途**: 国家代码 (ISO 2 位 / 3 位) + 中英文名映射, 给下单时国家下拉用。

---

## 130. 地区管理 (`districts`)

(同 110)

---

## 131-133. 邮编/偏远/燃油 (复制基础信息)

---

## 134. HS 编码字典 (`hscodes`)

**用途**: 海关 HS 编码库, 申报时用。

---

## 135. 价格分区 (`zones`)

---

## 136. 银行字典 (`bank-names`)

---

## 137. 物流接口配置 (`logistics-interfaces`)

**用途**: 跟外部物流系统对接的接口配置 (Karrio / EasyShip / Shippo)。

---

## 138. 进口商预设模板 (`importer-templates`)

**用途**: 美国 EIN / 增值税号等进口商信息预设, 下单时一键填入。

---

## 139-140. Webhook

- **Webhook 端点** (`webhook-endpoints`) — 我们对外暴露的回调 URL
- **Webhook 事件流水** (`webhook-events`) — 已发送的事件日志

---

## 141. API 凭证 (`api-credentials`)

**用途**: 客户/合作伙伴的 API 密钥管理。客户用我们 API 上单需要这里发的 API key + secret。

---

## 142. API 调用日志 (`api-call-logs`)

**用途**: 所有 API 调用流水, 含请求体 + 响应 + 耗时, 用于 debug 和审计。

---

## 143-144. AI 客服

- **AI 客服会话** (`ai-cs-sessions`) — Claude/GPT 跟客户的对话记录
- **AI 客服知识库** (`ai-cs-kb`) — RAG 知识库 (常见问题 + 操作指南)

---

# 附录 A: 系统能力快照 (2026-06-27)

## 财务全链路 ✅ 100% 对齐

```
charges  AR USD 391.00  ═  profit_snapshots USD 391.00
charges  AR CNY 1237.90 ═  profit_snapshots CNY 1237.90
invoice  头 = SUM(lines)                  0 mismatch
payments 重复                              0 笔
charges  孤儿 (无 customer)               0 条
```

## RateEngine 渠道 (31 个)

```
末端派送 lane=US-LAST-MILE       (4 渠道, 4200 阶梯)
海派      lane=US-OCEAN-EXPRESS  (8 渠道, 120 阶梯)
卡派      lane=US-OCEAN-TRUCK    (10 渠道, 333 阶梯)
原 ACC                          (9 渠道)
```

## 计费方式

```
chargeable = max(实重 weightKg, 体积重 volumeCbm × 1_000_000 / dimFactor)

末端派送 dimFactor = 5000
海派/卡派 dimFactor = 6000
```

## Zone 解析 (3 套)

1. **US-LAST-MILE** → `ups_zone_mappings` 表 (917 origin → 905 dest, 返 US-Z002~US-Z008)
2. **US-OCEAN-EXPRESS** → 目的地邮编首位 → USE/USM/USW
   - 加拿大 (CA): K=渥太华 / V=温哥华 / T=卡尔加里 / M=多伦多
3. **US-OCEAN-TRUCK** → channelAccountCode 作仓库代码, regex 匹配 `(^|[、./, ])` 多分隔符

---

# 附录 B: 已删 24 个 tab

```
A. 17 个 HR/非货代业务:
   assets, attendances, borrowings, departments, dividends, employees,
   expense-categories, expenses, fund-persons, funds, social-persons,
   socials, wages, tasks, notices, templates, api-docs

B. 7 个表不存在死链:
   sold-tos, fees, fee-item-types, sales-prices, customer-prices,
   published-prices, files
```

---

# 附录 C: 已实现的 R-X 需求清单

| 需求 | 描述 | 状态 |
|---|---|---|
| R-1 | 同单去重 + add-cost | ✅ |
| R-2 | xlsx 批量导单 | ✅ |
| R-3 | 加成本覆盖 | ✅ |
| R-4 | 渠道账单对比 + Phase B UPS Rating stub | ✅ |
| R-5 | 操作费 API | ✅ |
| R-6 | 预报价池 + 自动核销 | ✅ |
| R-7 | xlsx 批量补收 | ✅ |
| R-8 | 单笔补收 PATCH | ✅ |
| R-9 | 补收/回退聚合列 (restate_added/returned) | ✅ |
| R-10 | 账单 xlsx 导出 + 差异列 | ✅ |
| R-11 | 状态来源 badge (🤖✋📄⚡🔁📥) | ✅ |
| R-12 | RateEngine 客户 commission_rate 真生效 | ✅ |
| R-13 | 费用类目权限 @PreAuthorize | ✅ |

