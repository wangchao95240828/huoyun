# 新航线统一平台客户方向模型

版本：2026-05-07  
背景：新智慧和 ACC 不是简单两个外部系统，而是对应两条客户业务方向：**卖货客户** 与 **制单客户**。

## 1. 核心修正

之前文档更多从“ACC / 新智慧两套系统复刻”的角度描述。现在需要修正为：

> 新平台要服务两类客户方向，ACC 和新智慧只是这两类方向的旧系统样本、字段来源和对照来源。

新平台主模型不应该把业务边界写成 `ACC_CUSTOMER` 或 `XQT_CUSTOMER`，而应该抽象成：

| 客户方向 | 建议编码 | 核心诉求 | 系统承接 |
| --- | --- | --- | --- |
| 卖货客户 | `SELLER_CUSTOMER` | 货品、订单、仓库、发货、账单、利润、售后、POD、客户对账 | 订单、仓库、财务、客户门户、报表 |
| 制单客户 | `DOCUMENT_CUSTOMER` | 制单、取号、面单、轨迹、运费试算、余额、API 下单、批量导入 | 制单 API、运单、标签、轨迹、计费、客户 API |

ACC / 新智慧在架构中应登记为 `source_system`，而不是客户方向本身：

| 维度 | 说明 |
| --- | --- |
| `customer_direction` | 主系统业务方向：卖货客户 / 制单客户 |
| `source_system` | 样本来源：ACC / XQT / LOCAL |
| `external_object_refs` | 旧系统 ID、旧单号、旧字段引用 |
| `comparison_cases` | 用旧系统页面/API 对照新系统结果 |

## 2. 两类客户的业务差异

### 2.1 卖货客户

卖货客户更关注“从货到钱”的闭环：

1. 商品/货品资料。
2. 客户订单和发货需求。
3. 仓库收货、库存、拣货、装箱、出库。
4. 供应商/渠道成本。
5. 客户账单、供应商账单、利润。
6. 售后、POD、问题件、理赔。
7. 销售提成和经营报表。

重点页面：

| 模块 | 能力 |
| --- | --- |
| 订单 | 客户订单、销售订单、发货计划 |
| 仓库 | 收货、库存、拣货、装箱、出库 |
| 运单 | 运单、提单、货箱、轨迹 |
| 财务 | 客户流水、客户账单、利润、提成 |
| 售后 | POD、问题件、理赔、退件 |

### 2.2 制单客户

制单客户更关注“从制单到履约状态”的效率：

1. API 下单或批量制单。
2. 运费试算和服务选择。
3. 转单号、面单、标签、换标。
4. 轨迹查询和状态回传。
5. 余额、预扣费、客户账单。
6. 批量导入、批量审核、批量作废。

重点页面/API：

| 模块 | 能力 |
| --- | --- |
| 客户 API | 认证、查价、下单、查询、余额 |
| 制单 | 快速下单、批量导入、制单校验 |
| 标签 | 面单、换标、文件下载 |
| 轨迹 | 轨迹查询、状态订阅 |
| 财务 | 预扣费、余额、客户账单、收款 |

## 3. 主数据设计建议

### 3.1 customers 增加方向字段

建议 `customers` 或客户扩展表增加：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `customer_direction` | enum/string | `SELLER_CUSTOMER` / `DOCUMENT_CUSTOMER` / `BOTH` |
| `service_modes` | array/json | 支持的服务模式，如 `SELLER_FULFILLMENT`, `DOCUMENT_SHIPPING` |
| `source_system` | string | 首次来源：ACC / XQT / LOCAL |
| `external_customer_refs` | via `external_object_refs` | 旧系统客户 ID、客户编号、API ID |
| `settlement_profile_id` | uuid | 结算配置 |
| `api_enabled` | boolean | 是否开通客户 API |

### 3.2 不建议

不建议用两个客户表：

- `acc_customers`
- `xqt_customers`

这样会让客户、账单、权限、对账长期割裂。正确做法是一个 `customers` 主表，用 `customer_direction` 和 `external_object_refs` 区分业务方向和来源。

## 4. 订单与运单设计

两类客户共用主线：

```text
customer -> order -> shipment -> cartons -> charges -> invoices -> payments -> ledger
```

但入口和字段重点不同：

| 方向 | 入口 | 订单重点 | 财务重点 |
| --- | --- | --- | --- |
| 卖货客户 | 销售订单、仓库收货、发货计划 | 货品、库存、仓库、出货、售后 | 客户账单、供应商成本、利润、提成 |
| 制单客户 | API 下单、批量制单、快速下单 | 收发件、申报、重量、渠道、面单 | 预扣费、余额、运费、客户账单 |

建议 `orders` / `shipments` 增加：

| 字段 | 说明 |
| --- | --- |
| `customer_direction` | 订单所属客户方向 |
| `order_entry_type` | `SALES_ORDER`, `API_ORDER`, `MANUAL_DOCUMENT`, `BATCH_IMPORT` |
| `service_mode` | 卖货履约、制单发货、仓配、转运等 |
| `source_system` | ACC / XQT / LOCAL |
| `external_ref_id` | 旧系统对象引用 |

## 5. 权限与菜单设计

菜单不应该只是 `ACC` 和 `新智慧` 两个入口，而应该变成：

| 菜单组 | 面向方向 |
| --- | --- |
| 卖货客户工作台 | `SELLER_CUSTOMER` |
| 制单客户工作台 | `DOCUMENT_CUSTOMER` |
| 订单/运单中心 | 两者共用，按方向过滤 |
| 仓库中心 | 卖货客户为主，制单客户可按需启用 |
| 财务中心 | 两者共用，但账单和余额口径不同 |
| 客户 API | 制单客户为主，卖货客户可开放查询/对账 |
| 复刻对照 | 内部使用，按 ACC/XQT 来源查看 |

权限示例：

| 权限 | 说明 |
| --- | --- |
| `customer.seller.read` | 查看卖货客户 |
| `customer.document.read` | 查看制单客户 |
| `order.seller.write` | 维护卖货订单 |
| `order.document.write` | 维护制单订单 |
| `api.document.order` | 制单客户 API 下单 |
| `finance.seller.invoice.read` | 卖货客户账单 |
| `finance.document.balance.read` | 制单客户余额 |

## 6. 财务差异

| 财务对象 | 卖货客户 | 制单客户 |
| --- | --- | --- |
| 应收 | 销售订单/发货/增值服务形成应收 | 制单运费、附加费、预扣费形成应收 |
| 应付 | 供应商、仓库、渠道、保险、派送成本 | 渠道成本、标签/面单、保险成本 |
| 账户 | 客户账期、应收账款、回款 | 余额、预充值、预扣费、欠费 |
| 账单 | 月结账单、销售/仓配账单 | 运费账单、制单账单、充值流水 |
| 利润 | 销售收入 - 仓库/渠道/派送/保险成本 | 运费收入 - 渠道成本 - 附加费成本 |

因此财务表要共用，但字段要能区分 `customer_direction` 和 `service_mode`。

## 7. 对 ACC / 新智慧文档口径的修正

后续文档中建议这样表述：

| 旧表述 | 新表述 |
| --- | --- |
| ACC 和新智慧是两套系统要集成 | ACC 和新智慧是两类客户方向的旧系统样本 |
| 复刻 ACC / 复刻新智慧 | 复刻卖货客户方向 / 制单客户方向的业务能力，并用 ACC/XQT 对照 |
| ACC 页面 / 新智慧页面 | 旧系统来源页面，用于字段映射和对照 |
| ACC 客户 / 新智慧客户 | 主系统客户 + `customer_direction` + `external_object_refs` |

## 8. 测试用例影响

对照用例要新增维度：

| 字段 | 说明 |
| --- | --- |
| `customer_direction` | `SELLER_CUSTOMER` / `DOCUMENT_CUSTOMER` |
| `source_system` | ACC / XQT |
| `service_mode` | 卖货履约、制单发货等 |

示例：

```json
{
  "caseNo": "DIR-DOC-001",
  "customerDirection": "DOCUMENT_CUSTOMER",
  "sourceSystem": "ACC",
  "scenario": "API 下单并生成面单",
  "expectedResult": {
    "orderEntryType": "API_ORDER",
    "labelGenerated": true,
    "balanceDeducted": true
  }
}
```

```json
{
  "caseNo": "DIR-SELLER-001",
  "customerDirection": "SELLER_CUSTOMER",
  "sourceSystem": "XQT",
  "scenario": "销售发货生成客户账单",
  "expectedResult": {
    "warehouseFlow": true,
    "customerInvoiceGenerated": true,
    "profitVisible": true
  }
}
```

## 9. 开发优先级

P0 必须马上补进设计：

1. `customer_direction` 字段和枚举。
2. 菜单按客户方向重新命名。
3. 订单/运单/财务 API 查询支持 `customerDirection`。
4. 对照用例支持 `customerDirection`。
5. 前端驾驶舱区分卖货客户、制单客户指标。

P1 再做：

1. 两类客户不同首页。
2. 两类客户不同账单模板。
3. 制单客户 API 门户。
4. 卖货客户仓配和售后工作台。

