# 新智慧只读 API 抓取结果

抓取时间：2026-05-06

## 结论

本轮使用授权账号登录新智慧，只打开页面并记录页面自动发出的 XHR/fetch 请求，没有点击新增、保存、删除、审核、导入、导出、生成、同步等业务动作。

已完成两类抓取：

- 菜单结构抓取：抽取到 109 个菜单/动作节点。
- 全菜单页面抓取：访问 88 个可读页面，捕获 88 个自动加载端点。

原始抓取结果保存在本机临时目录：

- 菜单 JSON：`/tmp/xqt-xzh-crawl/output/2026-05-06T05-50-44-341Z`
- 全菜单接口：`/tmp/xqt-xzh-crawl/output/2026-05-06T07-26-26-618Z`
- 核心页面接口：`/tmp/xqt-xzh-crawl/output/2026-05-06T07-21-16-950Z`

输出中未保留账号密码值，只保留请求字段名和响应结构。

开发使用规则：

1. 本文档只作为新智慧页面和只读接口的对照证据，不作为新平台生产运行依赖。
2. 新平台目标接口必须使用 Spring Boot `/api/*`，响应统一为 `ApiResponse<T>`。
3. XQT 字段通过 `external_field_mappings` 做映射，不直接污染主业务模型。
4. 所有写接口必须按 Controller -> Service -> Repository 分层，并写入 token 操作人和审计日志。

## 请求规律

新智慧后台页面主要采用以下规则：

- 页面路径：`/tms/{domain}/{module}`
- 列表接口：`POST /rest/tms/{domain}/{module}/lists`
- 菜单接口：`GET /rest/tms/aos/common/menu`
- 常见列表请求字段：`scenes`、`timeLimit`
- 多数列表接口响应为 `application/json`
- 部分配置页或旧页面返回 `text/html`

其中：

- `csos` 更偏客户单据、运单、提单、货箱、用户、合同。
- `aos` 更偏财务、主数据、规则、系统配置。
- `dos` 更偏仓库作业。

## 核心数据流端点

| 业务域 | 页面 | API |
| --- | --- | --- |
| 运单 | `/tms/csos/shipment` | `POST /rest/tms/csos/shipment/lists` |
| 提单 | `/tms/csos/waybill` | `POST /rest/tms/csos/waybill/lists` |
| 货箱 | `/tms/csos/parcel` | `POST /rest/tms/csos/parcel/lists` |
| 快递单 | `/tms/csos/outer_shipment` | `POST /rest/tms/csos/outer_shipment/lists` |
| 保险单 | `/tms/csos/insure` | `POST /rest/tms/csos/insure/lists` |
| 轨迹跟踪 | `/tms/csos/trajectory_tracking` | `POST /rest/tms/csos/trajectory_tracking/lists` |
| 财务流水 | `/tms/aos/financial_detail` | `POST /rest/tms/aos/financial_detail/lists` |
| 运单审计 | `/tms/aos/shipment` | `POST /rest/tms/aos/shipment/lists` |
| 应收报表 | `/tms/aos/user_report` | `POST /rest/tms/aos/user_report/lists` |
| 应付报表 | `/tms/aos/partner_report` | `POST /rest/tms/aos/partner_report/lists` |
| 客户账单 | `/tms/aos/invoice` | `POST /rest/tms/aos/invoice/lists` |
| 供应商账单 | `/tms/aos/invoice_partner` | `POST /rest/tms/aos/invoice_partner/lists` |
| 运价维护 | `/tms/aos/rates` | `POST /rest/tms/aos/rates/lists` |
| 费用类型 | `/tms/aos/charge_type_mod` | `POST /rest/tms/aos/charge_type_mod/lists` |
| 仓库收货 | `/tms/dos/picklist` | `POST /rest/tms/dos/picklist/lists` |
| 仓库出货 | `/tms/dos/lading` | `POST /rest/tms/dos/lading/lists` |
| 托盘管理 | `/tms/dos/pallet` | `POST /rest/tms/dos/pallet/lists` |
| 小包装箱 | `/tms/dos/packing` | `POST /rest/tms/dos/packing/lists` |

## 全菜单列表端点

### 单据 / 客户侧

| 页面 | API | 状态 |
| --- | --- | --- |
| 运单 | `POST /rest/tms/csos/shipment/lists` | 200 |
| 提单 | `POST /rest/tms/csos/waybill/lists` | 200 |
| 工单 | `POST /rest/tms/csos/ticket/lists` | 200 |
| 快递单 | `POST /rest/tms/csos/outer_shipment/lists` | 200 |
| 保险单 | `POST /rest/tms/csos/insure/lists` | 200 |
| 预约取件 | `POST /rest/tms/csos/booking/lists` | 200 |
| 单证 | `POST /rest/tms/csos/declaration/lists` | 200 |
| 货箱 | `POST /rest/tms/csos/parcel/lists` | 待复探 |
| 邮包 | `POST /rest/tms/csos/packet/lists` | 200 |
| 查货集 | `POST /rest/tms/csos/aggregate/lists` | 200 |
| 产品库 | `POST /rest/tms/csos/product/lists` | 200 |
| POD 管理 | `POST /rest/tms/csos/pod/lists` | 200 |
| 轨迹跟踪 | `POST /rest/tms/csos/trajectory_tracking/lists` | 200 |

### 用户 / 合同

| 页面 | API | 状态 |
| --- | --- | --- |
| 用户 | `POST /rest/tms/csos/user/lists` | 200 |
| 用户等级 | `POST /rest/tms/csos/user_grade/lists` | 200 |
| 合同管理 | `POST /rest/tms/csos/contract/lists` | 200 |
| 用户查询 | `POST /rest/tms/csos/user_search/lists` | 200 |
| 结算方式 | `POST /rest/tms/csos/pay_type_mod/lists` | 200 |
| 客户 API 对接 | `POST /rest/tms/csos/user_api/lists` | 200 |

### 财务

| 页面 | API | 状态 |
| --- | --- | --- |
| 财务流水 | `POST /rest/tms/aos/financial_detail/lists` | 200 |
| 运单审计 | `POST /rest/tms/aos/shipment/lists` | 200 |
| 应收报表 | `POST /rest/tms/aos/user_report/lists` | 待复探 |
| 应付报表 | `POST /rest/tms/aos/partner_report/lists` | 200 |
| 运价维护 | `POST /rest/tms/aos/rates/lists` | 200 |
| 客户流水 | `POST /rest/tms/aos/invoice_detail/lists` | 200 |
| 客户账单 | `POST /rest/tms/aos/invoice/lists` | 200 |
| 供应商流水 | `POST /rest/tms/aos/detail_partner/lists` | 200 |
| 供应商账单 | `POST /rest/tms/aos/invoice_partner/lists` | 200 |
| 销售成本流水 | `POST /rest/tms/aos/detail_seller/lists` | 200 |
| 销售提成流水 | `POST /rest/tms/aos/detail_seller_commission/lists` | 200 |
| 销售提成单 | `POST /rest/tms/aos/invoice_seller_commission/lists` | 200 |
| 账户 | `POST /rest/tms/aos/financial_account/lists` | 200 |
| 账户流水 | `POST /rest/tms/aos/financial_account_record/lists` | 200 |
| 汇率 | `POST /rest/tms/aos/currency/lists` | 200 |
| 费用类型 | `POST /rest/tms/aos/charge_type_mod/lists` | 200 |
| 月结单 | `POST /rest/tms/aos/lock_invoice_time/lists` | 200 |
| 费用审批 | `POST /rest/tms/aos/charge_approval/lists` | 待复探 |
| 审批 | `POST /rest/tms/aos/approval/lists` | 200 |

### 仓库

| 页面 | API | 状态 |
| --- | --- | --- |
| 仓库收货 | `POST /rest/tms/dos/picklist/lists` | 200 |
| 仓库出货 | `POST /rest/tms/dos/lading/lists` | 200 |
| 托盘管理 | `POST /rest/tms/dos/pallet/lists` | 待复探 |
| 打托派送 | `POST /rest/tms/dos/pallet_delivery/lists` | 200 |
| 小包换标日志 | `POST /rest/tms/dos/b2c_scan_log/lists` | 待复探 |
| 小包装箱 | `POST /rest/tms/dos/packing/lists` | 200 |
| 常用标签 | `POST /rest/tms/dos/barcode/lists` | 200 |
| 拣货日志 | `POST /rest/tms/dos/pickuplog/lists` | 200 |

### 统计 / 主数据 / 系统

| 页面 | API | 状态 |
| --- | --- | --- |
| 货量统计新 | `POST /rest/tms/aos/new_statistics/lists` | 200 |
| 货量标签统计 | `POST /rest/tms/aos/statistics_label/lists` | 200 |
| 配货标签统计 | `POST /rest/tms/aos/statistics_label_dist/lists` | 200 |
| 服务 | `POST /rest/tms/aos/service/lists` | 200 |
| 供应商 | `POST /rest/tms/aos/company/lists` | 200 |
| 线路分类 | `POST /rest/tms/aos/line/lists` | 200 |
| 区域划分 | `POST /rest/tms/aos/zone/lists` | 200 |
| 货站/仓库 | `POST /rest/tms/aos/depot/lists` | 200 |
| 机场/港口 | `POST /rest/tms/aos/location/lists` | 200 |
| API 对接配置 | `POST /rest/tms/aos/api_account/lists` | 200 |
| API 数据映射 | `POST /rest/tms/aos/api_mapping/lists` | 200 |
| 快递分区 | `POST /rest/tms/aos/carrier_zone/lists` | 200 |
| 号段维护 | `POST /rest/tms/aos/segment_number/lists` | 200 |
| 地址库 | `POST /rest/tms/aos/addr_lib/lists` | 200 |
| 事件&规则 | `POST /rest/tms/aos/rule_match/lists` | 200 |
| 舱位线路 | `POST /rest/tms/aos/cabin_line/lists` | 200 |
| 舱位资源 | `POST /rest/tms/aos/cabin_resource/lists` | 200 |
| GPS 管理 | `POST /rest/tms/aos/gps/lists` | 200 |
| 超长超重 | `POST /rest/tms/aos/over_size/lists` | 200 |
| 系统设置 | `POST /rest/tms/aos/setting/base_form` | 200 |
| 模块设置 | `POST /rest/tms/aos/module/menu` | 200 |
| 操作日志 | `POST /rest/tms/aos/oplog/lists` | 200 |
| 后台任务 | `POST /rest/tms/aos/task/lists` | 200 |
| 数据模板 | `POST /rest/tms/aos/data_template/lists` | 200 |
| 打印模板 | `POST /rest/tms/aos/template/lists` | 200 |
| 快捷复制 | `POST /rest/tms/aos/quick_copy/lists` | 200 |
| 标识 | `POST /rest/tms/aos/tag/lists` | 200 |
| 电池标识 | `POST /rest/tms/aos/battery/lists` | 200 |
| 智能设备 | `POST /rest/tms/aos/device/lists` | 200 |
| 税金计算 | `POST /rest/tms/aos/taxes/lists` | 200 |
| 员工 | `POST /rest/tms/aos/staff/lists` | 200 |
| 组织架构 | `POST /rest/tms/aos/organization/lists` | 200 |
| 员工角色 | `POST /rest/tms/aos/staff_role/lists` | 200 |
| 运费计算器 | `POST /rest/tms/aos/calculator/lists` | 200 |
| 偏远查询 | `POST /rest/tms/aos/check_remote/lists` | 200 |
| 个人信息 | `POST /rest/tms/aos/profile/user` | 200 |

## 需要复探的端点

以下页面自动请求在页面切换时未捕获到完整响应，不代表接口不可用，建议后续单独加长等待或补筛选参数再测：

- `POST /rest/tms/aos/charge_approval/lists`
- `POST /rest/tms/aos/user_report/lists`
- `POST /rest/tms/csos/parcel/lists`
- `POST /rest/tms/dos/b2c_scan_log/lists`
- `POST /rest/tms/dos/pallet/lists`

## 对新系统的集成建议

第一批新智慧 adapter 应先接以下只读接口：

1. `shipment/lists`：订单/运单主数据。
2. `waybill/lists`：提单/批次。
3. `parcel/lists`：箱级/货箱。
4. `financial_detail/lists`：财务流水。
5. `invoice/lists`、`invoice_partner/lists`：客户/供应商账单。
6. `rates/lists`、`charge_type_mod/lists`：费率与费用字典。
7. `trajectory_tracking/lists`：轨迹跟踪。
8. `picklist/lists`、`lading/lists`：仓库收发货。

新系统落库时应把新智慧原始响应作为 `external_records.raw_json`，再投影到 `shipments`、`cartons`、`charges`、`tracking_events`、`customer_invoices`、`carrier_bill_lines`。
