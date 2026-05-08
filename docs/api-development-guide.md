# 新航线统一平台 API 开发文档

版本：2026-05-08
适用阶段：Spring Boot 主后端迁移 / 从零开发两套客户流程

## 1. API 定位

本平台 API 的目标不是长期转发 ACC 或新智慧接口，也不是复制旧系统路径，而是在新系统内建立自己的业务 API、数据模型、权限体系和审计体系。API 开发分成两个业务部分：

| 开发部分 | 客户方向 | 对照来源 | 目标 API |
| --- | --- | --- | --- |
| 第一部分：客户卖货 | `SELLER_CUSTOMER` | XQT 新智慧 | `/api/seller/*`, `/api/warehouse/*`, `/api/finance/*` |
| 第二部分：客户自己制单 | `DOCUMENT_CUSTOMER` | ACC | `/api/document/*`, `/api/customer-api/*`, `/api/labels/*`, `/api/tracking/*` |

外部系统接口只作为对照材料：

| 来源 | 用途 | 生产关系 |
| --- | --- | --- |
| XQT 新智慧页面/API | 对照客户卖货流程的页面、筛选、POST body、仓配和财务口径 | 不作为运行时依赖 |
| ACC 源码/API | 对照客户自己制单流程的下单、面单、轨迹、余额和费用逻辑 | 不作为运行时依赖 |
| Fastify 原型 API | 早期页面验证和对照 | 分批迁入 Spring Boot |
| Spring Boot API | 新系统正式主后端 | 目标生产 API |

开发原则：

1. 前端只依赖新系统 `/api/*`，不直接依赖 XQT 或 ACC。
2. XQT 请求只用于客户卖货流程的只读对照，不作为运行时业务依赖。
3. ACC 源码/API 只用于客户自己制单流程的规则对照，不作为生产兼容接口目标。
4. ACC/XQT 字段通过 `external_*` 映射层进入系统，不直接污染主模型。
5. 所有客户、订单、运单、财务查询必须支持 `customerDirection`，区分卖货客户和制单客户。
6. 写接口必须具备权限、幂等、审计和错误追踪。
7. Controller 只负责 HTTP 入参、鉴权主体获取和响应包装，不直接持有 `JdbcTemplate`，不直接写 SQL。
8. 所有正式接口统一返回 `ApiResponse<T>`，错误由 `GlobalExceptionHandler` 输出 `errorCode`。
9. 写接口必须从 token 获取 `tenantId`、`userId` 写入操作人字段，不接受前端传入操作人。
10. 后端提交前必须通过 `./mvnw verify`，包含测试、Checkstyle、P3C/PMD、SpotBugs。

## 2. 环境与代理

| 服务 | 本地地址 | 说明 |
| --- | --- | --- |
| Web | `http://localhost:5173` | 统一前端入口 |
| Spring Boot API | `http://localhost:18103` | 目标主 API |
| Fastify API | `http://localhost:18080` | 当前原型 API |
| PostgreSQL | `localhost:5432` 或 Docker `15432` | 主库 |

前端代理规则：

| 前端请求 | 代理到 | 当前说明 |
| --- | --- | --- |
| `/api/auth/*` | Spring Boot | 已接管 |
| `/api/admin/*` | Spring Boot | 已接管 |
| `/api/health` | Spring Boot | 已接管 |
| `/api/*` | Fastify | 业务原型，逐步迁移 |
| `/health` | Fastify | 原型健康检查 |

生产目标：

```text
Web -> API Gateway / Nginx -> Spring Boot -> PostgreSQL / Redis
```

Fastify 只保留在开发和迁移阶段。

## 3. 通用协议

### 3.1 认证

除登录、健康检查外，所有正式 API 使用 Bearer Token。

```http
Authorization: Bearer <token>
```

登录接口返回：

```json
{
  "ok": true,
  "data": {
    "ok": true,
    "token": "<token>",
    "expiresIn": 28800,
    "user": {
      "userId": "...",
      "tenantId": "...",
      "username": "admin",
      "displayName": "系统管理员",
      "tenantCode": "xqt",
      "roles": ["admin"],
      "permissions": ["admin.user.read"],
      "exp": 1778106742,
      "jti": "..."
    }
  },
  "error": null,
  "errorCode": null
}
```

### 3.2 响应格式

所有 Spring Boot 正式 API 统一响应包：

```json
{
  "ok": true,
  "data": {},
  "error": null,
  "errorCode": null
}
```

列表：

```json
{
  "ok": true,
  "data": {
    "items": [],
    "page": 1,
    "pageSize": 20
  },
  "error": null,
  "errorCode": null
}
```

错误：

```json
{
  "ok": false,
  "data": null,
  "error": "请求参数不合法",
  "errorCode": "VALIDATION_FAILED"
}
```

`data` 里必须是明确 DTO：列表使用 `ListResponse<T>`，分页使用 `PageResponse<T>`，单对象使用 `ItemResponse<T>`，命令类使用 `CommandResponse`。不允许 Controller 公开返回裸 `Map`。

### 3.3 分页和排序

查询类 POST 统一使用：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {},
  "sorts": [
    {
      "field": "createdAt",
      "direction": "desc"
    }
  ]
}
```

涉及客户、订单、运单、财务的查询，`filters` 中应包含：

```json
{
  "filters": {
    "customerDirection": "SELLER_CUSTOMER",
    "sourceSystem": "XQT",
    "serviceMode": "SELLER_FULFILLMENT"
  }
}
```

`customerDirection` 可选值：

| 值 | 说明 |
| --- | --- |
| `SELLER_CUSTOMER` | 卖货客户 |
| `DOCUMENT_CUSTOMER` | 制单客户 |
| `BOTH` | 两类业务都支持 |

兼容 GET 列表时使用：

```http
GET /api/resource?pageNum=1&pageSize=20&keyword=xxx
```

### 3.4 幂等

涉及写入、金额、库存、账单、核销、导入、批量动作的接口必须支持幂等键。

```http
Idempotency-Key: <client-generated-key>
```

后端落库到幂等表，重复请求返回同一业务结果。

### 3.5 错误码

| code | 场景 |
| --- | --- |
| `UNAUTHORIZED` | 未登录或 token 无效 |
| `FORBIDDEN` | 无权限 |
| `VALIDATION_ERROR` | 参数校验失败 |
| `NOT_FOUND` | 资源不存在 |
| `CONFLICT` | 状态冲突、重复提交 |
| `IDEMPOTENCY_CONFLICT` | 幂等键请求体不一致 |
| `EXTERNAL_MAPPING_MISSING` | 外部字段映射缺失 |
| `BUSINESS_RULE_FAILED` | 业务规则不通过 |
| `DATABASE_ERROR` | 数据库异常 |

## 4. 已实现 Spring Boot API

### 4.1 健康检查

```http
GET /api/health
```

响应：

```json
{
  "ok": true,
  "service": "xqt-backend",
  "database": "ok",
  "time": "2026-05-06T21:54:48+08:00"
}
```

### 4.2 登录

```http
POST /api/auth/login
Content-Type: application/json
```

请求 body：

```json
{
  "tenantCode": "xqt",
  "username": "admin",
  "password": "<password>"
}
```

### 4.3 当前用户

```http
GET /api/auth/me
Authorization: Bearer <token>
```

### 4.4 退出登录

```http
POST /api/auth/logout
Authorization: Bearer <token>
```

### 4.5 系统管理

| 方法 | 路径 | 权限建议 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/admin/users` | `admin.user.read` | 用户列表 |
| `POST` | `/api/admin/users` | `admin.user.write` | 创建用户，写入操作人和审计 |
| `PUT` | `/api/admin/users/{id}` | `admin.user.write` | 更新用户资料、状态、角色、密码 |
| `DELETE` | `/api/admin/users/{id}` | `admin.user.write` | 禁用并软删除用户 |
| `GET` | `/api/admin/roles` | `admin.role.read` | 角色列表 |
| `POST` | `/api/admin/roles` | `admin.role.write` | 创建角色并分配权限 |
| `PUT` | `/api/admin/roles/{id}` | `admin.role.write` | 更新角色 |
| `PUT` | `/api/admin/roles/{id}/permissions` | `admin.role.write` | 重置角色权限点 |
| `DELETE` | `/api/admin/roles/{id}` | `admin.role.write` | 归档非系统角色 |
| `GET` | `/api/admin/permissions` | `admin.permission.read` | 权限列表 |
| `GET` | `/api/admin/audit-logs` | `admin.audit.read` | 操作审计日志 |

所有通用 CRUD 写接口必须从 token 获取 `tenantId` 和 `userId`，不接受前端伪造操作人；业务表写入 `created_by`、`updated_by`、`deleted_by`，并同步写 `audit_logs` 的实体、动作、操作人、before/after JSON。

## 5. Spring Boot 目标 API 分组

| 模块 | 路径 | 优先级 | 说明 |
| --- | --- | --- | --- |
| 业务流程 | `/api/business-flows/*` | P0 | 卖货流程、制单流程、服务模式 |
| 认证 | `/api/auth/*` | P0 | 已实现，继续增强 |
| 系统管理 | `/api/admin/*` | P0 | 用户、角色、权限、菜单、日志 |
| 主数据 | `/api/master-data/*` | P0 | 客户、供应商、服务、币种、费用类型 |
| 客户卖货 | `/api/seller/*` | P0 | 对照 XQT 的卖货订单、仓配、利润、售后 |
| 客户自己制单 | `/api/document/*` | P0 | 对照 ACC 的制单、取号、面单、轨迹、余额 |
| 客户 API | `/api/customer-api/*` | P0 | 制单客户自助/API 下单、查价、查轨迹 |
| 标签面单 | `/api/labels/*` | P0 | 面单生成、换标、文件下载 |
| 财务 | `/api/finance/*` | P0 | 两套流程共用财务中心，按 `customerDirection` 区分 |
| 外部对照 | `/api/external/*` | P0 | 系统、模块、字段映射、对照用例 |
| 订单 | `/api/orders/*` | P1 | 两套流程共用订单视图 |
| 运单 | `/api/shipments/*` | P1 | 运单、轨迹、审计 |
| 仓库 | `/api/warehouse/*` | P1 | 卖货流程入库、出库、装箱、托盘 |
| 对账 | `/api/reconciliation/*` | P1 | 差异、核销、调账 |
| 账本 | `/api/ledger/*` | P1 | 过账、分录、凭证 |

### 5.1 业务流程、卖货/制单、服务模式如何关联查看

两份核心文档的阅读方式：

| 文档 | 看什么 | 和 API 的关系 |
| --- | --- | --- |
| `docs/platform-capability-coverage-framework-research.md` | 功能范围、优先级、ACC/XQT 覆盖矩阵 | 先确认某个功能属于卖货流程、制单流程还是 SaaS 通用模块 |
| `docs/api-development-guide.md` | 接口路径、request body、response、验收要求 | 再回到本文件查具体接口和 body |

业务方向和服务模式的对应关系：

| 客户方向 | 服务模式 | 流程配置来源 | 订单接口路径 | 对照来源 |
| --- | --- | --- | --- | --- |
| `SELLER_CUSTOMER` | `SELLER_FULFILLMENT` | `GET /api/business-flows` 返回的卖货流程 | `/api/seller/orders/*` | XQT 新智慧卖货页面 |
| `DOCUMENT_CUSTOMER` | `DOCUMENT_SHIPPING` | `GET /api/business-flows` 返回的制单流程 | `/api/document/orders/*` | ACC 制单能力 |

当前已实现的 Spring Boot 订单接口中，`customerDirection` 和 `serviceMode` 不需要前端在 body 里传：

| 路径 | 后端自动写入/筛选的方向 |
| --- | --- |
| `/api/seller/orders/search`, `/api/seller/orders` | `customerDirection=SELLER_CUSTOMER`, `serviceMode=SELLER_FULFILLMENT` |
| `/api/document/orders/search`, `/api/document/orders` | `customerDirection=DOCUMENT_CUSTOMER`, `serviceMode=DOCUMENT_SHIPPING` |

也就是说：

1. 先调 `GET /api/business-flows`，拿到系统支持的流程配置。
2. 卖货业务进入 `/api/seller/*`，制单业务进入 `/api/document/*`。
3. 后续通用财务、仓库、报表类接口如果共用 `/api/finance/*`、`/api/warehouse/*`，再在 `filters.customerDirection` 和 `filters.serviceMode` 中区分业务方向。

### 5.2 业务流程接口 body

`GET /api/business-flows` 没有 request body。

请求：

```http
GET /api/business-flows
Authorization: Bearer <token>
```

响应示例：

```json
{
  "ok": true,
  "data": {
    "items": [
      {
        "flowCode": "SELLER_FULFILLMENT",
        "customerDirection": "SELLER_CUSTOMER",
        "name": "卖货客户履约流程",
        "entryChannels": ["sales_order", "warehouse_receipt", "manual_order"],
        "defaultSteps": [
          "quote",
          "order",
          "warehouse_in",
          "pick_pack",
          "ship",
          "track",
          "customer_invoice",
          "partner_reconcile",
          "profit_review"
        ],
        "settlementModel": "AR_AP_PROFIT",
        "active": true
      },
      {
        "flowCode": "DOCUMENT_SHIPPING",
        "customerDirection": "DOCUMENT_CUSTOMER",
        "name": "制单客户发货流程",
        "entryChannels": ["api_order", "batch_import", "manual_document", "customer_portal"],
        "defaultSteps": [
          "rate_quote",
          "order_validate",
          "label_create",
          "freight_deduct",
          "ship",
          "track",
          "customer_statement",
          "balance_reconcile"
        ],
        "settlementModel": "PREPAID_OR_MONTHLY",
        "active": true
      }
    ]
  },
  "error": null
}
```

### 5.3 当前已实现的卖货/制单订单 body

当前代码已实现的查询 body 是轻量版，字段直接放在 body 顶层：

```json
{
  "page": 1,
  "pageSize": 20,
  "keyword": "",
  "customerCode": "SELLER-DEMO",
  "status": "DRAFT"
}
```

创建卖货订单：

```http
POST /api/seller/orders
```

```json
{
  "customerCode": "SELLER-DEMO",
  "customerRef": "SELLER-REF-001",
  "status": "DRAFT",
  "orderEntryType": "SALES_ORDER",
  "metadata": {
    "sourcePage": "seller_order",
    "remark": "卖货流程样例"
  },
  "lines": [
    {
      "lineNo": 1,
      "itemName": "Sample Product",
      "sku": "SKU-001",
      "quantity": 1,
      "declaredValue": 10,
      "declaredCurrency": "USD",
      "weightKg": 1.2,
      "metadata": {
        "cartonNo": "CTN001"
      }
    }
  ]
}
```

创建制单订单：

```http
POST /api/document/orders
```

```json
{
  "customerCode": "DOC-DEMO",
  "customerRef": "DOC-REF-001",
  "status": "DRAFT",
  "orderEntryType": "MANUAL_DOCUMENT",
  "metadata": {
    "sourcePage": "document_order",
    "receiverCountry": "US",
    "postcode": "90001"
  },
  "lines": [
    {
      "lineNo": 1,
      "itemName": "Document Item",
      "sku": "DOC-SKU-001",
      "quantity": 1,
      "declaredValue": 20,
      "declaredCurrency": "USD",
      "weightKg": 0.8
    }
  ]
}
```

当前已实现接口的入参字段来自 `OrderRequests.Search` 和 `OrderRequests.Save`；目标规范化查询 body 仍按后续章节的 `pageNum/pageSize/filters/sorts` 结构推进。

## 6. 第一部分：客户卖货 API，对照 XQT

### 6.1 开发边界

客户卖货流程从零开发，XQT 只作为页面、筛选、字段和财务口径对照。开发时分两层：

| 层 | 路径 | 用途 |
| --- | --- | --- |
| 标准业务 API | `/api/seller/*`, `/api/warehouse/*`, `/api/finance/*` | 前端正式依赖，使用主系统标准字段 |
| XQT 对照 API | `/api/external/xqt/aos/*` | 可选，用于接收 XQT 同形 body，做字段对照 |

XQT 已抓取到的是页面列表接口，不是未来生产接口。所有 XQT 财务列表接口的基础 body：

```json
{
  "timeLimit": 0,
  "scenes": 1
}
```

筛选字段作为顶层字段追加，空值通常不传。

### 6.2 卖货流程标准查询 body

正式 `/api/seller/*/search`、`/api/warehouse/*/search`、`/api/finance/*/search` 统一使用：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "SELLER_CUSTOMER",
    "serviceMode": "SELLER_FULFILLMENT",
    "keyword": "ABC",
    "dateRange": ["2026-05-01", "2026-05-31"]
  },
  "sorts": [
    {
      "field": "createdAt",
      "direction": "desc"
    }
  ],
  "includeExternalFields": true
}
```

返回数据必须包含主系统字段；当 `includeExternalFields=true` 时可追加：

```json
{
  "external": {
    "system": "XQT",
    "module": "financial_detail",
    "rawFields": {}
  }
}
```

### 6.3 卖货流程 API 总表

| 新系统接口 | XQT 对照页面 | XQT POST API/字段来源 | 优先级 |
| --- | --- | --- | --- |
| `POST /api/seller/orders/search` | 运单审计 | `/rest/tms/aos/shipment/lists` | P0 |
| `POST /api/seller/orders` | 运单/订单表单 | 页面字段人工对照 | P0 |
| `GET /api/seller/orders/{id}` | 运单/订单详情 | 页面字段人工对照 | P0 |
| `PUT /api/seller/orders/{id}` | 运单/订单编辑 | 页面字段人工对照 | P0 |
| `DELETE /api/seller/orders/{id}` | 订单取消/软删除 | 页面字段人工对照 | P0 |
| `POST /api/seller/shipments/search` | 运单审计 | `/rest/tms/aos/shipment/lists` | P0 |
| `POST /api/warehouse/receipts/search` | 仓配/入库页面 | 页面字段人工对照 | P1 |
| `POST /api/warehouse/picklists/search` | 仓配/出库页面 | 页面字段人工对照 | P1 |
| `POST /api/finance/ledger-records/search` | 财务流水 | `/rest/tms/aos/financial_detail/lists` | P0 |
| `POST /api/finance/shipment-audits/search` | 运单审计 | `/rest/tms/aos/shipment/lists` | P0 |
| `POST /api/finance/receivable-reports/search` | 应收报表 | `/rest/tms/aos/user_report/lists` | P1 |
| `POST /api/finance/payable-reports/search` | 应付报表 | `/rest/tms/aos/partner_report/lists` | P1 |
| `POST /api/finance/rates/search` | 运价维护 | `/rest/tms/aos/rates/lists` | P1 |
| `POST /api/finance/customer-charge-details/search` | 客户流水 | `/rest/tms/aos/invoice_detail/lists` | P0 |
| `POST /api/finance/customer-invoices/search` | 客户账单 | `/rest/tms/aos/invoice/lists` | P0 |
| `POST /api/finance/partner-charge-details/search` | 供应商流水 | `/rest/tms/aos/detail_partner/lists` | P0 |
| `POST /api/finance/partner-invoices/search` | 供应商账单 | `/rest/tms/aos/invoice_partner/lists` | P0 |
| `POST /api/finance/seller-cost-details/search` | 销售成本流水 | `/rest/tms/aos/detail_seller/lists` | P1 |
| `POST /api/finance/seller-commission-details/search` | 销售提成流水 | `/rest/tms/aos/detail_seller_commission/lists` | P1 |
| `POST /api/finance/seller-commission-invoices/search` | 销售提成单 | `/rest/tms/aos/invoice_seller_commission/lists` | P1 |
| `POST /api/finance/accounts/search` | 账户 | `/rest/tms/aos/financial_account/lists` | P0 |
| `POST /api/finance/account-records/search` | 账户流水 | `/rest/tms/aos/financial_account_record/lists` | P0 |
| `POST /api/finance/currencies/search` | 汇率 | `/rest/tms/aos/currency/lists` | P1 |
| `POST /api/finance/charge-types/search` | 费用类型 | `/rest/tms/aos/charge_type_mod/lists` | P0 |
| `POST /api/finance/monthly-locks/search` | 月结单 | `/rest/tms/aos/lock_invoice_time/lists` | P1 |
| `POST /api/finance/charge-approvals/search` | 费用审批 | `/rest/tms/aos/charge_approval/lists` | P1 |
| `POST /api/finance/approvals/search` | 审批 | `/rest/tms/aos/approval/lists` | P1 |

上表里的财务接口继续使用 `/api/finance/*`，但必须在请求中带 `customerDirection=SELLER_CUSTOMER` 或由客户档案自动推导。

### 6.4 XQT 已抓取 body key

下表来自只读抓取结果。开发时不要直接把这些字段全部做成主系统字段；先进入 `external_field_mappings`，确认口径后再映射到标准字段。

| 模块 | 外部 API | 基础 body | 可追加 body keys |
| --- | --- | --- | --- |
| 财务流水 | `/rest/tms/aos/financial_detail/lists` | `{"timeLimit":0,"scenes":1}` | `keywords`, `serial_number`, `payment_type`, `audited`, `company_account_id`, `user_account_id`, `partner_account_id`, `staff_account_id`, `pay_time`, `created_daterange`, `audit_time`, `creator`, `invoiced`, `money`, `username`, `invoice_number` |
| 运单审计 | `/rest/tms/aos/shipment/lists` | `{"timeLimit":0,"scenes":1}` | `keywords`, `waybill_number`, `lading_number`, `service`, `username`, `user_grade`, `pay_type`, `country`, `to_warehouse_code`, `postcode`, `servicer_id`, `seller_id`, `finance_id`, `organization_id`, `partner_service`, `depot_id`, `pickup_depot_id`, `creator`, `created_daterange`, `picking_daterange`, `rates_daterange`, `delivered_daterange`, `ship_daterange`, `tag`, `tag_not`, `charge_audit`, `charge_paid`, `sell_charge_amount_start`, `sell_charge_amount_end`, `cost_charge_amount_start`, `cost_charge_amount_end`, `seller_profit_start`, `seller_profit_end`, `sell_profit_start`, `sell_profit_end`, `outer_carrier_code`, `config`, `vat_number`, `main_name` |
| 应收报表 | `/rest/tms/aos/user_report/lists` | `{"timeLimit":0,"scenes":1}` | `user`, `user_grade`, `servicer_id`, `seller_id`, `finance_id`, `currency`, `pay_type`, `daterange`, `min`, `max` |
| 应付报表 | `/rest/tms/aos/partner_report/lists` | `{"timeLimit":0,"scenes":1}` | `user`, `currency`, `daterange` |
| 运价维护 | `/rest/tms/aos/rates/lists` | `{"timeLimit":0,"scenes":1}` | `keywords`, `service_code`, `zone_id`, `user_grade_id`, `user_ids`, `status` |
| 客户流水 | `/rest/tms/aos/invoice_detail/lists` | `{"timeLimit":0,"scenes":1}` | `user_id`, `user_grade`, `user_seller_id`, `charge_type`, `invoice_time`, `keywords`, `detail_id`, `invoice_number`, `item_number`, `shipment_id`, `tracking_number`, `waybill_number`, `client_reference`, `audited`, `charge_start`, `charge_end`, `currency`, `paid`, `invoice`, `created`, `creator`, `tag`, `tag_not`, `service`, `pay_time`, `approver` |
| 客户账单 | `/rest/tms/aos/invoice/lists` | `{"timeLimit":0,"scenes":1}` | `keywords`, `username`, `pay_type`, `user_grade`, `currency`, `money`, `daterange`, `seller_id`, `servicer_id`, `finance_id`, `organization_id`, `due_actions`, `tags`, `tags_not`, `pay_time`, `created`, `ship_time`, `creator`, `due_date`, `tax_date`, `fluent_charge_start`, `fluent_charge_end`, `invoice_confirm` |
| 供应商流水 | `/rest/tms/aos/detail_partner/lists` | `{"timeLimit":0,"scenes":1}` | `user_id`, `charge_type`, `invoice_time`, `charge_start`, `charge_end`, `currency`, `keywords`, `detail_id`, `invoice_number`, `item_number`, `shipment_id`, `tracking_number`, `waybill_number`, `container_number`, `client_reference`, `audited`, `paid`, `invoice`, `created`, `creator`, `tag`, `tag_not`, `pay_time` |
| 供应商账单 | `/rest/tms/aos/invoice_partner/lists` | `{"timeLimit":0,"scenes":1}` | `keywords`, `username`, `currency`, `money`, `creator`, `tags`, `daterange`, `pay_time`, `created`, `ship_time`, `due_date` |
| 销售成本流水 | `/rest/tms/aos/detail_seller/lists` | `{"timeLimit":0,"scenes":1}` | `kw`, `user_id`, `charge_type`, `invoice_time`, `currency`, `item_number`, `detail_id`, `shipment_id`, `tracking_number`, `waybill_number`, `audited`, `creator`, `created` |
| 销售提成流水 | `/rest/tms/aos/detail_seller_commission/lists` | `{"timeLimit":0,"scenes":1}` | `user_id`, `shipment_id`, `charge_type`, `creator`, `audited`, `created`, `invoice_time` |
| 销售提成单 | `/rest/tms/aos/invoice_seller_commission/lists` | `{"timeLimit":0,"scenes":1}` | `user_id`, `invoice_date`, `created` |
| 账户 | `/rest/tms/aos/financial_account/lists` | `{"timeLimit":0,"scenes":1}` | `keywords`, `username`, `user_grade`, `created_daterange` |
| 账户流水 | `/rest/tms/aos/financial_account_record/lists` | `{"timeLimit":0,"scenes":1}` | `id`, `account_id`, `pay_time`, `type`, `username`, `partner_id`, `created_daterange` |
| 汇率 | `/rest/tms/aos/currency/lists` | `{"timeLimit":0,"scenes":1}` | 无 |
| 费用类型 | `/rest/tms/aos/charge_type_mod/lists` | `{"timeLimit":0,"scenes":1}` | `name`, `code`, `type`, `is_show` |
| 月结单 | `/rest/tms/aos/lock_invoice_time/lists` | `{"timeLimit":0,"scenes":1}` | `sell`, `cost`, `seller`, `creator`, `_id`, `time` |
| 费用审批 | `/rest/tms/aos/charge_approval/lists` | `{"timeLimit":0,"scenes":1}` | `user_id`, `charge_type`, `serial_number`, `shipment_id`, `approval_time` |
| 审批 | `/rest/tms/aos/approval/lists` | `{"timeLimit":0,"scenes":1}` | `approval_number`, `number`, `type`, `detail_type`, `time` |

### 6.5 XQT 对照 API

为了对照验证，可以实现同形 body 接收接口，但只查主库，不转发 XQT。

```http
POST /api/external/xqt/aos/{module}/lists
Content-Type: application/json
Authorization: Bearer <token>
```

请求 body 示例：

```json
{
  "timeLimit": 0,
  "scenes": 1,
  "keywords": "ABC",
  "created_daterange": ["2026-05-01", "2026-05-31"]
}
```

响应：

```json
{
  "ok": true,
  "externalSystem": "XQT",
  "module": "financial_detail",
  "items": [],
  "page": {
    "pageNum": 1,
    "pageSize": 20,
    "total": 0
  },
  "mappingWarnings": []
}
```

对照接口要求：

1. 不向 `XQT_BASE_URL` 提交请求。
2. 只使用本地数据库和 `external_field_mappings` 做字段转换。
3. 对无法映射的字段返回 `mappingWarnings`。
4. 所有请求写入 `comparison_cases` 或 API 调试日志。

## 7. 客户卖货财务 P0 API 详细定义

### 7.1 财务流水

```http
POST /api/finance/ledger-records/search
```

请求 body：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "keyword": "",
    "serialNumber": "",
    "paymentType": "",
    "audited": null,
    "accountId": "",
    "payTimeRange": [],
    "createdRange": [],
    "auditTimeRange": [],
    "creatorId": "",
    "invoiced": null,
    "amountRange": {
      "min": null,
      "max": null
    },
    "customerName": "",
    "invoiceNumber": ""
  }
}
```

响应 item 字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 主系统流水 ID |
| `serialNumber` | 流水号 |
| `paymentType` | 支付方式 |
| `accountName` | 账户 |
| `direction` | 收入/支出 |
| `currency` | 币种 |
| `amount` | 金额 |
| `audited` | 审核状态 |
| `invoiced` | 开票状态 |
| `payTime` | 支付时间 |

### 7.2 运单审计

```http
POST /api/finance/shipment-audits/search
```

请求 body：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "keyword": "",
    "waybillNumber": "",
    "ladingNumber": "",
    "serviceCode": "",
    "customerName": "",
    "country": "",
    "sellerId": "",
    "financeId": "",
    "organizationId": "",
    "createdRange": [],
    "shipRange": [],
    "tags": [],
    "chargeAudit": null,
    "chargePaid": null,
    "sellChargeAmount": {
      "min": null,
      "max": null
    },
    "costChargeAmount": {
      "min": null,
      "max": null
    },
    "sellProfit": {
      "min": null,
      "max": null
    }
  }
}
```

响应 item 字段：

| 字段 | 说明 |
| --- | --- |
| `shipmentId` | 运单 ID |
| `shipmentNumber` | 运单号 |
| `waybillNumber` | 提单号 |
| `serviceName` | 服务 |
| `customerName` | 客户 |
| `sellChargeAmount` | 应收金额 |
| `costChargeAmount` | 应付成本 |
| `sellerProfit` | 销售利润 |
| `sellProfit` | 总利润 |
| `chargeAuditStatus` | 费用审核状态 |
| `chargePaidStatus` | 核销状态 |

### 7.3 客户流水

```http
POST /api/finance/customer-charge-details/search
```

请求 body：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerId": "",
    "chargeType": "",
    "invoiceTimeRange": [],
    "keyword": "",
    "detailId": "",
    "invoiceNumber": "",
    "itemNumber": "",
    "shipmentId": "",
    "trackingNumber": "",
    "waybillNumber": "",
    "clientReference": "",
    "audited": null,
    "chargeAmount": {
      "min": null,
      "max": null
    },
    "currency": "",
    "paid": null,
    "invoiceStatus": null,
    "createdRange": [],
    "tags": []
  }
}
```

### 7.4 客户账单

```http
POST /api/finance/customer-invoices/search
```

请求 body：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "keyword": "",
    "customerName": "",
    "payType": "",
    "customerGrade": "",
    "currency": "",
    "amountRange": {
      "min": null,
      "max": null
    },
    "invoiceDateRange": [],
    "sellerId": "",
    "servicerId": "",
    "financeId": "",
    "organizationId": "",
    "dueActions": [],
    "tags": [],
    "paidTimeRange": [],
    "createdRange": [],
    "shipTimeRange": [],
    "dueDateRange": [],
    "taxDateRange": [],
    "invoiceConfirm": null
  }
}
```

响应 item 字段：

| 字段 | 说明 |
| --- | --- |
| `invoiceId` | 账单 ID |
| `invoiceNumber` | 账单号 |
| `customerName` | 客户 |
| `currency` | 币种 |
| `totalAmount` | 账单金额 |
| `paidAmount` | 已核销金额 |
| `unpaidAmount` | 未核销金额 |
| `invoiceConfirm` | 账单确认状态 |
| `dueDate` | 到期时间 |

### 7.5 供应商流水

```http
POST /api/finance/partner-charge-details/search
```

请求 body：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "partnerId": "",
    "chargeType": "",
    "invoiceTimeRange": [],
    "chargeAmount": {
      "min": null,
      "max": null
    },
    "currency": "",
    "keyword": "",
    "detailId": "",
    "invoiceNumber": "",
    "itemNumber": "",
    "shipmentId": "",
    "trackingNumber": "",
    "waybillNumber": "",
    "containerNumber": "",
    "clientReference": "",
    "audited": null,
    "paid": null,
    "invoiceStatus": null,
    "createdRange": [],
    "tags": []
  }
}
```

### 7.6 供应商账单

```http
POST /api/finance/partner-invoices/search
```

请求 body：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "keyword": "",
    "partnerName": "",
    "currency": "",
    "amountRange": {
      "min": null,
      "max": null
    },
    "creatorId": "",
    "tags": [],
    "invoiceDateRange": [],
    "paidTimeRange": [],
    "createdRange": [],
    "shipTimeRange": [],
    "dueDateRange": []
  }
}
```

### 7.7 账户和账户流水

```http
POST /api/finance/accounts/search
POST /api/finance/account-records/search
```

账户查询 body：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "keyword": "",
    "ownerName": "",
    "ownerGrade": "",
    "createdRange": []
  }
}
```

账户流水查询 body：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "recordId": "",
    "accountId": "",
    "payTimeRange": [],
    "type": "",
    "customerName": "",
    "partnerId": "",
    "createdRange": []
  }
}
```

### 7.8 费用类型

```http
POST /api/finance/charge-types/search
```

请求 body：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "name": "",
    "code": "",
    "type": "",
    "visible": null
  }
}
```

## 8. 第二部分：客户自己制单 API，对照 ACC

### 8.1 开发边界

客户自己制单流程从零开发，ACC 只作为源码、旧 API body、状态码、面单和余额口径对照。目标不是保留 `/api/acc/*` 作为生产接口，而是把能力沉淀到 `/api/document/*`、`/api/customer-api/*`、`/api/labels/*`、`/api/tracking/*`。

目标沉淀策略：

| 当前路径 | 目标 |
| --- | --- |
| `/api/acc/customer-api/order` | `/api/customer-api/orders`，客户自助/API 下单 |
| `/api/acc/orders` | `/api/document/orders`，内部制单订单 |
| `/api/acc/orders/calc-freight` | `/api/document/rates/quote`，运费试算 |
| `/api/acc/customer-api/balance` | `/api/customer-api/balance`，客户余额 |
| `/api/acc/tracking/query` | `/api/tracking/query`，轨迹查询 |
| `/api/acc/orders/*label*` | `/api/labels/*`，面单生成、换标、下载 |
| `/api/acc/charges`, `/costs`, `/bills`, `/receiveds` | `/api/finance/*`，制单流程费用、账单、收款 |

### 8.2 制单流程 API 总表

| 新系统接口 | ACC 对照能力 | 优先级 | 说明 |
| --- | --- | --- | --- |
| `POST /api/document/orders/search` | `GET /api/acc/orders` | P0 | 内部制单订单列表 |
| `POST /api/document/orders` | `POST /api/acc/customer-api/order` | P0 | 内部创建制单订单 |
| `GET /api/document/orders/{id}` | ACC 订单详情 | P0 | 内部制单订单详情 |
| `PUT /api/document/orders/{id}` | ACC 订单编辑 | P0 | 修改制单订单 |
| `DELETE /api/document/orders/{id}` | ACC 作废/取消 | P0 | 软删除并记录操作人 |
| `POST /api/customer-api/orders` | `POST /api/acc/customer-api/order` | P0 | 客户自助/API 下单 |
| `POST /api/document/rates/quote` | ACC 价格/余额逻辑 | P0 | 运费试算，不落正式费用 |
| `POST /api/document/orders/{id}/submit` | ACC 提交/审核逻辑 | P0 | 提交制单并生成运单 |
| `POST /api/labels/generate` | ACC 面单逻辑 | P0 | 生成面单文件 |
| `POST /api/labels/relabel` | ACC 换标逻辑 | P1 | 旧单号换新单号 |
| `GET /api/labels/{id}/download` | ACC 面单下载 | P0 | 下载 PDF/图片 |
| `POST /api/tracking/query` | `POST /api/acc/tracking/query` | P0 | 单号轨迹查询 |
| `GET /api/customer-api/balance` | ACC 余额查询 | P0 | 客户余额和可用额度 |
| `POST /api/finance/document-charges/search` | ACC 应收运费 | P0 | 制单费用列表 |
| `POST /api/finance/document-invoices/search` | ACC 客户账单 | P1 | 制单账单列表 |

制单流程标准查询 body：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "DOCUMENT_CUSTOMER",
    "serviceMode": "DOCUMENT_SHIPPING",
    "keyword": "",
    "orderNo": "",
    "trackingNo": "",
    "status": "",
    "createdRange": []
  }
}
```

### 8.3 制单流程请求 body

内部提交制单：

```json
{
  "orderId": "uuid",
  "validateOnly": false,
  "remark": "提交制单"
}
```

客户 API 下单：

```json
{
  "customerCode": "CUST001",
  "referenceNo": "REF001",
  "serviceCode": "SERVICE",
  "country": "US",
  "weight": 1.2,
  "pieces": 1,
  "receiver": {
    "name": "Receiver",
    "phone": "",
    "postcode": "",
    "city": "",
    "address": ""
  },
  "items": [
    {
      "name": "Product",
      "quantity": 1,
      "declaredValue": 10
    }
  ]
}
```

运费试算：

```json
{
  "customerId": "uuid",
  "serviceCode": "SERVICE",
  "destinationCountry": "US",
  "destinationPostcode": "91710",
  "pieces": 1,
  "weightKg": 1.2,
  "declaredValue": 10,
  "items": []
}
```

生成面单：

```json
{
  "shipmentId": "uuid",
  "labelFormat": "PDF",
  "paperSize": "A4",
  "forceRegenerate": false
}
```

换标：

```json
{
  "originalTrackingNo": "OLD_TRACKING_NO",
  "shipmentId": "uuid",
  "reason": "退件二次制单"
}
```

轨迹查询：

```json
{
  "trackingNo": "TRACKING_NO",
  "includeRawEvents": false
}
```

制单费用查询：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "DOCUMENT_CUSTOMER",
    "customerId": "",
    "shipmentNo": "",
    "trackingNo": "",
    "chargeType": "",
    "paid": null,
    "createdRange": [],
    "amountRange": {
      "min": null,
      "max": null
    }
  }
}
```

## 9. 外部映射 API

外部映射 API 用于保存 XQT 卖货对照、ACC 制单对照的页面、接口、字段和对照结果。

### 9.1 外部系统

```http
GET /api/external/systems
POST /api/external/systems
```

创建 body：

```json
{
  "code": "XQT",
  "name": "新智慧",
  "systemType": "tms_aos",
  "baseUrlKey": "XQT_BASE_URL",
  "status": "active",
  "notes": "客户卖货流程只读对照样本"
}
```

### 9.2 外部模块

```http
GET /api/external/modules?systemCode=XQT
POST /api/external/modules
```

创建 body：

```json
{
  "externalSystemCode": "XQT",
  "moduleCode": "financial_detail",
  "moduleName": "财务流水",
  "pagePath": "/tms/aos/financial_detail",
  "apiPath": "/rest/tms/aos/financial_detail/lists",
  "method": "POST",
  "safeMode": true
}
```

### 9.3 字段映射

```http
GET /api/external/field-mappings?systemCode=XQT&moduleCode=financial_detail
POST /api/external/field-mappings
```

创建 body：

```json
{
  "externalSystemCode": "XQT",
  "moduleCode": "financial_detail",
  "externalField": "serial_number",
  "externalLabel": "流水号",
  "internalEntity": "FinanceLedgerRecord",
  "internalField": "serialNumber",
  "valueType": "string",
  "mappingStatus": "confirmed",
  "notes": ""
}
```

### 9.4 对照用例

```http
GET /api/external/comparison-cases
POST /api/external/comparison-cases
POST /api/external/comparison-cases/{id}/result
```

创建 body：

```json
{
  "caseNo": "XQT-FIN-001",
  "externalSystemCode": "XQT",
  "moduleCode": "financial_detail",
  "internalUrl": "/finance/ledger-records",
  "externalFilter": {
    "timeLimit": 0,
    "scenes": 1,
    "keywords": "ABC"
  },
  "expectedResult": {
    "rowCount": 20,
    "amountSummary": "manual-check"
  },
  "status": "pending"
}
```

写入结果 body：

```json
{
  "actualResult": {
    "rowCount": 20,
    "amountSummary": "matched"
  },
  "status": "passed",
  "diff": []
}
```

## 10. 系统管理 API 迁移

Fastify `/api/sys/*` 后续迁到 Spring Boot `/api/admin/*`。

| 当前接口 | 目标接口 | body |
| --- | --- | --- |
| `POST /api/sys/users` | `POST /api/admin/users` | 用户创建 body |
| `PUT /api/sys/users/:id` | `PUT /api/admin/users/{id}` | 用户更新 body |
| `POST /api/sys/users/:id/reset-password` | `POST /api/admin/users/{id}/reset-password` | 重置密码 body |
| `POST /api/sys/roles` | `POST /api/admin/roles` | 角色创建 body |
| `POST /api/sys/menus` | `POST /api/admin/menus` | 菜单保存 body |
| `POST /api/sys/configs` | `POST /api/admin/configs` | 配置保存 body |

创建用户 body：

```json
{
  "username": "operator01",
  "displayName": "操作员",
  "email": "operator@example.com",
  "password": "Initial@123456",
  "status": "ACTIVE",
  "roleCodes": ["OPERATOR"]
}
```

创建角色 body：

```json
{
  "code": "finance",
  "name": "财务",
  "description": "财务人员",
  "permissionCodes": ["finance.receivable.read", "finance.payable.read"]
}
```

菜单 body：

```json
{
  "parentId": null,
  "name": "财务中心",
  "path": "/finance",
  "component": "FinanceLayout",
  "icon": "wallet",
  "permission": "finance.receivable.read",
  "sortOrder": 10,
  "visible": true
}
```

配置 body：

```json
{
  "key": "finance.auto_reconcile",
  "value": "false",
  "group": "finance",
  "description": "是否自动对账"
}
```

## 11. Controller 开发模板

### 11.1 Controller

```java
@RestController
@RequestMapping("/api/finance/customer-invoices")
public class CustomerInvoiceController {
    private final CustomerInvoiceService service;
    private final RequestContext context;

    public CustomerInvoiceController(CustomerInvoiceService service, RequestContext context) {
        this.service = service;
        this.context = context;
    }

    @PostMapping("/search")
    public ApiResponse<PageResponse<CustomerInvoiceView>> search(
        Authentication authentication,
        @Valid @RequestBody CustomerInvoiceSearchRequest request
    ) {
        AuthPrincipal auth = context.principal(authentication);
        return ApiResponse.ok(service.search(auth, request));
    }
}
```

### 11.2 Request DTO

```java
public record CustomerInvoiceSearchRequest(
        Integer pageNum,
        Integer pageSize,
        CustomerInvoiceFilters filters,
        List<SortSpec> sorts,
        Boolean includeExternalFields
) {}
```

### 11.3 Service 规则

Service 层负责：

1. 设置租户上下文。
2. 校验权限和业务规则。
3. 做字段映射和查询组装。
4. 控制事务。
5. 写接口使用 `@Transactional(rollbackFor = Exception.class)`。
6. 写操作日志。
7. 抛出 `ApiException`，由全局异常处理器统一输出 `ApiResponse`。

Repository 层只负责 SQL，不处理业务状态。

## 12. 验收清单

每个新增 API 必须满足：

| 项 | 要求 |
| --- | --- |
| 路径 | 符合模块分组和 REST 风格 |
| 鉴权 | 需要 token 的接口必须校验 |
| 权限 | 明确 permission code |
| 参数 | 有 request body 示例和校验规则 |
| 响应 | 有成功和失败响应格式 |
| 分页 | 已实现接口使用 `page/pageSize` 或目标规范 `pageNum/pageSize`，文档必须标清 |
| 排序 | 明确可排序字段 |
| 租户 | SQL 带 tenant 或依赖 RLS |
| 审计 | 写接口记录操作日志 |
| 幂等 | 金额、库存、账单、批量写支持幂等 |
| 测试 | 至少有接口测试或 service 单元测试 |
| 质量门禁 | `./mvnw verify`、前端 lint/build 必须通过 |
| 对照 | 卖货流程关联 XQT comparison case，制单流程关联 ACC comparison case |

## 13. 文档来源

| 文档 | 用途 |
| --- | --- |
| `docs/api-reference.md` | 当前已存在接口清单 |
| `docs/api-request-body-catalog.md` | 每个目标 API 的 request body 总表 |
| `docs/alibaba-java-development-standard.md` | 阿里巴巴 Java 开发规约落地版 |
| `docs/xqt-readonly-api-crawl.md` | 新智慧全菜单只读接口抓取 |
| `docs/xqt-finance-api-requests.md` | 新智慧财务 19 个 POST 请求和 body 模板 |
| `docs/acc-api-reverse-db-design.md` | ACC 源码 API 到数据库反推 |
| `docs/main-system-database-design.md` | 主系统数据库表设计 |
| `docs/complete-system-architecture-development.md` | 总体架构与开发方案 |
| `docs/platform-capability-coverage-framework-research.md` | 功能覆盖与框架选型调研 |
| `docs/acc-xqt-full-match-roadmap.md` | ACC 与新智慧完全匹配实施矩阵 |
| `docs/development-comparison-test-cases.md` | 对照开发测试用例 |
