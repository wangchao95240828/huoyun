# ACC API 反推数据库结构设计

## 结论

ACC API 的核心数据模型不是单纯的“订单表”，而是围绕 `Express` 运单展开：

- `Online` 保存 API 下单/预报时的收件、重量、申报金额和标签偏好。
- `Express` 保存系统内运单主档、产品、国家、状态、转单号、计费结果和在线下单结果。
- `Express_Item` 保存申报明细。
- `Online_Package`、`Online_Package_Item`、`Online_Package_List` 保存箱级装箱单。
- `Express_Charge`、`Customer_Balance`、`Customer_Balance_History` 保存预扣费和余额流水。
- `Express_TrackNo`、`Online_TrackNo`、`Express_Process`、`Transit_Process`、`Stowage_Process` 共同组成轨迹数据。

如果要在新 SaaS 中承接 ACC API，建议保留一层 ACC 兼容适配器，把旧字段映射到新系统的 `shipments`、`cartons`、`declarations`、`charges`、`tracking_events`、`ledger_entries`。不要直接把 ACC 表照搬成最终业务模型。

## 反推范围

反推来源是本地 ACC 源码中的 API 文件：

- `acc/api/APIClass.php`: 主接口，包含预报、修改、提交、查询、标签、价格、余额、轨迹、配载同步。
- `acc/api/APIClass2.php`: 与主接口几乎一致，但签名校验被注释，属于高风险入口。
- `acc/api/sumy.php`: 第三方推单接口。
- `acc/api/Track.php`: 公开轨迹查询。
- `acc/api/Scale.php`: 称重设备查询。
- `acc/api/getNewLabel.php`: 新旧单号换标、面单文件查询。

ACC 使用 `~!@_` 作为表前缀占位符，运行时由 `SYS_ID=358` 替换。因此下文写 `Express`，实际旧库表名通常是 `358_Express`。

本文只覆盖 API 直接读写到的表和字段。旧后台可能还有更多字段，不应把本文当成完整旧库 DDL。

## API 到数据表

| API 动作 | 主要能力 | 读写表 |
| --- | --- | --- |
| `PreOrder` | 创建预报单，不提交渠道 | `Online`、`Express`、`Express_Item`、装箱单表、余额/费用表 |
| `Modify` | 修改未收货订单 | 同 `PreOrder`，按 `Express.No` 找旧单 |
| `PreSubmit` | 预提交，生成可提交数据但不一定取号 | 同 `Submit` 前置部分 |
| `Submit` | 在线下单、取转单号、写状态 | `Express`、`Express_Item`、`Express_TrackNo`、`Online_TrackNo`、`Express_Status` |
| `Cancel` | 申请作废 | `Express`、`Online_Void`、`Express_Status` |
| `Query` | 查询订单与申报 | `Express`、`Online`、`Country`、`Product`、`Express_Item`、`Online_TrackNo`、`Online_Shipper` |
| `Label` | 获取/生成面单 | `Express`、`Online`、`Channel_Account`、`Country`、`Online_Shipper`、`Express_Item`、`Online_TrackNo`、`Express_TrackNo`、`Express_Status` |
| `Status` | 查询订单状态码 | `Express` |
| `Track` | 查询综合轨迹 | `Express`、`Shipment`、`Shipment_Item`、`Transit`、`Transit_Process`、`Online_Package`、`Online_Package_Item`、`Stowage`、`Stowage_Process`、`Express_Process`、`Track_Item`、`Track_Location` |
| `Price` | 试算销售价 | `Product`、`Channel`、`Country`、`Product_Price`、`Product_Item`、`Product_Zone`、`Zone_Country` |
| `Product` / `Channel` | 查询可用产品 | `Product`、`Channel` |
| `Balance` | 查询客户余额 | `Customer_Balance`、`Currency` |
| `Sync` | 同步客户配载 | `Stowage`、`Stowage_Category`、`Online_Package`、`Online_Package_Item` |
| `getNewLabel.php` | 换单号、查面单文件 | `Online_File`、`Express_TrackNo`、`Change`、`Change_No` |
| `Scale.php` | 称重设备认证/状态 | `Scale` |

注意：主接口的 `Submit` 分支调用了 `doSubmit()`，但 `APIClass.php` 中没有定义该方法。实际提交逻辑集中在 `doCreate($SubmitType)` 后半段，迁移时应按行为兼容，不要照函数名建模。

## 领域关系

```text
Customer 1--n Customer_API
Customer 1--n Express
Customer 1--n Customer_Balance
Customer 1--n Stowage

Channel 1--n Product
Product 1--n Express
Country 1--n Express

Online 1--1 Express via Express.Receipt
Express 1--n Express_Item
Express 1--n Express_TrackNo
Express 1--n Online_TrackNo
Express 1--n Express_Status
Express 1--n Express_Process
Express 1--n Express_Charge
Express 1--0..1 Online_Package

Online_Package 1--n Online_Package_Item
Online_Package_Item 1--n Online_Package_List
Online_Package_Item n--0..1 Stowage

Shipment 1--n Shipment_Item
Shipment_Item n--1 Express
Shipment n--0..2 Transit
Transit 1--n Transit_Process
Stowage 1--n Stowage_Process
```

## 核心表设计

### 客户与认证

#### `Customer`

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 客户主键 |
| `Code` | varchar(50) | 客户编号，提交渠道时会作为 `CustomerCode` |
| `Group` | bigint | 客户分组，用于客户组渠道限制 |
| `Credits` | decimal(18,4) | 授信额度 |
| `Balance` | decimal(18,4) | CNY 口径汇总余额，旧逻辑方向需要重新校准 |

#### `Customer_API`

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | API 凭证主键 |
| `Customer` | bigint fk | 关联 `Customer.Id` |
| `APIID` | varchar(100) | API 用户名 / user |
| `APIKey` | varchar(100) | 签名密钥或 token |
| `Count` | int | API 调用计数 |

索引：`unique(APIID)`，`unique(APIKey)`，`index(Customer)`。

### 主数据

#### `Channel`

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 物流渠道主键 |
| `Name` | varchar(100) | 渠道名称 |
| `Code` | varchar(50) | 渠道编码，部分接口/插件使用 |
| `isOpen` | tinyint/bool | 是否启用 |
| `TheOrder` | int | 排序 |

#### `Channel_Account`

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 渠道账号主键 |
| `Code` | varchar(80) | 插件代码 / 账号代码 |
| `Name` | varchar(100) | 账号名称 |
| `Product` | bigint | 限定可服务的销售产品，0 表示不限 |
| `Arg` | text/json | 插件参数 |
| `isDebug` | tinyint/bool | 是否调试模式 |
| `MaxCount` | int | 渠道账号最大票数限制 |
| `MaxPiece` | int | 最大件数 |
| `MaxWeight` | decimal(18,4) | 最大重量 |

#### `Product`

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 销售产品主键 |
| `Name` | varchar(100) | 产品名称 |
| `Code` | varchar(50) | API 入参 `Product` 对应值 |
| `Channel` | bigint fk | 关联 `Channel.Id` |
| `ChannelType` | bigint | `sumy.php` 使用的渠道关联字段 |
| `Type` | int | 产品类型，`sumy.php` 用于过滤 |
| `Customer` | bigint | 客户专属产品 |
| `isOpen` | tinyint/bool | 是否启用 |
| `TheOrder` | int | 排序 |

#### `Country`

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 国家主键 |
| `Name` | varchar(100) | 英文国家名 |
| `Code2` | char(2) | API 入参 `Country` 使用的二字码 |
| `CN` | varchar(100) | 中文国家名 |

#### 价格主数据

| 表 | 关键字段 | 用途 |
| --- | --- | --- |
| `Product_Price` | `Id`、`Product` | 产品价卡主表 |
| `Product_Item` | `Id`、`Price`、`StartTime`、`EndTime` | 价卡明细版本 |
| `Product_Zone` | `Id`、`Item`、`Zone`、`Country` | 价卡分区 |
| `Zone_Country` | `Id`、`Country` | 分区国家列表 |
| `Customer_Product` | `Customer`、`Product`、`Account` | 客户渠道账号限制 |
| `Customer_Group_Product` | `Group`、`Product`、`Account` | 客户组渠道账号限制 |
| `Currency` | `Id`、`Name`、`Code`、`Symbol`、`Rate`、`Decimal`、`TheOrder` | 币种、汇率、小数位 |

### 订单与运单

#### `Online`

`Online` 更像 API 收件信息和在线制单请求记录，不是最终运单主表。

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 主键 |
| `Token` | bigint/string | API 入参识别码 |
| `OrderNo` | varchar(30) | 客户订单号，来自 `No` |
| `Warehouse` | bigint | 仓库代码对应 `Online_Warehouse.Id` |
| `Weight` | decimal(18,4) | 入参重量 |
| `Volume` | decimal(18,4) | 入参材积重 |
| `Piece` | int | 件数 |
| `Company` | varchar(60) | 收件公司 |
| `Country` | bigint fk | 国家 |
| `Consignee` | varchar(80) | 收件人 |
| `Phone` | varchar(30) | 电话 |
| `Province` | varchar(50) | 州/省 |
| `City` | varchar(50) | 城市 |
| `Address` | varchar(160) | 地址 |
| `TaxNo` | varchar(80) | 税号 |
| `Currency` | bigint/int | 申报币种，旧代码默认 1 |
| `DeclaredValue` | decimal(18,4) | 申报金额 |
| `BatteryCode` | int | 原始电池代码 |
| `Services` | int | 附加服务位图 |
| `LabelType` | tinyint | 0 PDF，1 ZPL |
| `Insurance` | decimal/bool | 保险值，当前 API 默认 0 |
| `Pay` | decimal/bool | 支付状态，当前 API 默认 0 |
| `Count` | int | 提交/打印次数相关字段 |
| `Shipper` | bigint | 关联 `Online_Shipper.Id` |
| `Sold` | bigint | 进口商/售达方 `Online_Shipper.Id` |

索引：`index(Token)`，`index(OrderNo)`。

#### `Express`

`Express` 是 ACC 的运单主表，新系统应映射为 `shipments`。

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 主键 |
| `Customer` | bigint fk | 客户 |
| `No` | varchar(30) | 客户参考号/API 单号 |
| `CompanyNo` | varchar(50) | 系统内部单号 |
| `TheDate` | date | 订单日期 |
| `Type` | int | 快件类型，API 限制 0/1 |
| `Country` | bigint fk | 国家 |
| `Postcode` | varchar(20) | 邮编 |
| `Product` | bigint fk | 销售产品 |
| `BatteryType` | int | 电池类型归一值 |
| `SpecialType` | int | 敏感/仿牌类型，0-5 |
| `MaterialsEN` | varchar(50) | 英文货物描述 |
| `MaterialsCN` | varchar(50) | 中文货物描述 |
| `Remark` | varchar/text | 备注 |
| `Receipt` | bigint fk | 关联 `Online.Id` |
| `Piece` | int | 件数 |
| `Weight` | decimal(18,4) | 计费前/入库重量 |
| `Volume` | decimal(18,4) | 材积 |
| `VolumeRate` | decimal(18,4) | 材积系数 |
| `ChargeWeight` | decimal(18,4) | 计费重 |
| `Unit` | int/string | 重量单位 |
| `Sale` | bigint | 销售价卡/价格 ID |
| `Shipping` | bigint | 物流成本价卡/渠道价格 ID |
| `ChannelAccount` | bigint | 实际提交渠道账号 |
| `Currency` | bigint fk | 计费币种 |
| `Amount` | decimal(18,4) | 运费 |
| `Charges` | decimal(18,4) | 燃油费 |
| `Surcharge` | decimal(18,4) | 附加费 |
| `Paid` | decimal(18,4) | 预扣合计 |
| `CNY` | decimal(18,4) | 折合 CNY |
| `TrackNo` | varchar(80) | 主转单号 |
| `Status` | int | 订单业务状态 |
| `Delivery` | int | 轨迹派送状态 |
| `Current` | varchar/text | 最新轨迹描述 |
| `CurrentTime` | datetime | 最新轨迹时间 |
| `Orders` | int | 收货/签入状态相关字段 |
| `JoinID` | bigint/int | 合并订单关联 |
| `AddId` | bigint | 创建人 ID |
| `AddName` | varchar(50) | 创建人 |
| `AddTime` | datetime | 创建时间 |
| `ModifyTime` | datetime | 修改时间 |

约束建议：`unique(Customer, No)`，`index(TrackNo)`，`index(Receipt)`，`index(Status)`。

#### `Express_Item`

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 主键 |
| `Express` | bigint fk | 关联 `Express.Id` |
| `Name` | varchar(200) | 英文品名 |
| `CNName` | varchar(200) | 中文品名 |
| `Origin` | varchar(50) | 原产国 |
| `HSCode` | varchar(30) | HS Code |
| `Price` | decimal(18,4) | 单价 |
| `Quantity` | decimal(18,4) | 数量 |
| `Remark` | varchar(100) | 备注/Note |

### 地址与仓库

| 表 | 关键字段 | 用途 |
| --- | --- | --- |
| `Online_Warehouse` | `Id`、`Code`、`Company`、`Consignee`、`Province`、`City`、`Phone`、`Postcode`、`Address` | API 传 `Warehouse` 时自动补收件地址 |
| `Online_Shipper` | `Id`、`Type`、`Company`、`Consignee`、`Address`、`Phone`、`Postcode`、`City`、`Province`、`TaxNo` | 发件人、售达方/进口商 |

### 装箱单与箱级明细

#### `Online_Package`

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 主键 |
| `Express` | bigint fk | 关联 `Express.Id` |
| `Customer` | bigint fk | 客户 |
| `No` | varchar(30) | 装箱单号，默认等于订单 `No` |
| `TheDate` | date | 日期 |
| `Piece` | int | 箱数 |
| `Quantity` | decimal(18,4) | 总商品数量 |
| `DeclaredValue` | decimal(18,4) | 总申报金额 |
| `TaxAmount` | decimal(18,4) | 税额 |
| `Company` | varchar(60) | 收件公司 |
| `Consignee` | varchar(80) | 收件人 |
| `Province` | varchar(50) | 州/省 |
| `City` | varchar(50) | 城市 |
| `Phone` | varchar(30) | 电话 |
| `Address` | varchar(160) | 地址 |
| `Audit` | int | 审核状态，API 创建默认 999 |
| `AddName` | varchar(50) | 创建人 |
| `AuditName` | varchar(50) | 审核人 |
| `AddTime` | datetime | 创建时间 |
| `ModifyTime` | datetime | 修改时间 |
| `AuditTime` | datetime | 审核时间 |

#### `Online_Package_Item`

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 主键 |
| `Package` | bigint fk | 关联 `Online_Package.Id` |
| `No` | varchar(30) | 箱号 / PackageNo |
| `Weight` | decimal(18,4) | 箱重 |
| `Extent` | decimal(18,4) | 长 |
| `Width` | decimal(18,4) | 宽 |
| `Height` | decimal(18,4) | 高 |
| `Quantity` | decimal(18,4) | 箱内总数量 |
| `SubTotal` | decimal(18,4) | 箱内申报金额 |
| `TrackNo` | varchar(80) | 箱级转单号，用于配载同步 |
| `Stowage` | bigint fk | 关联 `Stowage.Id`，0 表示未配载 |

#### `Online_Package_List`

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 主键 |
| `Item` | bigint fk | 关联 `Online_Package_Item.Id` |
| `ProductEN` | varchar(200) | 英文品名 |
| `ProductCN` | varchar(200) | 中文品名 |
| `Material` | varchar(200) | 材质 |
| `Tax` | decimal(18,4) | 税率 |
| `TaxAmount` | decimal(18,4) | 税额 |
| `HSCode` | varchar(30) | HS Code |
| `Weight` | decimal(18,4) | 商品重量 |
| `Price` | decimal(18,4) | 单价 |
| `Quantity` | decimal(18,4) | 数量 |
| `Note` | varchar(100) | 备注 |

新系统映射建议：`Online_Package_Item` 应成为 `cartons`，`Online_Package_List` 与 `Express_Item` 统一映射到 `declarations`，但保留来源字段区分“整票申报”和“箱内申报”。

### 计费、预扣和余额

#### `Express_Charge`

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 主键 |
| `Express` | bigint fk | 关联 `Express.Id` |
| `TheDate` | date | 费用日期 |
| `Customer` | bigint fk | 客户 |
| `Type` | int | 费用类型，1 运费，3 燃油，其它为附加费类型 |
| `Currency` | bigint fk | 币种 |
| `Amount` | decimal(18,4) | 原币费用 |
| `Paid` | decimal(18,4) | 预扣金额 |
| `CNY` | decimal(18,4) | 本位币金额 |
| `Audit` | int | 审核状态，API 预扣写 9999 |
| `AuditName` | varchar(50) | 审核说明 |
| `AuditTime` | datetime | 审核时间 |
| `AddId` | bigint | 创建人 ID |
| `AddName` | varchar(50) | 创建人 |
| `AddTime` | datetime | 创建时间 |
| `ModifyTime` | datetime | 修改时间 |

#### `Express_Charge_Status`

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 主键 |
| `Express` | bigint fk | 运单 |
| `Charge` | bigint fk | 费用行 |
| `Text` | varchar/text | 费用日志 |
| `AddName` | varchar(50) | 操作人 |
| `AddTime` | datetime | 时间 |

#### `Customer_Balance`

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 主键 |
| `Customer` | bigint fk | 客户 |
| `Currency` | bigint fk | 币种 |
| `Balance` | decimal(18,4) | 余额/欠款方向旧系统不直观 |
| `Credits` | decimal(18,4) | 授信 |
| `CNY` | decimal(18,4) | 本位币折算 |
| `ModifyTime` | datetime | 更新时间 |

旧 API 在余额查询时返回 `Balance * -1`，说明数据库余额方向和客户展示方向相反。新系统应改成不可变账本：`charges` 只是费用事实，客户余额通过 `ledger_entries` 或 `wallet_transactions` 计算，不建议继续直接更新余额字段。

#### `Customer_Balance_History`

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 主键 |
| `Customer` | bigint fk | 客户 |
| `Currency` | bigint fk | 币种 |
| `Amount` | decimal(18,4) | 本次变动 |
| `BeforeBalance` | decimal(18,4) | 变动前余额 |
| `AfterBalance` | decimal(18,4) | 变动后余额 |
| `Msg` | varchar(200) | 摘要 |
| `Time` | datetime | 时间 |

### 转单号、面单和状态

#### `Express_TrackNo`

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 主键 |
| `Express` | bigint fk | 运单 |
| `TrackNo` | varchar(80) | 转单号/子单号 |
| `AddName` | varchar(50) | 来源 |
| `AddTime` | datetime | 时间 |

#### `Online_TrackNo`

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 主键 |
| `Express` | bigint fk | 运单 |
| `TrackNo` | varchar(80) | 在线制单子单号 |

旧代码同时写 `Online_TrackNo`，查询时又出现 `Online_Trackno`，大小写不一致。迁移到 PostgreSQL 时必须统一命名。

#### `Online_File`

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 主键 |
| `Express` | bigint fk | 运单 |
| `Type` | int | 文件类型，换标接口按 0 查面单 |
| `TrackNo` | varchar(80) | 单号 |
| `Hash` | varchar(128) | 文件哈希 |
| `Ext` | varchar(20) | 扩展名 |
| `Time` | datetime | 生成时间 |

`getNewLabel.php` 使用小写 `online_file`，实际 MySQL 环境可能不区分大小写；新库必须统一。

#### `Express_Status`

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 主键 |
| `Express` | bigint fk | 运单 |
| `Text` | varchar/text | 操作日志，如 API 创建、API 提交、申请作废 |
| `AddName` | varchar(50) | 操作人 |
| `AddTime` | datetime | 时间 |

#### `Express_Process`

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 主键 |
| `Express` | bigint fk | 运单 |
| `Time` | datetime | 轨迹时间 |
| `Activity` | bigint fk | 关联 `Track_Item.Id` |
| `Location` | bigint fk | 关联 `Track_Location.Id` |
| `TrackNo` | bigint fk | 关联 `Express_TrackNo.Id` |

#### 轨迹字典

| 表 | 关键字段 | 用途 |
| --- | --- | --- |
| `Track_Item` | `Id`、`Name` | 轨迹动作字典 |
| `Track_Location` | `Id`、`Name` | 轨迹地点字典 |

### 作废、换单和外部辅助

| 表 | 关键字段 | 用途 |
| --- | --- | --- |
| `Online_Void` | `Id`、`Express`、`Customer`、`No`、`TrackNo`、`Status`、`AddName`、`AddTime` | API 作废申请 |
| `Change` | `oldNo`、`newNo`、`Status` | 换标旧单号到新单号 |
| `Change_No` | `oldNo`、`newNo`、`Status` | 另一套换单号表 |
| `Scale` | `Code` 等设备字段 | 称重设备接口查询 |

### 配载、转运和公开轨迹

#### `Stowage`

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `Id` | bigint pk | 主键 |
| `No` | varchar(30) | 配载单号 |
| `TheDate` | date | 日期 |
| `Customer` | bigint fk | 客户 |
| `Type` | int | 配载类型，API 限制 0/1 |
| `Category` | bigint fk | 关联 `Stowage_Category.Id` |
| `Count` | int | 票数 |
| `Piece` | int | 件数 |
| `Quantity` | decimal(18,4) | 数量 |
| `Weight` | decimal(18,4) | 重量 |
| `Volume` | decimal(18,4) | 体积 |
| `DeclaredValue` | decimal(18,4) | 申报金额 |
| `TaxAmount` | decimal(18,4) | 税额 |
| `DepartureTime` | date/datetime | 出发时间 |
| `DeparturePort` | bigint fk | 出发口岸 |
| `ArrivalTime` | date/datetime | 到达时间 |
| `ArrivalPort` | bigint fk | 到达口岸 |
| `Remark` | text | 备注 |
| `AddName` | varchar(50) | 创建人 |
| `AddTime` | datetime | 创建时间 |
| `ModifyTime` | datetime | 修改时间 |

| 表 | 关键字段 | 用途 |
| --- | --- | --- |
| `Stowage_Category` | `Id`、`Name` | 配载分类 |
| `Stowage_Port` | `Id`、`Name` | 配载口岸 |
| `Stowage_Process` | `Id`、`Stowage`、`Time`、`Location`、`Activity` | 配载轨迹 |
| `Shipment` | `Id`、`Type`、`Port`、`Airway` | 出运/提单批次，连接 Transit |
| `Shipment_Item` | `Id`、`Shipment`、`Express` | 出运批次与运单关系 |
| `Transit` | `Id` | 转运节点/提单节点 |
| `Transit_Process` | `Id`、`Transit`、`Time`、`Location`、`FlightNo`、`Activity` | 转运轨迹 |
| `Express_Weight` | `Id`、`Express` | 轨迹查询中用于重量/分票出运关联 |

## 新系统映射建议

| ACC 表 | 新 SaaS 建议表 | 说明 |
| --- | --- | --- |
| `Customer` | `customers` / `tenants` | ACC 是单公司多客户，新系统要加租户隔离 |
| `Customer_API` | `api_credentials` | 存哈希密钥、权限范围、限流，不存明文密钥 |
| `Product`、`Channel`、`Channel_Account` | `carriers`、`channels`、`service_products` | 拆清销售产品和实际渠道账号 |
| `Online` | `shipment_requests` 或 `orders` | 保留 API 原始请求与收件信息 |
| `Express` | `shipments` | 运单主表 |
| `Express_Item` | `declarations` | 整票申报 |
| `Online_Package_Item` | `cartons` | 箱级数据 |
| `Online_Package_List` | `declarations` | 箱内申报，带 `carton_id` |
| `Express_Charge` | `charges` | 应收费用行，保留规则快照 |
| `Customer_Balance*` | `ledger_entries` / `wallet_transactions` | 不直接加减余额，用流水计算余额 |
| `Express_TrackNo`、`Online_TrackNo` | `tracking_numbers` | 支持主单号、子单号、渠道单号分类 |
| `Express_Status` | `audit_logs` / `shipment_events` | 操作日志 |
| `Express_Process`、`Transit_Process`、`Stowage_Process` | `tracking_events` | 统一轨迹事件，保留来源类型 |
| `Stowage` | `ship_batches` / `stowages` | 批次/配载 |
| `Shipment`、`Shipment_Item`、`Transit` | `ship_batches`、`shipment_batch_links`、`transit_nodes` | 出运批次与转运节点 |
| `Online_File` | `documents` | 面单、POD、换标文件统一对象存储索引 |

## P0 最小建表范围

如果目标是先跑通 ACC API 兼容层，P0 至少需要：

- 客户认证：`Customer`、`Customer_API`
- 主数据：`Country`、`Channel`、`Channel_Account`、`Product`、`Currency`
- 订单：`Online`、`Express`、`Express_Item`
- 箱级：`Online_Package`、`Online_Package_Item`、`Online_Package_List`
- 计费：`Product_Price`、`Product_Item`、`Product_Zone`、`Zone_Country`、`Express_Charge`
- 余额：`Customer_Balance`、`Customer_Balance_History`
- 标签/单号：`Express_TrackNo`、`Online_TrackNo`、`Online_File`
- 状态/轨迹：`Express_Status`、`Express_Process`、`Track_Item`、`Track_Location`
- 作废：`Online_Void`

P1 再补：

- 配载/批次：`Stowage`、`Stowage_Category`、`Stowage_Port`、`Stowage_Process`
- 转运/提单：`Shipment`、`Shipment_Item`、`Transit`、`Transit_Process`、`Express_Weight`
- 发件人/售达方：`Online_Shipper`
- 换标兼容：`Change`、`Change_No`
- 称重设备：`Scale`

## 索引与约束建议

| 表 | 索引/约束 |
| --- | --- |
| `Customer_API` | `unique(APIID)`、`unique(APIKey)`、`index(Customer)` |
| `Product` | `unique(Code)`、`index(Channel, isOpen)` |
| `Channel_Account` | `unique(Code)`、`index(Product)` |
| `Country` | `unique(Code2)` |
| `Express` | `unique(Customer, No)`、`index(TrackNo)`、`index(Receipt)`、`index(Status)`、`index(ChannelAccount)` |
| `Online` | `index(Token)`、`index(OrderNo)` |
| `Express_Item` | `index(Express)` |
| `Online_Package` | `unique(Customer, No)`、`index(Express)` |
| `Online_Package_Item` | `index(Package)`、`index(TrackNo)`、`index(Stowage)` |
| `Online_Package_List` | `index(Item)` |
| `Express_Charge` | `index(Express, Type)`、`index(Customer, TheDate)`、`index(Audit)` |
| `Customer_Balance` | `unique(Customer, Currency)` |
| `Customer_Balance_History` | `index(Customer, Time)` |
| `Express_TrackNo` | `unique(Express, TrackNo)`、`index(TrackNo)` |
| `Online_TrackNo` | `unique(Express, TrackNo)`、`index(TrackNo)` |
| `Express_Process` | `index(Express, Time)`、`index(TrackNo)` |
| `Express_Status` | `index(Express, AddTime)` |
| `Online_Void` | `index(Express, Status)`、`index(Customer, AddTime)` |
| `Stowage` | `unique(Customer, No)`、`index(Category)` |
| `Stowage_Process` | `index(Stowage, Time)` |
| `Shipment_Item` | `index(Shipment)`、`index(Express)` |
| `Transit_Process` | `index(Transit, Time)` |
| `Online_File` | `index(Express, Type)`、`index(TrackNo)` |

## 迁移风险

1. `APIClass2.php` 签名校验被注释，不能作为新系统安全模型参考。
2. `Submit` 调用了不存在的 `doSubmit()`，真实提交逻辑在 `doCreate($SubmitType)`，接口兼容要按行为测试。
3. 表名大小写混用：`Online_TrackNo` / `Online_Trackno`、`online_file`、`express_trackno`。迁移 PostgreSQL 必须统一命名。
4. 余额方向混乱：数据库余额和 API 输出方向相反，新系统应改用账本流水。
5. `Express` 与 `Online` 是一对一但非强约束，旧代码用 `Express.Receipt = Online.Id`，需要补唯一约束或在新系统中明确 `shipment_request_id`。
6. `Express_Charge` 是费用事实和预扣状态混在一起，新系统应拆成 `charges`、`charge_audit_events`、`ledger_entries`。
7. API 会删除再重插 `Express_Item` 和装箱单明细，旧系统不是不可变历史。新系统如果需要审计，需要保留版本或事件日志。
8. 面单文件大多落在文件目录，`Online_File` 只保存哈希和扩展名。新系统应统一放对象存储并保存 `document_id`。
9. 配载同步按 `Online_Package_Item.TrackNo` 绑定箱级数据，若箱级转单号为空，会影响后续轨迹和配载。
10. 旧 API 直接用 `Customer` 维度隔离数据，新 SaaS 要增加 `tenant_id`，所有核心表都应有租户隔离字段和 RLS。

## 推荐落地方式

第一阶段不要重建完整 ACC 后台库，而是建立两层：

1. ACC 兼容层：保留旧接口参数、签名、状态码、字段名，把请求转换成新系统命令。
2. 新业务库：按 `shipments`、`cartons`、`declarations`、`charges`、`tracking_events`、`ledger_entries` 存储。

只有在必须支持旧插件或旧报表时，才补充 ACC shadow 表。shadow 表应由新业务事件异步投影生成，避免新系统继续依赖 ACC 的直接余额更新和删除重插模式。
