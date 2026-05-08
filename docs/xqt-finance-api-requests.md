# 新智慧财务模块 POST 请求与 Body 清单

抓取/复探时间：2026-05-06

## 说明

本清单来自新智慧后台页面只读抓包和财务列表接口复探。过程只打开页面并请求列表接口，没有点击新增、保存、审核、删除、导入、导出、生成、同步等写入或触发业务动作。

本文档已经合并原来的接口清单和表单字段说明；每个 `POST` 请求都列出基础 body、可追加的查询字段，以及一份 body 模板。

开发使用规则：

1. 本文档记录的是新智慧外部接口证据，不作为新平台生产运行依赖。
2. 新平台对应能力必须沉淀到 `/api/finance/*`、`/api/seller/*` 等 Spring Boot 目标 API。
3. 目标 API 响应统一为 `ApiResponse<T>`，错误统一输出 `errorCode`。
4. 写接口必须按阿里巴巴开发规约分层：Controller -> Service -> Repository，并从 token 写入操作人和审计日志。

基础域名由环境变量或部署配置提供，文档中不固化真实第三方地址：

```text
XQT_BASE_URL
```

通用请求头：

```http
Accept: application/json
Content-Type: application/json;charset=utf-8
```

## Body 规则

所有已确认的财务列表页基础 body 都是：

```json
{
  "timeLimit": 0,
  "scenes": 1
}
```

需要筛选时，把对应模块表格里的 `body key` 作为顶层字段追加到 body。空值通常不传，或保持页面默认空值。

字段类型约定：`input` 用字符串；`number` 用数字；`select` 用选项 key/value；`treeSelect` 用节点 value；`dateRange` 通常用两个时间组成的数组；`group` 已展开为子字段。

## 总览

| 模块 | 页面 | POST API | 基础 body | 可追加字段数 | 状态 |
| --- | --- | --- | --- | ---: | --- |
| 财务流水 | `/tms/aos/financial_detail` | `/rest/tms/aos/financial_detail/lists` | `{"timeLimit":0,"scenes":1}` | 16 | HTTP 200 / success=1 |
| 运单审计 | `/tms/aos/shipment` | `/rest/tms/aos/shipment/lists` | `{"timeLimit":0,"scenes":1}` | 39 | HTTP 200 / success=1 |
| 应收报表 | `/tms/aos/user_report` | `/rest/tms/aos/user_report/lists` | `{"timeLimit":0,"scenes":1}` | 10 | HTTP 200 / success=1 |
| 应付报表 | `/tms/aos/partner_report` | `/rest/tms/aos/partner_report/lists` | `{"timeLimit":0,"scenes":1}` | 3 | HTTP 200 / success=1 |
| 运价维护 | `/tms/aos/rates` | `/rest/tms/aos/rates/lists` | `{"timeLimit":0,"scenes":1}` | 6 | HTTP 200 / success=1 |
| 客户流水 | `/tms/aos/invoice_detail` | `/rest/tms/aos/invoice_detail/lists` | `{"timeLimit":0,"scenes":1}` | 26 | HTTP 200 / success=1 |
| 客户账单 | `/tms/aos/invoice` | `/rest/tms/aos/invoice/lists` | `{"timeLimit":0,"scenes":1}` | 23 | HTTP 200 / success=1 |
| 供应商流水 | `/tms/aos/detail_partner` | `/rest/tms/aos/detail_partner/lists` | `{"timeLimit":0,"scenes":1}` | 23 | HTTP 200 / success=1 |
| 供应商账单 | `/tms/aos/invoice_partner` | `/rest/tms/aos/invoice_partner/lists` | `{"timeLimit":0,"scenes":1}` | 11 | HTTP 200 / success=1 |
| 销售成本流水 | `/tms/aos/detail_seller` | `/rest/tms/aos/detail_seller/lists` | `{"timeLimit":0,"scenes":1}` | 13 | HTTP 200 / success=1 |
| 销售提成流水 | `/tms/aos/detail_seller_commission` | `/rest/tms/aos/detail_seller_commission/lists` | `{"timeLimit":0,"scenes":1}` | 7 | HTTP 200 / success=1 |
| 销售提成单 | `/tms/aos/invoice_seller_commission` | `/rest/tms/aos/invoice_seller_commission/lists` | `{"timeLimit":0,"scenes":1}` | 3 | HTTP 200 / success=1 |
| 账户 | `/tms/aos/financial_account` | `/rest/tms/aos/financial_account/lists` | `{"timeLimit":0,"scenes":1}` | 4 | HTTP 200 / success=1 |
| 账户流水 | `/tms/aos/financial_account_record` | `/rest/tms/aos/financial_account_record/lists` | `{"timeLimit":0,"scenes":1}` | 7 | HTTP 200 / success=1 |
| 汇率 | `/tms/aos/currency` | `/rest/tms/aos/currency/lists` | `{"timeLimit":0,"scenes":1}` | 0 | HTTP 200 / success=1 |
| 费用类型 | `/tms/aos/charge_type_mod` | `/rest/tms/aos/charge_type_mod/lists` | `{"timeLimit":0,"scenes":1}` | 4 | HTTP 200 / success=1 |
| 月结单 | `/tms/aos/lock_invoice_time` | `/rest/tms/aos/lock_invoice_time/lists` | `{"timeLimit":0,"scenes":1}` | 6 | HTTP 200 / success=1 |
| 费用审批 | `/tms/aos/charge_approval` | `/rest/tms/aos/charge_approval/lists` | `{"timeLimit":0,"scenes":1}` | 5 | HTTP 200 / success=1 |
| 审批 | `/tms/aos/approval` | `/rest/tms/aos/approval/lists` | `{"timeLimit":0,"scenes":1}` | 5 | HTTP 200 / success=1 |

## 请求详情

### 财务流水

- 页面：`/tms/aos/financial_detail`
- 请求：`POST /rest/tms/aos/financial_detail/lists`
- 基础 body：`{"timeLimit":0,"scenes":1}`
- 复探状态：HTTP 200，success=1，返回成功
- 可追加 body keys：`keywords`, `serial_number`, `payment_type`, `audited`, `company_account_id`, `user_account_id`, `partner_account_id`, `staff_account_id`, `pay_time`, `created_daterange`, `audit_time`, `creator`, `invoiced`, `money`, `username`, `invoice_number`

Body 模板：

```json
{
  "timeLimit": 0,
  "scenes": 1,
  "keywords": "VALUE",
  "serial_number": "VALUE",
  "payment_type": "VALUE",
  "audited": "VALUE",
  "company_account_id": "VALUE",
  "user_account_id": "VALUE",
  "partner_account_id": "VALUE",
  "staff_account_id": "VALUE",
  "pay_time": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "created_daterange": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "audit_time": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "creator": "VALUE",
  "invoiced": "VALUE",
  "money": "0",
  "username": "VALUE",
  "invoice_number": "VALUE"
}
```

字段说明：

| 字段 | body key | 控件 | 值类型 | 选项/说明 |
| --- | --- | --- | --- | --- |
| 关键字 | `keywords` | input | string | 输入关键字模糊查询，多个用 “,” 隔开 |
| 流水号 | `serial_number` | input | string | 输入流水号精确查询 |
| 支付方式 | `payment_type` | select | string | bank_transfer:银行转账; alipay:支付宝; wechat:微信 |
| 审核状态 | `audited` | select | string | pending:待审核; passed:通过; rejected:退回; cancelled:取消 |
| 公司账户 | `company_account_id` | select | string | options 43 项，取 key |
| 用户账户 | `user_account_id` | select | string | options 887 项，取 key |
| 供应商账户 | `partner_account_id` | select | string | options 444 项，取 key |
| 员工账户 | `staff_account_id` | select | string | options 40 项，取 key |
| 支付时间 | `pay_time` | dateRange | array<string>，通常为 [start, end] |  |
| 创建时间 | `created_daterange` | dateRange | array<string>，通常为 [start, end] |  |
| 审核时间 | `audit_time` | dateRange | array<string>，通常为 [start, end] |  |
| 创建人 | `creator` | select | string | options 154 项，取 key |
| 是否已开票 | `invoiced` | select | string | 1:是; 0:否 |
| 金额 | `money` | input | string |  |
| 用户名 | `username` | select | string | options 999 项，取 key |
| 账单号 | `invoice_number` | input | string |  |

### 运单审计

- 页面：`/tms/aos/shipment`
- 请求：`POST /rest/tms/aos/shipment/lists`
- 基础 body：`{"timeLimit":0,"scenes":1}`
- 复探状态：HTTP 200，success=1，返回成功
- 可追加 body keys：`keywords`, `waybill_number`, `lading_number`, `service`, `username`, `user_grade`, `pay_type`, `country`, `to_warehouse_code`, `postcode`, `servicer_id`, `seller_id`, `finance_id`, `organization_id`, `partner_service`, `depot_id`, `pickup_depot_id`, `creator`, `created_daterange`, `picking_daterange`, `rates_daterange`, `delivered_daterange`, `ship_daterange`, `tag`, `tag_not`, `charge_audit`, `charge_paid`, `sell_charge_amount_start`, `sell_charge_amount_end`, `cost_charge_amount_start`, `cost_charge_amount_end`, `seller_profit_start`, `seller_profit_end`, `sell_profit_start`, `sell_profit_end`, `outer_carrier_code`, `config`, `vat_number`, `main_name`

Body 模板：

```json
{
  "timeLimit": 0,
  "scenes": 1,
  "keywords": "VALUE",
  "waybill_number": "VALUE",
  "lading_number": "VALUE",
  "service": [
    "VALUE"
  ],
  "username": [
    "VALUE"
  ],
  "user_grade": [
    "VALUE"
  ],
  "pay_type": [
    "VALUE"
  ],
  "country": [
    "VALUE"
  ],
  "to_warehouse_code": "VALUE",
  "postcode": "VALUE",
  "servicer_id": [
    "VALUE"
  ],
  "seller_id": [
    "VALUE"
  ],
  "finance_id": [
    "VALUE"
  ],
  "organization_id": [
    "VALUE"
  ],
  "partner_service": [
    "VALUE"
  ],
  "depot_id": [
    "VALUE"
  ],
  "pickup_depot_id": [
    "VALUE"
  ],
  "creator": [
    "VALUE"
  ],
  "created_daterange": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "picking_daterange": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "rates_daterange": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "delivered_daterange": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "ship_daterange": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "tag": [
    "VALUE"
  ],
  "tag_not": [
    "VALUE"
  ],
  "charge_audit": "VALUE",
  "charge_paid": [
    "VALUE"
  ],
  "sell_charge_amount_start": 0,
  "sell_charge_amount_end": 0,
  "cost_charge_amount_start": 0,
  "cost_charge_amount_end": 0,
  "seller_profit_start": 0,
  "seller_profit_end": 0,
  "sell_profit_start": 0,
  "sell_profit_end": 0,
  "outer_carrier_code": "VALUE",
  "config": "VALUE",
  "vat_number": "VALUE",
  "main_name": "VALUE"
}
```

字段说明：

| 字段 | body key | 控件 | 值类型 | 选项/说明 |
| --- | --- | --- | --- | --- |
| 关键词 | `keywords` | input | string | 输入单号查询，多个请用“,”隔开 |
| 提单号 | `waybill_number` | input | string | 输入提单号查询精准查询 |
| 出货单号 | `lading_number` | input | string | 输入出货单号查询精准查询 |
| 服务 | `service` | treeSelect | array<string> | options 49 项，取 value |
| 用户名 | `username` | select | array<string> | options 999 项，取 key |
| 用户等级 | `user_grade` | select | array<string> | options 12 项，取 key |
| 结算方式 | `pay_type` | select | array<string> | options 14 项，取 key |
| 国家 | `country` | select | array<string> | options 255 项，取 key |
| 地址编码 | `to_warehouse_code` | input | string | 多个请用“,”隔开 |
| 邮编 | `postcode` | input | string |  |
| 客服代表 | `servicer_id` | select | array<string> | options 53 项，取 key |
| 销售代表 | `seller_id` | select | array<string> | options 57 项，取 key |
| 财务代表 | `finance_id` | select | array<string> | options 14 项，取 key |
| 分公司 | `organization_id` | select | array<string> |  |
| 供应商服务 | `partner_service` | treeSelect | array<string> | options 44 项，取 value |
| 站点 | `depot_id` | select | array<string> | 5822efcb8a23ca13d00071a4:深圳集散中心; 58bca68f6f11db03cb52528e:义乌仓; 662c77854835886aca7c2f18:机场分公司 |
| 拣货站点 | `pickup_depot_id` | select | array<string> | 5822efcb8a23ca13d00071a4:深圳集散中心; 58bca68f6f11db03cb52528e:义乌仓; 662c77854835886aca7c2f18:机场分公司 |
| 创建人 | `creator` | select | array<string> | options 153 项，取 key |
| 创建时间 | `created_daterange` | dateRange | array<string>，通常为 [start, end] |  |
| 拣货时间 | `picking_daterange` | dateRange | array<string>，通常为 [start, end] |  |
| 计费时间 | `rates_daterange` | dateRange | array<string>，通常为 [start, end] |  |
| 签收时间 | `delivered_daterange` | dateRange | array<string>，通常为 [start, end] |  |
| 出货时间 | `ship_daterange` | dateRange | array<string>，通常为 [start, end] |  |
| 已标识 | `tag` | select | array<string> | options 33 项，取 key |
| 未标识 | `tag_not` | select | array<string> | options 33 项，取 key |
| 审计状态 | `charge_audit` | select | string | sell_pending:应收-待审计; sell_passed:应收-已审计; cost_pending:应付-待审计; cost_passed:应付-已审计; seller_pending:销售成本-待审计; seller_passed:销售成本-已审计; seller_commission_pending:销售提成-待审计; seller_commission_passed:销售提成-已审计 |
| 核销状态 | `charge_paid` | select | array<string> | sell_unpaid:应收-未核销; sell_paid:应收-已核销; cost_unpaid:应付-未核销; cost_paid:应付-已核销; seller_unpaid:销售成本-未核销; seller_paid:销售成本-已核销; seller_commission_unpaid:销售提成-未核销; seller_commission_paid:销售提成-已核销 |
| 应收金额 |  | group | 见子字段 |  |
| 应收金额/最小值 | `sell_charge_amount_start` | number | number | 最小金额 |
| 应收金额/最大值 | `sell_charge_amount_end` | number | number | 最大金额 |
| 应付金额 |  | group | 见子字段 |  |
| 应付金额/最小值 | `cost_charge_amount_start` | number | number | 最小金额 |
| 应付金额/最大值 | `cost_charge_amount_end` | number | number | 最大金额 |
| 销售利润 |  | group | 见子字段 |  |
| 销售利润/最小值 | `seller_profit_start` | number | number | 最小金额 |
| 销售利润/最大值 | `seller_profit_end` | number | number | 最大金额 |
| 毛利 |  | group | 见子字段 |  |
| 毛利/最小值 | `sell_profit_start` | number | number | 最小金额 |
| 毛利/最大值 | `sell_profit_end` | number | number | 最大金额 |
| 承运快递 | `outer_carrier_code` | input | string |  |
| 更多 | `config` | selectBlock | string | options 50 项，取 key |
| VAT号 | `vat_number` | input | string |  |
| 主品名 | `main_name` | input | string |  |

### 应收报表

- 页面：`/tms/aos/user_report`
- 请求：`POST /rest/tms/aos/user_report/lists`
- 基础 body：`{"timeLimit":0,"scenes":1}`
- 复探状态：HTTP 200，success=1，返回成功
- 可追加 body keys：`user`, `user_grade`, `servicer_id`, `seller_id`, `finance_id`, `currency`, `pay_type`, `daterange`, `min`, `max`

Body 模板：

```json
{
  "timeLimit": 0,
  "scenes": 1,
  "user": "VALUE",
  "user_grade": "VALUE",
  "servicer_id": "VALUE",
  "seller_id": "VALUE",
  "finance_id": "VALUE",
  "currency": "VALUE",
  "pay_type": "VALUE",
  "daterange": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "min": 0,
  "max": 0
}
```

字段说明：

| 字段 | body key | 控件 | 值类型 | 选项/说明 |
| --- | --- | --- | --- | --- |
| 用户 | `user` | select | string | options 999 项，取 key |
| 用户等级 | `user_grade` | select | string | options 12 项，取 key |
| 客服代表 | `servicer_id` | select | string | options 53 项，取 key |
| 销售代表 | `seller_id` | select | string | options 57 项，取 key |
| 财务代表 | `finance_id` | select | string | options 14 项，取 key |
| 币种 | `currency` | select | string | CNY:人民币; USD:美元; EUR:欧元; HKD:港币; GBP:英镑; CAD:加币 |
| 结算方式 | `pay_type` | select | string | options 14 项，取 key |
| 时间 | `daterange` | dateRange | array<string>，通常为 [start, end] |  |
| 金额搜索 |  | group | 见子字段 |  |
| 金额搜索/最小值 | `min` | number | number | 最小金额 |
| 金额搜索/最大值 | `max` | number | number | 最大金额 |

### 应付报表

- 页面：`/tms/aos/partner_report`
- 请求：`POST /rest/tms/aos/partner_report/lists`
- 基础 body：`{"timeLimit":0,"scenes":1}`
- 复探状态：HTTP 200，success=1，返回成功
- 可追加 body keys：`user`, `currency`, `daterange`

Body 模板：

```json
{
  "timeLimit": 0,
  "scenes": 1,
  "user": "VALUE",
  "currency": "VALUE",
  "daterange": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ]
}
```

字段说明：

| 字段 | body key | 控件 | 值类型 | 选项/说明 |
| --- | --- | --- | --- | --- |
| 用户 | `user` | select | string | options 304 项，取 key |
| 币种 | `currency` | select | string | CNY:人民币; USD:美元; EUR:欧元; HKD:港币; GBP:英镑; CAD:加币 |
| 时间 | `daterange` | dateRange | array<string>，通常为 [start, end] |  |

### 运价维护

- 页面：`/tms/aos/rates`
- 请求：`POST /rest/tms/aos/rates/lists`
- 基础 body：`{"timeLimit":0,"scenes":1}`
- 复探状态：HTTP 200，success=1，返回成功
- 可追加 body keys：`keywords`, `service_code`, `zone_id`, `user_grade_id`, `user_ids`, `status`

Body 模板：

```json
{
  "timeLimit": 0,
  "scenes": 1,
  "keywords": "VALUE",
  "service_code": "VALUE",
  "zone_id": "VALUE",
  "user_grade_id": "VALUE",
  "user_ids": "VALUE",
  "status": "VALUE"
}
```

字段说明：

| 字段 | body key | 控件 | 值类型 | 选项/说明 |
| --- | --- | --- | --- | --- |
| 关键字 | `keywords` | input | string | 输入关键字模糊查询，多个用 “,” 隔开 |
| 服务 | `service_code` | select | string | options 3201 项，取 value |
| 收货区域 | `zone_id` | select | string | 5c7dc9733aff975a327a8c57:深圳; 5c7dc98d3aff972f427cc19b:美国; 5c7dc9b93aff976a1e25ef9f:欧洲; 6628995848358829ed12886f:义乌; 663adc0748358878c2094c2b:机场分部 |
| 用户等级 | `user_grade_id` | select | string | options 12 项，取 value |
| 用户 | `user_ids` | select | string | options 999 项，取 key |
| 状态 | `status` | select | string | active:正常; blocked:停用 |

### 客户流水

- 页面：`/tms/aos/invoice_detail`
- 请求：`POST /rest/tms/aos/invoice_detail/lists`
- 基础 body：`{"timeLimit":0,"scenes":1}`
- 复探状态：HTTP 200，success=1，返回成功
- 可追加 body keys：`user_id`, `user_grade`, `user_seller_id`, `charge_type`, `invoice_time`, `keywords`, `detail_id`, `invoice_number`, `item_number`, `shipment_id`, `tracking_number`, `waybill_number`, `client_reference`, `audited`, `charge_start`, `charge_end`, `currency`, `paid`, `invoice`, `created`, `creator`, `tag`, `tag_not`, `service`, `pay_time`, `approver`

Body 模板：

```json
{
  "timeLimit": 0,
  "scenes": 1,
  "user_id": "VALUE",
  "user_grade": "VALUE",
  "user_seller_id": "VALUE",
  "charge_type": [
    "VALUE"
  ],
  "invoice_time": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "keywords": "VALUE",
  "detail_id": "VALUE",
  "invoice_number": "VALUE",
  "item_number": "VALUE",
  "shipment_id": "VALUE",
  "tracking_number": "VALUE",
  "waybill_number": "VALUE",
  "client_reference": "VALUE",
  "audited": "VALUE",
  "charge_start": 0,
  "charge_end": 0,
  "currency": "VALUE",
  "paid": "VALUE",
  "invoice": "VALUE",
  "created": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "creator": "VALUE",
  "tag": [
    "VALUE"
  ],
  "tag_not": [
    "VALUE"
  ],
  "service": [
    "VALUE"
  ],
  "pay_time": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "approver": "VALUE"
}
```

字段说明：

| 字段 | body key | 控件 | 值类型 | 选项/说明 |
| --- | --- | --- | --- | --- |
| 用户 | `user_id` | select | string | options 999 项，取 value |
| 用户等级 | `user_grade` | select | string | options 12 项，取 value |
| 用户销售代表 | `user_seller_id` | select | string | options 151 项，取 key |
| 费用类型 | `charge_type` | select | array<string> | options 569 项，取 value |
| 业务时间 | `invoice_time` | dateRange | array<string>，通常为 [start, end] |  |
| 关键字 | `keywords` | input | string | 输入关键字模糊查询，多个用“,”隔开 |
| 流水号 | `detail_id` | input | string | 输入流水号精确查询 |
| 账单号 | `invoice_number` | input | string | 输入账单号精确查询 |
| 关联单号 | `item_number` | input | string | 输入关联单号精确查询 |
| 运单号 | `shipment_id` | input | string | 输入运单号精确查询 |
| 转单号 | `tracking_number` | input | string | 输入转单号精确查询 |
| 提单号 | `waybill_number` | input | string | 输入提单号精确查询 |
| 客户单号 | `client_reference` | input | string | 输入客户单号精确查询 |
| 审计状态 | `audited` | select | string | 0:待审计; 1:已审计 |
| 费用 |  | group | 见子字段 |  |
| 费用/最小值 | `charge_start` | number | number | 最小金额 |
| 费用/最大值 | `charge_end` | number | number | 最大金额 |
| 币种 | `currency` | select | string | CNY:人民币; USD:美元; EUR:欧元; HKD:港币; GBP:英镑; CAD:加币 |
| 核销状态 | `paid` | select | string | 0:未核销; 1:已核销 |
| 是否放入账单 | `invoice` | select | string | 0:未入账单; 1:已入账单 |
| 创建时间 | `created` | dateRange | array<string>，通常为 [start, end] |  |
| 创建人 | `creator` | select | string | options 155 项，取 key |
| 已标识 | `tag` | select | array<string> |  |
| 未标识 | `tag_not` | select | array<string> |  |
| 服务 | `service` | treeSelect | array<string> | options 49 项，取 value |
| 核销时间 | `pay_time` | dateRange | array<string>，通常为 [start, end] |  |
| 审批人 | `approver` | select | string | options 151 项，取 key |

### 客户账单

- 页面：`/tms/aos/invoice`
- 请求：`POST /rest/tms/aos/invoice/lists`
- 基础 body：`{"timeLimit":0,"scenes":1}`
- 复探状态：HTTP 200，success=1，返回成功
- 可追加 body keys：`keywords`, `username`, `pay_type`, `user_grade`, `currency`, `money`, `daterange`, `seller_id`, `servicer_id`, `finance_id`, `organization_id`, `due_actions`, `tags`, `tags_not`, `pay_time`, `created`, `ship_time`, `creator`, `due_date`, `tax_date`, `fluent_charge_start`, `fluent_charge_end`, `invoice_confirm`

Body 模板：

```json
{
  "timeLimit": 0,
  "scenes": 1,
  "keywords": "VALUE",
  "username": "VALUE",
  "pay_type": "VALUE",
  "user_grade": "VALUE",
  "currency": "VALUE",
  "money": 0,
  "daterange": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "seller_id": "VALUE",
  "servicer_id": "VALUE",
  "finance_id": "VALUE",
  "organization_id": "VALUE",
  "due_actions": [
    "VALUE"
  ],
  "tags": [
    "VALUE"
  ],
  "tags_not": [
    "VALUE"
  ],
  "pay_time": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "created": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "ship_time": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "creator": "VALUE",
  "due_date": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "tax_date": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "fluent_charge_start": 0,
  "fluent_charge_end": 0,
  "invoice_confirm": "VALUE"
}
```

字段说明：

| 字段 | body key | 控件 | 值类型 | 选项/说明 |
| --- | --- | --- | --- | --- |
| 关键字 | `keywords` | input | string | 输入关键字模糊查询，多个用 “,” 隔开 |
| 用户名 | `username` | select | string | options 999 项，取 key |
| 结算方式 | `pay_type` | select | string | options 14 项，取 key |
| 用户等级 | `user_grade` | select | string | options 12 项，取 key |
| 币种 | `currency` | select | string | CNY:人民币; USD:美元; EUR:欧元; HKD:港币; GBP:英镑; CAD:加币 |
| 金额 | `money` | number | number |  |
| 账单时间 | `daterange` | dateRange | array<string>，通常为 [start, end] |  |
| 销售代表 | `seller_id` | select | string | options 58 项，取 key |
| 客服代表 | `servicer_id` | select | string | options 53 项，取 key |
| 财务代表 | `finance_id` | select | string | options 14 项，取 key |
| 所属分公司 | `organization_id` | select | string |  |
| 到期动作 | `due_actions` | select | array<string> | forbid_ship:出货控制; wos_hidden_tracking_number:隐藏转单; wos_forbid_login:登录控制; wos_forbid_order:下单控制 |
| 自定义标识 | `tags` | select | array<string> | 693696ebdf2c124ecf46d183:账期风险预警标签 |
| 未标识 | `tags_not` | select | array<string> | 693696ebdf2c124ecf46d183:账期风险预警标签 |
| 核销时间 | `pay_time` | dateRange | array<string>，通常为 [start, end] |  |
| 创建时间 | `created` | dateRange | array<string>，通常为 [start, end] |  |
| 出发时间 | `ship_time` | dateRange | array<string>，通常为 [start, end] |  |
| 创建人 | `creator` | select | string | options 153 项，取 key |
| 到期时间 | `due_date` | dateRange | array<string>，通常为 [start, end] |  |
| 开票日期 | `tax_date` | dateRange | array<string>，通常为 [start, end] |  |
| 冲账金额 |  | group | 见子字段 |  |
| 冲账金额/最小值 | `fluent_charge_start` | number | number | 最小金额 |
| 冲账金额/最大值 | `fluent_charge_end` | number | number | 最大金额 |
| 账单确认 | `invoice_confirm` | select | string | 1:是; 0:否 |

### 供应商流水

- 页面：`/tms/aos/detail_partner`
- 请求：`POST /rest/tms/aos/detail_partner/lists`
- 基础 body：`{"timeLimit":0,"scenes":1}`
- 复探状态：HTTP 200，success=1，返回成功
- 可追加 body keys：`user_id`, `charge_type`, `invoice_time`, `charge_start`, `charge_end`, `currency`, `keywords`, `detail_id`, `invoice_number`, `item_number`, `shipment_id`, `tracking_number`, `waybill_number`, `container_number`, `client_reference`, `audited`, `paid`, `invoice`, `created`, `creator`, `tag`, `tag_not`, `pay_time`

Body 模板：

```json
{
  "timeLimit": 0,
  "scenes": 1,
  "user_id": "VALUE",
  "charge_type": [
    "VALUE"
  ],
  "invoice_time": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "charge_start": 0,
  "charge_end": 0,
  "currency": "VALUE",
  "keywords": "VALUE",
  "detail_id": "VALUE",
  "invoice_number": "VALUE",
  "item_number": "VALUE",
  "shipment_id": "VALUE",
  "tracking_number": "VALUE",
  "waybill_number": "VALUE",
  "container_number": "VALUE",
  "client_reference": "VALUE",
  "audited": "VALUE",
  "paid": "VALUE",
  "invoice": "VALUE",
  "created": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "creator": "VALUE",
  "tag": [
    "VALUE"
  ],
  "tag_not": [
    "VALUE"
  ],
  "pay_time": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ]
}
```

字段说明：

| 字段 | body key | 控件 | 值类型 | 选项/说明 |
| --- | --- | --- | --- | --- |
| 供应商 | `user_id` | select | string | options 304 项，取 value |
| 费用类型 | `charge_type` | select | array<string> | options 569 项，取 value |
| 业务时间 | `invoice_time` | dateRange | array<string>，通常为 [start, end] |  |
| 费用 |  | group | 见子字段 |  |
| 费用/最小值 | `charge_start` | number | number | 最小金额 |
| 费用/最大值 | `charge_end` | number | number | 最大金额 |
| 币种 | `currency` | select | string | CNY:人民币; USD:美元; EUR:欧元; HKD:港币; GBP:英镑; CAD:加币 |
| 关键字 | `keywords` | input | string | 输入关键字模糊查询，多个用“,”隔开 |
| 流水号 | `detail_id` | input | string | 输入流水号精确查询 |
| 账单号 | `invoice_number` | input | string | 输入账单号精确查询 |
| 关联单号 | `item_number` | input | string | 输入关联单号精确查询 |
| 运单号 | `shipment_id` | input | string | 输入运单号精确查询 |
| 转单号 | `tracking_number` | input | string | 输入转单号精确查询 |
| 提单号 | `waybill_number` | input | string | 输入提单号精确查询，多个用“,”隔开 |
| 柜号 | `container_number` | input | string | 输入柜号精确查询，多个用“,”隔开 |
| 客户单号 | `client_reference` | input | string | 输入客户单号精确查询 |
| 审计状态 | `audited` | select | string | 0:待审计; 1:已审计 |
| 核销状态 | `paid` | select | string | 0:未核销; 1:已核销 |
| 入账单 | `invoice` | select | string | 0:未入账单; 1:已入账单 |
| 创建时间 | `created` | dateRange | array<string>，通常为 [start, end] |  |
| 创建人 | `creator` | select | string | options 157 项，取 key |
| 已标识 | `tag` | select | array<string> |  |
| 未标识 | `tag_not` | select | array<string> |  |
| 核销时间 | `pay_time` | dateRange | array<string>，通常为 [start, end] |  |

### 供应商账单

- 页面：`/tms/aos/invoice_partner`
- 请求：`POST /rest/tms/aos/invoice_partner/lists`
- 基础 body：`{"timeLimit":0,"scenes":1}`
- 复探状态：HTTP 200，success=1，返回成功
- 可追加 body keys：`keywords`, `username`, `currency`, `money`, `creator`, `tags`, `daterange`, `pay_time`, `created`, `ship_time`, `due_date`

Body 模板：

```json
{
  "timeLimit": 0,
  "scenes": 1,
  "keywords": "VALUE",
  "username": "VALUE",
  "currency": "VALUE",
  "money": 0,
  "creator": "VALUE",
  "tags": [
    "VALUE"
  ],
  "daterange": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "pay_time": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "created": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "ship_time": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "due_date": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ]
}
```

字段说明：

| 字段 | body key | 控件 | 值类型 | 选项/说明 |
| --- | --- | --- | --- | --- |
| 关键字 | `keywords` | input | string | 输入关键字模糊查询，多个用 “,” 隔开 |
| 供应商 | `username` | select | string | options 304 项，取 key |
| 币种 | `currency` | select | string | CNY:人民币; USD:美元; EUR:欧元; HKD:港币; GBP:英镑; CAD:加币 |
| 金额 | `money` | number | number |  |
| 创建人 | `creator` | select | string | options 153 项，取 key |
| 自定义标识 | `tags` | select | array<string> |  |
| 时间 | `daterange` | dateRange | array<string>，通常为 [start, end] |  |
| 核销时间 | `pay_time` | dateRange | array<string>，通常为 [start, end] |  |
| 创建时间 | `created` | dateRange | array<string>，通常为 [start, end] |  |
| 出发时间 | `ship_time` | dateRange | array<string>，通常为 [start, end] |  |
| 到期时间 | `due_date` | dateRange | array<string>，通常为 [start, end] |  |

### 销售成本流水

- 页面：`/tms/aos/detail_seller`
- 请求：`POST /rest/tms/aos/detail_seller/lists`
- 基础 body：`{"timeLimit":0,"scenes":1}`
- 复探状态：HTTP 200，success=1，返回成功
- 可追加 body keys：`kw`, `user_id`, `charge_type`, `invoice_time`, `currency`, `item_number`, `detail_id`, `shipment_id`, `tracking_number`, `waybill_number`, `audited`, `creator`, `created`

Body 模板：

```json
{
  "timeLimit": 0,
  "scenes": 1,
  "kw": "VALUE",
  "user_id": "VALUE",
  "charge_type": [
    "VALUE"
  ],
  "invoice_time": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "currency": "VALUE",
  "item_number": "VALUE",
  "detail_id": "VALUE",
  "shipment_id": "VALUE",
  "tracking_number": "VALUE",
  "waybill_number": "VALUE",
  "audited": "VALUE",
  "creator": "VALUE",
  "created": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ]
}
```

字段说明：

| 字段 | body key | 控件 | 值类型 | 选项/说明 |
| --- | --- | --- | --- | --- |
| 关键字 | `kw` | input | string | 输入关键字模糊查询，多个用“,”隔开 |
| 员工 | `user_id` | select | string | options 155 项，取 value |
| 费用类型 | `charge_type` | select | array<string> | options 569 项，取 value |
| 业务时间 | `invoice_time` | dateRange | array<string>，通常为 [start, end] |  |
| 币种 | `currency` | select | string | CNY:人民币; USD:美元; EUR:欧元; HKD:港币; GBP:英镑; CAD:加币 |
| 单号 | `item_number` | input | string | 输入单号精确查询 |
| 流水号 | `detail_id` | input | string | 输入流水号精确查询 |
| 运单号 | `shipment_id` | input | string | 输入运单号精确查询 |
| 转单号 | `tracking_number` | input | string | 输入转单号精确查询 |
| 提单号 | `waybill_number` | input | string | 输入提单号精确查询 |
| 审计状态 | `audited` | select | string | 0:待审计; 1:已审计 |
| 创建人 | `creator` | select | string | options 155 项，取 key |
| 创建时间 | `created` | dateRange | array<string>，通常为 [start, end] |  |

### 销售提成流水

- 页面：`/tms/aos/detail_seller_commission`
- 请求：`POST /rest/tms/aos/detail_seller_commission/lists`
- 基础 body：`{"timeLimit":0,"scenes":1}`
- 复探状态：HTTP 200，success=1，返回成功
- 可追加 body keys：`user_id`, `shipment_id`, `charge_type`, `creator`, `audited`, `created`, `invoice_time`

Body 模板：

```json
{
  "timeLimit": 0,
  "scenes": 1,
  "user_id": "VALUE",
  "shipment_id": "VALUE",
  "charge_type": [
    "VALUE"
  ],
  "creator": "VALUE",
  "audited": "VALUE",
  "created": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "invoice_time": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ]
}
```

字段说明：

| 字段 | body key | 控件 | 值类型 | 选项/说明 |
| --- | --- | --- | --- | --- |
| 销售 | `user_id` | select | string | options 153 项，取 key |
| 运单号 | `shipment_id` | input | string | 输入运单号精确查询 |
| 费用类型 | `charge_type` | select | array<string> | options 569 项，取 key |
| 创建人 | `creator` | select | string | options 153 项，取 key |
| 审计状态 | `audited` | select | string | 0:待审计; 1:已审计 |
| 创建时间 | `created` | dateRange | array<string>，通常为 [start, end] |  |
| 业务时间 | `invoice_time` | dateRange | array<string>，通常为 [start, end] |  |

### 销售提成单

- 页面：`/tms/aos/invoice_seller_commission`
- 请求：`POST /rest/tms/aos/invoice_seller_commission/lists`
- 基础 body：`{"timeLimit":0,"scenes":1}`
- 复探状态：HTTP 200，success=1，返回成功
- 可追加 body keys：`user_id`, `invoice_date`, `created`

Body 模板：

```json
{
  "timeLimit": 0,
  "scenes": 1,
  "user_id": "VALUE",
  "invoice_date": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "created": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ]
}
```

字段说明：

| 字段 | body key | 控件 | 值类型 | 选项/说明 |
| --- | --- | --- | --- | --- |
| 销售 | `user_id` | select | string | options 153 项，取 key |
| 账单日期 | `invoice_date` | dateRange | array<string>，通常为 [start, end] |  |
| 创建时间 | `created` | dateRange | array<string>，通常为 [start, end] |  |

### 账户

- 页面：`/tms/aos/financial_account`
- 请求：`POST /rest/tms/aos/financial_account/lists`
- 基础 body：`{"timeLimit":0,"scenes":1}`
- 复探状态：HTTP 200，success=1，返回成功
- 可追加 body keys：`keywords`, `username`, `user_grade`, `created_daterange`

Body 模板：

```json
{
  "timeLimit": 0,
  "scenes": 1,
  "keywords": "VALUE",
  "username": "VALUE",
  "user_grade": "VALUE",
  "created_daterange": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ]
}
```

字段说明：

| 字段 | body key | 控件 | 值类型 | 选项/说明 |
| --- | --- | --- | --- | --- |
| 账户 | `keywords` | input | string |  |
| 用户名 | `username` | select | string | options 999 项，取 key |
| 用户等级 | `user_grade` | select | string | options 12 项，取 key |
| 创建时间 | `created_daterange` | dateRange | array<string>，通常为 [start, end] |  |

### 账户流水

- 页面：`/tms/aos/financial_account_record`
- 请求：`POST /rest/tms/aos/financial_account_record/lists`
- 基础 body：`{"timeLimit":0,"scenes":1}`
- 复探状态：HTTP 200，success=1，返回成功
- 可追加 body keys：`id`, `account_id`, `pay_time`, `type`, `username`, `partner_id`, `created_daterange`

Body 模板：

```json
{
  "timeLimit": 0,
  "scenes": 1,
  "id": "VALUE",
  "account_id": "VALUE",
  "pay_time": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ],
  "type": "VALUE",
  "username": "VALUE",
  "partner_id": "VALUE",
  "created_daterange": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ]
}
```

字段说明：

| 字段 | body key | 控件 | 值类型 | 选项/说明 |
| --- | --- | --- | --- | --- |
| 流水号 | `id` | input | string | 输入流水号精准查询 |
| 账户 | `account_id` | select | string | options 1414 项，取 value |
| 支付时间 | `pay_time` | dateRange | array<string>，通常为 [start, end] |  |
| 类型 | `type` | select | string | options 22 项，取 value |
| 用户名 | `username` | select | string | options 999 项，取 key |
| 供应商 | `partner_id` | select | string | options 304 项，取 key |
| 创建时间 | `created_daterange` | dateRange | array<string>，通常为 [start, end] |  |

### 汇率

- 页面：`/tms/aos/currency`
- 请求：`POST /rest/tms/aos/currency/lists`
- 基础 body：`{"timeLimit":0,"scenes":1}`
- 复探状态：HTTP 200，success=1，返回成功
- 可追加 body keys：无搜索表单字段

Body 模板：

```json
{
  "timeLimit": 0,
  "scenes": 1
}
```

该接口未返回 `components.search.form.columns`，目前只确认基础 body。

### 费用类型

- 页面：`/tms/aos/charge_type_mod`
- 请求：`POST /rest/tms/aos/charge_type_mod/lists`
- 基础 body：`{"timeLimit":0,"scenes":1}`
- 复探状态：HTTP 200，success=1，返回成功
- 可追加 body keys：`name`, `code`, `type`, `is_show`

Body 模板：

```json
{
  "timeLimit": 0,
  "scenes": 1,
  "name": "VALUE",
  "code": "VALUE",
  "type": [
    "VALUE"
  ],
  "is_show": "VALUE"
}
```

字段说明：

| 字段 | body key | 控件 | 值类型 | 选项/说明 |
| --- | --- | --- | --- | --- |
| 名称 | `name` | input | string |  |
| 代码 | `code` | input | string |  |
| 适用类型 | `type` | select | array<string> | shipment:运单; lading:出货单; air_waybill:空运提单; sea_waybill:海运提单; railway_waybill:铁路提单; ground_waybill:陆运提单; express_waybill:快递提单; approval:审批; pallet:托盘; booking:预约取件 |
| 是否显示 | `is_show` | select | string | 0:否; 1:是 |

### 月结单

- 页面：`/tms/aos/lock_invoice_time`
- 请求：`POST /rest/tms/aos/lock_invoice_time/lists`
- 基础 body：`{"timeLimit":0,"scenes":1}`
- 复探状态：HTTP 200，success=1，返回成功
- 可追加 body keys：`sell`, `cost`, `seller`, `creator`, `_id`, `time`

Body 模板：

```json
{
  "timeLimit": 0,
  "scenes": 1,
  "sell": "VALUE",
  "cost": "VALUE",
  "seller": "VALUE",
  "creator": "VALUE",
  "_id": "VALUE",
  "time": "2026-05-01 00:00:00"
}
```

字段说明：

| 字段 | body key | 控件 | 值类型 | 选项/说明 |
| --- | --- | --- | --- | --- |
| 应收 | `sell` | select | string | 0:开启; 1:锁定 |
| 应付 | `cost` | select | string | 0:开启; 1:锁定 |
| 销售成本 | `seller` | select | string | 0:开启; 1:锁定 |
| 创建人 | `creator` | select | string | options 153 项，取 value |
| ID | `_id` | input | string |  |
| 时间 | `time` | date | string/date | 开始时间到结束时间内的时间 |

### 费用审批

- 页面：`/tms/aos/charge_approval`
- 请求：`POST /rest/tms/aos/charge_approval/lists`
- 基础 body：`{"timeLimit":0,"scenes":1}`
- 复探状态：HTTP 200，success=1，返回成功
- 可追加 body keys：`user_id`, `charge_type`, `serial_number`, `shipment_id`, `approval_time`

Body 模板：

```json
{
  "timeLimit": 0,
  "scenes": 1,
  "user_id": "VALUE",
  "charge_type": "VALUE",
  "serial_number": "VALUE",
  "shipment_id": "VALUE",
  "approval_time": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ]
}
```

字段说明：

| 字段 | body key | 控件 | 值类型 | 选项/说明 |
| --- | --- | --- | --- | --- |
| 用户 | `user_id` | select | string | options 1001 项，取 key |
| 费用类型 | `charge_type` | select | string | options 569 项，取 value |
| 流水号 | `serial_number` | input | string | 输入流水号精确查询 |
| 运单号 | `shipment_id` | input | string | 输入运单号精确查询 |
| 审批时间 | `approval_time` | dateRange | array<string>，通常为 [start, end] |  |

### 审批

- 页面：`/tms/aos/approval`
- 请求：`POST /rest/tms/aos/approval/lists`
- 基础 body：`{"timeLimit":0,"scenes":1}`
- 复探状态：HTTP 200，success=1，返回成功
- 可追加 body keys：`approval_number`, `number`, `type`, `detail_type`, `time`

Body 模板：

```json
{
  "timeLimit": 0,
  "scenes": 1,
  "approval_number": "VALUE",
  "number": "VALUE",
  "type": "VALUE",
  "detail_type": "VALUE",
  "time": [
    "2026-05-01 00:00:00",
    "2026-05-06 23:59:59"
  ]
}
```

字段说明：

| 字段 | body key | 控件 | 值类型 | 选项/说明 |
| --- | --- | --- | --- | --- |
| 审批编号 | `approval_number` | input | string |  |
| 关联单号 | `number` | input | string |  |
| 类型 | `type` | select | string | payment:付款; invoice_delay:账单延期; shipment_discount:运单优惠 |
| 费用类型 | `detail_type` | select | string | options 573 项，取 value |
| 发生时间 | `time` | dateRange | array<string>，通常为 [start, end] |  |

## 接入优先级

1. `financial_detail/lists`：财务流水，映射到外部流水、账本流水或费用明细。
2. `invoice/lists`：客户账单，映射到客户账单。
3. `invoice_partner/lists`：供应商账单，映射到供应商账单/账单明细。
4. `shipment/lists`：运单审计，补充运单费用、审计状态、核销状态。
5. `rates/lists`：运价维护，映射到价格表/价格规则。
6. `charge_type_mod/lists`：费用类型字典，映射到费用项目。
7. `financial_account/lists`、`financial_account_record/lists`：账户与账户流水，进入资金/账本模块。

## 注意事项

- 本文档只覆盖财务模块列表/查询类 POST，不覆盖新增、编辑、审核、删除、导入、导出、生成、同步等写操作。
- 大型下拉字段只记录 `options N 项，取 key/value`，真实选项来自接口响应的页面配置。
- Body 模板里的 `VALUE` 是占位符，接入时需要替换成页面配置中的 key/value 或真实查询值。
