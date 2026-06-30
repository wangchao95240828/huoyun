# XQT-SaaS 制单流程 SOP (按前端真字段)

**版本**: v2 - 2026-06-30  
**系统**: http://8.148.227.76/  (admin / admin123)  
**字段来源**: 直接从 `apps/web/src/App.vue` accFormFields 抓的真定义

> **每个字段名 100% 跟前端表单一致**, 不再用编出的字段名。  
> 必填字段标 `★`, 可选标 `○`。

---

# Step 1: 建客户 (销售中心 → 客户管理)

**入口**: 左菜单 → 销售中心 → 客户管理 → `+ 新增`

| ★/○ | 前端字段 (form col) | 显示标签 | 类型 | 真实示例 |
|---|---|---|---|---|
| ★ | `Name` | 名称 | text | 卖货流程样例客户 |
| ★ | `Code` | 编码 | text | SELLER-DEMO |
| ○ | `Contacts` | 联系人 | text | 张三 / 13888888888 |
| ○ | `CompanyMobile` | 手机号码 | text | |
| ○ | `CompanyPhone` | 固定电话 | text | |
| ○ | `Fax` | 传真 | text | |
| ○ | `Email` | 邮箱 | text | |
| ○ | `MainProduct` | 主营产品 | text | |
| ○ | `CompanyAddress` | 公司地址 | textarea | |
| ○ | `Group` | 分组 | select (ref customer-groups) | |
| ○ | `Branch` | 分公司 | select (ref branches) | |
| ○ | `Settlement` | 结算方式 | select | MONTHLY/QUARTERLY |
| ○ | `Credits` | 授信额度 | number | 10000 |
| ○ | `Amount1`~`Amount7` | 阶梯金额 1-7 | number | 阶梯计费规则 |
| ○ | `InvoiceTitle` | 开票抬头 | text | |
| ○ | `InvoiceTaxNo` | 税号 | text | |
| ○ | `LoginNo` | 登陆号 | text | (客户后台登录用) |
| ○ | `ApiKey` | API Key | text | (客户用 API 上单) |
| ○ | `isOpen` | 启用 | boolean | true |

**提交后预期**:
- API: `POST /api/acc/customers`
- 表 `customers` 新增 1 行
- 自动建 `financial_accounts` (CUSTOMER owner, balance=0)
- 客户列表立刻出现

---

# Step 2: 配渠道账号 (基础信息 → 渠道账号)  ⭐

**入口**: 左菜单 → 基础信息 → 渠道账号 → 选目标账号 → `编辑` (或 `+ 新增`)

## 2.1 基础信息 (前 11 字段)

| ★/○ | 前端字段 | 显示标签 | 类型 | 真实示例 |
|---|---|---|---|---|
| ★ | `Name` | 账号名称 | text | J602B0 真账号 |
| ○ | `Code` | 编号 | text | J602B0 |
| ★ | `Channel` | 渠道 | select (ref channels) | UPS Ground (US 国内) |
| ○ | `Supplier` | 物流商 | select (ref suppliers) | UPS |
| ○ | `provider_code` | 取号方式 | select | UPS / FEDEX / KARRIO / UPS_DEMO / FEDEX_DEMO / SANDBOX / **NOOP** |
| ○ | `api_key` | API Key (UPS Client ID) | text | IyKfxqS9... |
| ○ | `api_secret` | API Secret | text | ccGm497v... |
| ○ | `account_no` | 承运商账号 (UPS Account) | text | **J602B0** |
| ○ | `endpoint_url` | API Endpoint URL | text | https://onlinetools.ups.com/api |
| ○ | `processing_fee` | 操作费 | number | 0 |
| ○ | `isOpen` | 启用 | boolean | true |

> **关键提示** (避免 TEST0629 那种 bug):  
> `provider_code` 必须 = **UPS** (或 FEDEX/KARRIO), 不能 NOOP — NOOP 现在提交直接报错。  
> `account_no` 必须真实 UPS 账号 (J602B0), 用错号 UPS 返 [120100] ShipperNumber 错。

## 2.2 服务类型 (4 字段)

| ★/○ | 前端字段 | 显示标签 | 选项 |
|---|---|---|---|
| ○ | `ups_service_type` | UPS 服务类型 | Ground / Express / Air |
| ○ | `export_type` | 出口类型 | 销售 / 礼品 / 样品 / 退货 / 维修 |
| ○ | `return_service` | 退货服务 | 无 / PRP / RS |
| ○ | `weight_unit` | 重量单位 | 千克 / 盎司 / 磅 |

## 2.3 托运人 (Shipper) ⭐ 关键 — 提交订单时自动用, 不用每单填

| ★/○ | 前端字段 | 显示标签 | 真实示例 (J602B0 默认) |
|---|---|---|---|
| ○ | `shipper_company` | 托运人公司名称 | Fortune |
| ○ | `shipper_name` | 托运人联系人 | Fortune |
| ○ | `shipper_phone` | **托运人电话 (UPS 必填)** | 0000000000 |
| ○ | `shipper_email` | 托运人邮箱 | |
| ○ | `shipper_address1` | 托运人地址 1 | 1005 Middlesex Ave |
| ○ | `shipper_address2` | 托运人地址 2 | |
| ○ | `shipper_city` | 托运人城市 | Port Reading |
| ○ | `shipper_state` | 托运人省/州 | NJ |
| ○ | `shipper_country2` | 托运人国家二字码 | US |
| ○ | `shipper_postcode` | 托运人邮编 | 07064 |

**预期**: 此渠道账号下所有订单提交 UPS 时, 自动用这套托运人, 不要每单填。

---

# Step 3: 配仓库地址 (基础信息 → 仓库管理)  ⭐

**入口**: 左菜单 → 基础信息 → 仓库管理 → `+ 新增` (或编辑)

| ★/○ | 前端字段 | 显示标签 | 类型 | 真实示例 |
|---|---|---|---|---|
| ★ | `code` | 仓库代码 | text | ONT8 / DTM1 / LCY1 |
| ★ | `name` | 名称 | text | Amazon ONT8 |
| ○ | `warehouseType` | 类型 | select | DOMESTIC / **OVERSEAS** / TRANSIT / VIRTUAL |
| ○ | `countryCode` | 国家二字码 | text | US / GB / DE / FR |
| ○ | `province` | 省/州 | text | CA / NJ / NW / LDN |
| ○ | `city` | 城市 | text | Moreno Valley |
| ○ | `address` | 地址 | text | 24300 Nandina Ave |
| ○ | `postcode` | 邮编 | text | 92551 |
| ○ | `consignee` | 收件人 | text | ONT8 |
| ○ | `company` | 公司名称 | text | Amazon |
| ○ | `status` | 状态 | select | **ACTIVE** / INACTIVE |

**真状态**: 已导入 **240 个亚马逊 FBA 仓**:
```
US 155  GB 31  DE 14  PL 10  FR 6  IT 4  ES 4  NL 2  CZ 1
```

---

# Step 4: 配客户加价策略 (R-12, 可选)

**入口**: 销售中心 → 客户价格策略 → `+ 新增`

| ★/○ | 前端字段 | 显示标签 | 类型 | 真实示例 |
|---|---|---|---|---|
| ★ | `customerCode` | 客户编码 | text | SELLER-DEMO |
| ★ | `channelCode` | 渠道编码 | text | UPS-GROUND-US |
| ★ | `commissionRate` | 佣金率 (1=原价, 0.9=9折) | number | **1.20** (加 20%) / 0.90 (9 折) |
| ○ | `floorAmount` | 底价 (兜底, 可空) | number | 8 |
| ○ | `remark` | 备注 | textarea | "客户合同 2026Q1 涨 20%" |

**业务公式**:
```
AR (客户应收) = 基价 × commissionRate
若 AR < floorAmount: AR = floorAmount
AP (我们成本) = 基价 (不受影响)
利润 = AR - AP
```

---

# Step 5: 配费用类目 (R-13, 财务管理员)

**入口**: 核算中心 → 费用类目 → `+ 新增`

| ★/○ | 前端字段 | 显示标签 | 类型 | 真实示例 |
|---|---|---|---|---|
| ★ | `code` | 代码 | text | FUEL / GROUND_COMM |
| ★ | `name` | 名称 | text | 燃油附加费 |
| ○ | `category` | 类别 | select | FREIGHT / FUEL / SURCHARGE / INSURANCE |
| ○ | `defaultSide` | 默认方向 | select | AR (应收) / AP (应付) |
| ○ | `defaultUom` | 计费单位 | select | KG / LB / PIECE / SHIPMENT / PERCENT |

**权限要求**: 只有 `ROLE_ADMIN` / `ROLE_FINANCE_MANAGER` 能新增/删除。

---

# Step 6: 配预报价池 (R-6, 可选)

**入口**: 核算中心 → 预报价池 → `+ 新增`

| ★/○ | 前端字段 | 显示标签 | 类型 | 示例 |
|---|---|---|---|---|
| ★ | `channelCode` | 渠道编码 | text | UPS-GROUND-US |
| ○ | `customerCode` | 客户编码 (可空=通用) | text | SELLER-DEMO |
| ★ | `qty` | 件数 | number | 100 |
| ★ | `unitPrice` | 单价 | number | 13.85 |
| ○ | `currency` | 币种 | select | USD / CNY |
| ○ | `targetWeightKg` | 目标重(kg) | number | 1.5 |
| ○ | `remark` | 备注 | textarea | |

---

# Step 7: 下单 ⭐ (制单中心 → 快件订单 → + 新增)

下单弹窗有 **8 个区块, 38 个真字段**, 按真表单顺序:

## 7.1 订单基本

| ★/○ | 前端字段 (fullOrderData.*) | 显示标签 | 类型 |
|---|---|---|---|
| ★ | `orderNo` | 客户单号 | text |
| ○ | `orderDate` | 订单日期 | date |
| ★ | `customerId` | 客户 | select (ref customers) |
| ★ | `product` | 销售产品 (= 渠道) | select |
| ○ | `channelAccount` | 渠道账号 | select |
| ○ | `currency` | 币种 | select USD/CNY |

## 7.2 货物属性

| ★/○ | 字段 | 标签 | 类型 |
|---|---|---|---|
| ○ | `packageType` | 包裹类型 | select PARCEL/DOC |
| ○ | `batteryType` | 电池类型 | select 0=无/1=内置/2=干电池 |
| ○ | `batteryCode` | 电池代码 | text |
| ○ | `specialType` | 特殊类型 | select 0=一般/5=仿牌 |
| ○ | `labelType` | 标签格式 | select PDF/PNG/ZPL |
| ○ | `materialsCn` | 内件类型(中) | text |
| ○ | `materialsEn` | 内件类型(英) | text |

## 7.3 重量 + 体积

| ★/○ | 字段 | 标签 | 类型 |
|---|---|---|---|
| ★ | `weight` | 重量 (kg) | number |
| ○ | `volume` | 体积 (CBM) | number |
| ★ | `piece` | 件数 | number |
| ○ | `declaredValue` | 申报价值 | number |
| ○ | `insurance` | 保险费 | number |
| ○ | `freight` | 运费 | number (自动算) |

## 7.4 收件人 (Receiver) ⭐ 仓库地址+国家**可关键字搜索**

| ★/○ | 字段 (fullOrderData.receiver.*) | 标签 | 类型 |
|---|---|---|---|
| ○ | `warehouseCode` | **仓库代码** ⭐ | **input + datalist** 输入关键字搜 240 仓 (ONT8 / DTM1 / LCY1) |
| ★ | `country` (注: 顶层 fullOrderData.country) | 目的地国家 ⭐ | **input + datalist** 输入关键字搜 (US / GB / DE) |
| ★ | `name` | 收件人姓名 | text |
| ★ | `company` | 公司 | text |
| ★ | `phone` | 电话 | text |
| ○ | `vat` | 税号 (VAT, EU 必填) | text |
| ★ | `address` | 地址 | text |
| ○ | `houseNo` | 门牌号 | text |
| ○ | `city` | 城市 | text |
| ○ | `province` | 省/州 | text |
| ○ | `postcode` | 邮编 | text |
| ○ | `areaCode` | 邮编 (备用, 检偏远) | text |

**重要**:
- 选仓库代码 → 自动填 country/state/city/address/postcode (`applyWarehouseAddress`)
- 输 areaCode (邮编) → 自动检偏远 (`checkRemotePostcode`)

## 7.5 发件人 (Shipper)

**注**: 一般**不用填** — 自动从渠道账号 `shipper_*` 字段读 (Step 2.3 配的)。
表单仍有字段, 留空即可:

| ★/○ | 字段 (fullOrderData.shipper.*) | 标签 |
|---|---|---|
| ○ | `company` | 公司 |
| ○ | `name` | 联系人 |
| ○ | `phone` | 电话 |
| ○ | `address` | 地址 |
| ○ | `city` | 城市 |
| ○ | `province` | 省 |
| ○ | `postcode` | 邮编 |
| ○ | `vat` | 税号 |

## 7.6 进口商 ShipTo (DDP/DDU 必填)

| ★/○ | 字段 (fullOrderData.shipTo.*) | 标签 |
|---|---|---|
| ○ | `templateId` | 用进口商预设模板 (select) |
| ○ | `company` / `name` / `phone` / `address` / `city` / `province` / `postcode` / `vat` | 同 shipper 结构 |

## 7.7 装箱清单 (packageList[]) — 多包裹时拆

每包裹 4 字段:

| ★/○ | 字段 | 标签 | 类型 |
|---|---|---|---|
| ★ | `weight` | 单件重 (kg) | number |
| ○ | `length` | 长 (cm) | number |
| ○ | `width` | 宽 (cm) | number |
| ○ | `height` | 高 (cm) | number |

## 7.8 申报清单 (declare[]) — 海关必填

每个 SKU 7 字段:

| ★/○ | 字段 | 标签 | 类型 |
|---|---|---|---|
| ★ | `name_en` | 英文品名 | text |
| ○ | `name_cn` | 中文品名 | text |
| ○ | `hs_code` | 海关编码 | text |
| ★ | `quantity` | 数量 | number |
| ★ | `price` | 单价 | number |
| ○ | `material` | 材质 | text |
| ○ | `brand` | 品牌 | text |

---

# Step 8: 提交承运商 (Submit)

**入口**: 制单中心 → 快件订单 → 选行 → 顶部 `批量提交` (推荐) 或行 ✅

**自动行为** (调 UpsGroundCarrierGateway):
1. 从 `acc_channel_accounts` 拉 (provider_code='UPS') 的: api_key / api_secret / account_no / endpoint_url / **shipper_* 全部字段**
2. 拼 UPS Rating + Shipment API 请求 (用 fullOrderData.receiver + 渠道账号 shipper_*)
3. UPS 返 tracking (1Z+account_no+流水, 例 `1ZJ602B00305626286`)
4. 状态: DRAFT → SUBMITTED, 写 shipments + cartons + AR/AP charges

**真验证 (TEST0629 真案例)**:
```
请求: POST /api/acc/orders/batch-submit {"ids":["6497b940-..."]}
响应: {"submitted":1, "results":[{
  "orderNo":"TEST0629",
  "trackingNo":"1ZJ602B00305626286",  ← 真 UPS 单号
  "shipmentNo":"SHP-TEST0629"
}]}
```

---

# Step 9: 应收/应付 (核算中心) + 临时额度 (财务中心)

## 9.1 看应收 charges

**入口**: 核算中心 → 应收运费 (charges)

每行真字段:
```
expressNo (运单号) / customerName / productName /
country / chargeWeight (计费重kg) / type / amount /
paid (实收) / source_type (来源 emoji badge) /
restate_added (R-9 补收+) / restate_returned (R-9 回退-) /
theDate
```

## 9.2 单笔补收 (R-8)

行操作点 ✏️ → 弹窗:

| 字段 | 类型 | 真值 |
|---|---|---|
| `newAmount` | number | 新金额 |
| `mode` | radio | **DELTA** (差值, 推荐) / OVERWRITE (覆盖) |
| `remark` | text | "DWS 复称" |

提交后**自动审核**, balance_ledger 立即写, 应收实收对比实时平。

## 9.3 临时额度 (财务中心 → 客户账户)

**入口**: 财务中心 → 应收 → 客户账户 (临时额度) → 行点 💳

弹窗字段:
| 字段 | 类型 | 示例 |
|---|---|---|
| `currency` | select | USD / CNY / EUR / HKD |
| `amount` | number | 500 (新临时额度, 0=取消) |
| `remark` | text | "客户预付到账前临时垫资" |

**业务公式**: `余额 = 总付款额 + 临时额度 - 使用金额`

---

# Step 10: 出账单 + 收款 (财务中心)

## 10.1 生成账单 (财务中心 → 客户账单 → 生成账单)

| ★/○ | 字段 | 类型 | 真值 |
|---|---|---|---|
| ★ | `customer_id` | select (ref customers) | SELLER-DEMO |
| ○ | `currency` | select | USD / CNY |
| ○ | `date_from` | date | 默认本月 1 号 |
| ○ | `date_to` | date | 默认今天 |
| ○ | `order_no` | text | 留空=全部订单 |

**关键 UX 改进** (黄色 warning):
- 选完客户立刻显示该客户**已出账历史** (币种 / 数量 / 累计 / 最近日期 / 期范围)
- 避免跟历史期重叠重复出账

**预览/选行**: 点 🔍 预览可开账明细 → 看 charges:
- 🟢 BILLABLE 可开
- 🟣 BILLED 已开 (灰)
- 🔴 VOIDED 作废 (红)
- 🟡 UNAUDITED 待审 (黄)

## 10.2 导出账单 xlsx (奥沃星样式)

**入口**: 财务中心 → 客户账单 / 已出账 → 行 📥 **导出**

预期: 下载 `账单_INV*.xlsx`, 2 sheet:
- Sheet 1: 订单列表 (27 列, 含差异列)
- Sheet 2: 货件明细 (23 列, carton+申报)

## 10.3 打印对账单

**入口**: 财务中心 → 已出账 → 行 📄 **打印对账单**

预期: 弹新窗 HTML 对账单 → Ctrl+P 另存 PDF 发客户

## 10.4 渠道账单 FedEx-B 风格 (R-4 对账)

**入口**: 财务中心 → 渠道账单对比 → 顶部:
- `📥 上传渠道账单 xlsx` (输 partnerCode + currency)
- `📤 导出 FedEx-B 风格` (按 partner/currency/dateRange 过滤)

预期: 18 列 xlsx 对齐 FedEx-B价账单.xls 真格式

---

# Step 11: 收款 (财务中心 → 客户收款)

**入口**: 财务中心 → 客户账单 → 行 `快速收款`

| 字段 | 类型 |
|---|---|
| `customer_id` | select |
| `currency` | select |
| `settled_amount` | number |
| `bank_account_id` | select (ref banks) |

**预期**:
1. payments 表新增 audit_status=PENDING
2. 审核后 → AUDITED → 自动触发 BalanceLedgerSideEffect
3. balance_ledger 写 3 条 (CUSTOMER RECEIPT / COMPANY PAYMENT / INVOICE CREDIT)
4. charges.paid_amount += 收款额
5. customer_invoices.paid_amount += 收款额
6. **应收实收对比立刻平账**

---

# 📊 真状态快照 (2026-06-30)

```
现真能发单账号 (5 个 UPS):
  J602B0, ACC-UPS-GROUND-US-RES, ACC-EU-AIR-UPS,
  XQT-UPS-GROUND-COMM-NOOP*, XQT-UPS-GROUND-RES-NOOP*
  (* 已 UPDATE provider_code='UPS' + account_no='J602B0')

NOOP 占位 (27 个): 提交时直接报错, 不会出假 tracking

FBA 仓: 240 (US 155 + EU 41 + UK 31 + 13 其他)
31 渠道 (9 老 ACC + 22 XQT 新启天)
4711 价格阶梯
```

---

# 🚨 提交失败常见错误对照

| UPS 错误码 | 真因 | 修法 |
|---|---|---|
| `120100` | 发件人姓名/账号错 | 改 channel_account.account_no = J602B0 (真账号) |
| `120109` | 发件人电话缺/格式错 | 改 channel_account.shipper_phone (Step 2.3) |
| `BAD_REQUEST: NOOP` | 渠道账号未真接入 | 改 provider_code = UPS (Step 2.1) |
| `订单状态 SUBMITTED 不允许重新提交` | 已提交过, 又点提交 | 先到取消订单 → 彻底删除, 再重提 |
| `渠道 [X] 未配置可用取号接口` | strict mode + 没真 provider | 配 UPS API key |

---

# 附录: 真 API 路径

| 操作 | HTTP | URL |
|---|---|---|
| 建客户 | POST | /api/acc/customers |
| 改渠道账号 | PUT | /api/acc/channel-accounts/{id} |
| 建仓库 | POST | /api/acc/warehouses |
| 客户加价 | POST | /api/acc/customer-rate-strategies |
| 费用类目 | POST | /api/acc/charge-items |
| **下单** | POST | /api/acc/orders |
| **提交承运商** | POST | /api/acc/orders/batch-submit |
| 单笔补收 | PATCH | /api/acc/charges/{id}/restate |
| 临时额度 | PUT | /api/acc/customer-accounts/{customerId}/temporary-credit |
| 生成账单 | POST | /api/acc/bills/generate |
| 账单已出 | GET | /api/acc/bills/customer-billed-status?customerId= |
| 导出账单 xlsx | GET | /api/acc/bills/{id}/export-xlsx |
| 打印对账单 | GET | /api/acc/finance-workbench/invoices/{id}/print |
| 导出 FedEx-B | GET | /api/acc/partner-invoices/export-fedex-style?partnerCode=&currency= |
