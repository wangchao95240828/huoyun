# API 文档

版本：2026-05-08

## 1. 环境与分流

| 服务 | 本地地址 | 说明 |
| --- | --- | --- |
| Web | `http://localhost:5173` | 统一前端入口 |
| Spring Boot API | `http://localhost:18103` | 主后端，认证和系统管理基础接口 |
| Fastify API | `http://localhost:18080` | 原型接口，承载 ACC/XQT/财务/系统管理早期路由 |

前端代理：

| 前端请求 | 代理到 | 说明 |
| --- | --- | --- |
| `/api/auth/*` | `18103` | 登录、登出、当前用户 |
| `/api/admin/*` | `18103` | 用户、角色、权限 |
| `/api/health` | `18103` | Spring Boot 健康检查 |
| `/api/*` | `18080` | 原型业务接口 |
| `/health` | `18080` | Fastify 健康检查和外部 adapter 状态 |

## 2. 通用约定

### 2.1 认证

除登录和健康检查外，Spring Boot 主后端接口使用 Bearer Token。

```http
Authorization: Bearer <token>
```

### 2.2 JSON 响应

Spring Boot 正式接口统一使用：

```json
{
  "ok": true,
  "data": {},
  "error": null,
  "errorCode": null
}
```

列表接口通常返回：

```json
{
  "ok": true,
  "data": {
    "items": []
  },
  "error": null,
  "errorCode": null
}
```

错误响应：

```json
{
  "ok": false,
  "data": null,
  "error": "错误信息",
  "errorCode": "BAD_REQUEST"
}
```

Controller 不直接返回裸 `Map`，不直接持有 `JdbcTemplate`，写接口操作人统一从 token 获取。

## 3. Spring Boot 主后端 API

### 3.1 健康检查

```http
GET /api/health
```

响应：

```json
{
  "ok": true,
  "data": {
    "ok": true,
    "service": "xqt-backend",
    "database": "ok",
    "time": "2026-05-08T08:48:48+08:00"
  },
  "error": null,
  "errorCode": null
}
```

### 3.2 登录

```http
POST /api/auth/login
Content-Type: application/json
```

请求 body：

```json
{
  "tenantCode": "xqt",
  "username": "admin",
  "password": "******"
}
```

字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `tenantCode` | 否 | 租户编码，默认 `xqt` |
| `username` | 是 | 用户名或邮箱 |
| `password` | 是 | 密码 |

响应：

```json
{
  "ok": true,
  "data": {
    "ok": true,
    "token": "eyJ...",
    "expiresIn": 28800,
    "user": {
      "userId": "uuid",
      "tenantId": "uuid",
      "tenantCode": "xqt",
      "username": "admin",
      "displayName": "系统管理员",
      "roles": ["ADMIN"],
      "permissions": ["admin.user.read"],
      "exp": 1778106742,
      "jti": "uuid"
    }
  },
  "error": null,
  "errorCode": null
}
```

状态码：

| 状态码 | 场景 |
| --- | --- |
| `200` | 登录成功 |
| `400` | 用户名或密码为空 |
| `401` | 用户不存在、密码错误、用户不可用、会话过期 |

### 3.3 当前用户

```http
GET /api/auth/me
Authorization: Bearer <token>
```

响应：

```json
{
  "ok": true,
  "data": {
    "user": {
      "userId": "uuid",
      "tenantId": "uuid",
      "tenantCode": "xqt",
      "username": "admin",
      "displayName": "系统管理员",
      "roles": ["ADMIN"],
      "permissions": []
    }
  },
  "error": null,
  "errorCode": null
}
```

### 3.4 退出登录

```http
POST /api/auth/logout
Authorization: Bearer <token>
```

响应：

```json
{
  "ok": true,
  "data": {
    "success": true
  },
  "error": null,
  "errorCode": null
}
```

退出后同一个 token 再访问受保护接口应返回 `401`。

### 3.5 用户列表

```http
GET /api/admin/users
Authorization: Bearer <token>
```

权限：`admin.user.read`

响应：

```json
{
  "ok": true,
  "data": {
    "items": [
      {
        "id": "uuid",
        "username": "admin",
        "email": "admin@xqt.local",
        "displayName": "系统管理员",
        "roleCode": "ADMIN",
        "status": "ACTIVE",
        "lastLoginAt": "2026-05-08 08:48:48",
        "createdAt": "2026-05-08 08:00:00",
        "updatedAt": "2026-05-08 08:48:48",
        "roles": ["ADMIN"]
      }
    ]
  },
  "error": null,
  "errorCode": null
}
```

### 3.6 角色列表

```http
GET /api/admin/roles
Authorization: Bearer <token>
```

权限：`admin.role.read`

响应字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 角色 ID |
| `code` | 角色编码 |
| `name` | 角色名称 |
| `description` | 说明 |
| `systemRole` | 是否系统角色 |
| `permissions` | 角色拥有的权限码 |

### 3.7 权限列表

```http
GET /api/admin/permissions
Authorization: Bearer <token>
```

权限：`admin.role.read`

响应字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 权限 ID |
| `code` | 权限码 |
| `name` | 权限名称 |
| `resource` | 资源 |
| `action` | 动作 |
| `description` | 描述 |

## 4. Fastify 健康检查

```http
GET /health
```

响应：

```json
{
  "ok": true,
  "service": "xqt-portal",
  "upstreams": {
    "acc": {
      "available": true,
      "connected": false,
      "error": "Access denied..."
    },
    "xqt": {
      "available": false,
      "connected": false
    },
    "postgres": {
      "available": true,
      "connected": true
    }
  }
}
```

说明：

- `acc.available=true` 代表 adapter 已初始化，不代表真实 ACC 数据库已连接。
- 当前 ACC 真实连接未完成。
- `/health` 属于 Fastify 原型 API。

## 5. Fastify 财务概览 API

### 5.1 分公司

```http
GET /api/finance/branches
```

响应：

```json
{
  "data": [
    {
      "id": "uuid",
      "code": "HQ",
      "name": "总部",
      "org_type": "hq",
      "is_active": true
    }
  ]
}
```

### 5.2 财务概览

```http
GET /api/finance/overview
```

可选请求头：

```http
x-tenant-code: xqt
```

响应包含 ACC、XQT、本地财务汇总，用于驾驶舱。

### 5.3 痛点覆盖图

```http
GET /api/finance/painpoint-map
```

用于展示财务痛点、模块覆盖和当前落地阶段。

## 6. Fastify 系统管理原型 API

这些接口位于 `apps/api/src/routes/system.ts`，当前属于原型系统管理能力，后续应迁入 Spring Boot。

### 6.1 登录和密码

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/sys/login` | 原型系统登录 |
| `POST` | `/api/sys/change-password` | 修改密码 |

### 6.2 用户管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/sys/users` | 用户列表 |
| `POST` | `/api/sys/users` | 创建用户 |
| `PUT` | `/api/sys/users/:id` | 更新用户 |
| `DELETE` | `/api/sys/users/:id` | 删除用户 |
| `POST` | `/api/sys/users/:id/reset-password` | 重置密码 |

创建用户 body：

```json
{
  "username": "zhangsan",
  "password": "******",
  "realName": "张三",
  "email": "zhangsan@example.com",
  "phone": "13800000000",
  "roleId": 1,
  "branchId": 1,
  "status": "ACTIVE"
}
```

### 6.3 角色和菜单

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/sys/roles` | 角色列表 |
| `POST` | `/api/sys/roles` | 创建角色 |
| `PUT` | `/api/sys/roles/:id` | 更新角色 |
| `DELETE` | `/api/sys/roles/:id` | 删除角色 |
| `GET` | `/api/sys/menus` | 菜单列表 |
| `POST` | `/api/sys/menus` | 保存菜单 |
| `DELETE` | `/api/sys/menus/:id` | 删除菜单 |

创建角色 body：

```json
{
  "name": "财务主管",
  "description": "财务模块管理",
  "permissions": ["finance.receivable.read", "finance.receivable.write"]
}
```

菜单 body：

```json
{
  "parentId": 0,
  "name": "财务管理",
  "path": "/finance",
  "icon": "WalletCards",
  "sort": 10,
  "permission": "finance.receivable.read",
  "visible": true
}
```

### 6.4 日志和配置

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/sys/logs/operations` | 操作日志 |
| `GET` | `/api/sys/logs/errors` | 错误日志 |
| `POST` | `/api/sys/logs/errors` | 写入错误日志 |
| `GET` | `/api/sys/configs` | 配置列表 |
| `GET` | `/api/sys/configs/:key` | 查询单个配置 |
| `POST` | `/api/sys/configs` | 保存配置 |
| `DELETE` | `/api/sys/configs/:key` | 删除配置 |
| `GET` | `/api/sys/info` | 系统信息 |
| `GET` | `/api/sys/tools/db-stats` | 数据库统计 |
| `POST` | `/api/sys/tools/clean-logs` | 清理日志 |

配置 body：

```json
{
  "key": "finance.auto_reconcile",
  "value": "true",
  "description": "是否启用自动对账",
  "group": "finance"
}
```

### 6.5 客户服务、硬件、短信

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/sys/customer-accounts` | 客户账号列表 |
| `POST` | `/api/sys/customer-accounts` | 创建客户账号 |
| `PUT` | `/api/sys/customer-accounts/:id` | 更新客户账号 |
| `POST` | `/api/sys/customer-login` | 客户登录 |
| `GET` | `/api/sys/service/messages` | 客服消息 |
| `POST` | `/api/sys/service/messages` | 发送客服消息 |
| `POST` | `/api/sys/service/messages/read` | 标记已读 |
| `GET` | `/api/sys/service/unread` | 未读消息数 |
| `GET` | `/api/sys/hardware` | 硬件列表 |
| `POST` | `/api/sys/hardware` | 注册/保存硬件 |
| `POST` | `/api/sys/hardware/:id/heartbeat` | 硬件心跳 |
| `POST` | `/api/sys/sms/send` | 发送短信 |
| `GET` | `/api/sys/sms/logs` | 短信日志 |

## 7. ACC 原型 API

ACC API 当前位于 Fastify 原型层 `/api/acc/*`，主要用于复刻、对照和页面原型。真实 ACC MySQL 目前未接通，因此很多接口会返回空数据或默认结果。

### 7.1 通用列表参数

大部分列表接口支持：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `page` | number | 页码，默认 1 |
| `pageSize` | number | 每页数量，默认 50 |
| `keyword` | string | 关键词 |
| `dateFrom` | string | 开始日期 |
| `dateTo` | string | 结束日期 |

原型历史列表响应：

```json
{
  "data": [],
  "total": 0
}
```

迁入 Spring Boot 后必须改为 `ApiResponse<PageResponse<T>>`，不保留裸 `data/total` 作为正式接口契约。

### 7.2 ACC 概览

```http
GET /api/acc/stats
```

响应：

```json
{
  "orderCount": 0,
  "customerCount": 0,
  "supplierCount": 0,
  "channelCount": 0
}
```

### 7.3 订单和物流

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/acc/orders` | 快件订单 |
| `GET` | `/api/acc/orders/:id` | 订单详情 |
| `GET` | `/api/acc/orders/:id/tracks` | 订单轨迹 |
| `GET` | `/api/acc/returns` | 退件 |
| `GET` | `/api/acc/collects` | 总单/留仓 |
| `GET` | `/api/acc/detains` | 扣件 |
| `GET` | `/api/acc/asks` | 问题件 |
| `GET` | `/api/acc/reparations` | 赔偿 |
| `GET` | `/api/acc/quick-orders` | 快速下单 |
| `GET` | `/api/acc/void-orders` | 作废订单 |
| `GET` | `/api/acc/shipments` | 出货 |
| `GET` | `/api/acc/shipments/:id/items` | 出货明细 |
| `GET` | `/api/acc/stowages` | 配载 |
| `GET` | `/api/acc/stowages/:id/packages` | 配载包裹 |
| `GET` | `/api/acc/packages` | 装箱单 |
| `GET` | `/api/acc/transits` | 转运 |
| `GET` | `/api/acc/ports` | 港口 |
| `GET` | `/api/acc/warehouses` | 仓库 |
| `GET` | `/api/acc/dispatches` | 上门揽收 |
| `GET` | `/api/acc/forecasts` | 预报包裹 |
| `GET` | `/api/acc/tracks` | 轨迹项目 |

### 7.4 ACC 财务

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/acc/charges` | 应收运费 |
| `GET` | `/api/acc/charges/unpaid` | 未收费用 |
| `GET` | `/api/acc/costs` | 应付成本 |
| `GET` | `/api/acc/bills` | 客户账单 |
| `GET` | `/api/acc/bills/:id/items` | 账单明细 |
| `GET` | `/api/acc/payments` | 供应商付款 |
| `GET` | `/api/acc/receiveds` | 客户收款 |
| `GET` | `/api/acc/profits` | 利润明细 |
| `GET` | `/api/acc/profit-report` | 利润报表 |
| `GET` | `/api/acc/profits/overdue` | 逾期利润项 |
| `GET` | `/api/acc/profits/summary` | 利润汇总 |
| `GET` | `/api/acc/commissions` | 员工提成 |
| `GET` | `/api/acc/transfers` | 转账 |
| `GET` | `/api/acc/currencies` | 汇率 |
| `GET` | `/api/acc/fees` | 杂费套餐 |
| `GET` | `/api/acc/fee-types` | 附加费类型 |
| `GET` | `/api/acc/customer-fines` | 客户罚款 |
| `GET` | `/api/acc/supplier-fines` | 物流商罚款 |
| `GET` | `/api/acc/customer-adjusts` | 客户调账 |
| `GET` | `/api/acc/supplier-adjusts` | 物流商调账 |
| `GET` | `/api/acc/customer-refunds` | 客户退款 |
| `GET` | `/api/acc/supplier-refunds` | 物流商退款 |
| `GET` | `/api/acc/customer-rebates` | 客户返利 |
| `GET` | `/api/acc/supplier-rebates` | 物流商返利 |
| `GET` | `/api/acc/expenses` | 费用收支 |
| `GET` | `/api/acc/banks` | 银行账户 |

### 7.5 客户、供应商、渠道、基础资料

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/acc/customers` | 客户 |
| `GET` | `/api/acc/customer-groups` | 客户分组 |
| `GET` | `/api/acc/suppliers` | 物流商 |
| `GET` | `/api/acc/channels` | 渠道 |
| `GET` | `/api/acc/channel-accounts` | 渠道账号 |
| `GET` | `/api/acc/products` | 价格表 |
| `GET` | `/api/acc/product-items` | 品名 |
| `GET` | `/api/acc/potentials` | 潜在客户 |
| `GET` | `/api/acc/sold-tos` | 收件地址 |
| `GET` | `/api/acc/notices` | 客户通知 |
| `GET` | `/api/acc/employees` | 员工 |
| `GET` | `/api/acc/wages` | 工资 |
| `GET` | `/api/acc/attendances` | 考勤 |
| `GET` | `/api/acc/branches` | 分店 |
| `GET` | `/api/acc/departments` | 部门 |
| `GET` | `/api/acc/countries` | 国家地区 |
| `GET` | `/api/acc/zones` | 价格分区 |
| `GET` | `/api/acc/postcodes` | 邮编库 |
| `GET` | `/api/acc/remotes` | 偏远邮编 |
| `GET` | `/api/acc/fuels` | 燃油费率 |
| `GET` | `/api/acc/hscodes` | HS 编码 |
| `GET` | `/api/acc/bank-names` | 银行名称 |
| `GET` | `/api/acc/districts` | 行政区域 |
| `GET` | `/api/acc/logistics-interfaces` | 物流接口 |
| `GET` | `/api/acc/tasks` | 定时任务 |
| `GET` | `/api/acc/templates` | 消息模板 |

### 7.6 ACC 通用 CRUD

```http
POST /api/acc/:module
PUT /api/acc/:module/:id
DELETE /api/acc/:module/:id
GET /api/acc/:module/:id/raw
```

说明：

- `:module` 对应前端 ACC 标签页的 `api` 值，如 `orders`、`customers`、`bills`。
- 当前属于原型层，生产写入前必须迁移到 Spring Boot 并加入权限、审计、租户隔离。

### 7.7 ACC 业务动作

审核类通用 body：

```json
{
  "id": 123
}
```

批量审核 body：

```json
{
  "ids": [1, 2, 3]
}
```

关键动作：

| 方法 | 路径 | Body | 说明 |
| --- | --- | --- | --- |
| `POST` | `/api/acc/:module/audit` | `{ "id": 1 }` | 通用审核 |
| `POST` | `/api/acc/:module/undo` | `{ "id": 1 }` | 通用反审核 |
| `POST` | `/api/acc/orders/audit-biz` | `{ "id": 1 }` | 订单业务审核 |
| `POST` | `/api/acc/orders/undo-biz` | `{ "id": 1 }` | 订单反审核 |
| `POST` | `/api/acc/orders/calc-freight` | `{ "id": 1 }` | 计算运费 |
| `POST` | `/api/acc/orders/reload-freight` | `{ "id": 1 }` | 重算运费 |
| `POST` | `/api/acc/orders/calc-volume` | `{ "id": 1 }` | 计算体积 |
| `POST` | `/api/acc/orders/import` | `{ "rows": [] }` | 导入订单 |
| `POST` | `/api/acc/shipments/change-channel` | `{ "id": 1, "channelId": 2 }` | 更换渠道 |
| `POST` | `/api/acc/shipments/:id/items` | `{ "expressId": 1 }` | 添加出货明细 |
| `DELETE` | `/api/acc/shipments/:sid/items/:eid` | 无 | 删除出货明细 |
| `POST` | `/api/acc/charges/batch-audit` | `{ "ids": [1] }` | 应收批量审核 |
| `POST` | `/api/acc/costs/batch-audit` | `{ "ids": [1] }` | 应付批量审核 |
| `POST` | `/api/acc/bills/generate` | 见下方 | 生成客户账单 |
| `POST` | `/api/acc/bills/reload` | `{ "id": 1 }` | 重算账单 |
| `POST` | `/api/acc/receiveds/quick` | 见下方 | 快速收款 |
| `POST` | `/api/acc/commissions/calculate` | `{ "month": "2026-05" }` | 计算提成 |
| `POST` | `/api/acc/stowages/status` | `{ "id": 1, "status": 2 }` | 修改配载状态 |
| `POST` | `/api/acc/stowages/sync` | `{ "id": 1 }` | 同步配载 |
| `POST` | `/api/acc/warehouse/checkin` | `{ "expressId": 1 }` | 仓库入库 |
| `POST` | `/api/acc/warehouse/checkout` | `{ "expressId": 1 }` | 仓库出库 |
| `POST` | `/api/acc/warehouse/scan-check` | `{ "trackNo": "..." }` | 扫描检查 |
| `POST` | `/api/acc/freight/calculate` | 订单费用参数 | 运费计算 |
| `POST` | `/api/acc/tracking/query` | `{ "trackNo": "..." }` | 轨迹查询 |
| `POST` | `/api/acc/customer-api/order` | 客户下单 body | 客户 API 下单 |

生成账单 body：

```json
{
  "customerId": 1,
  "dateFrom": "2026-05-01",
  "dateTo": "2026-05-31"
}
```

快速收款 body：

```json
{
  "customerId": 1,
  "amount": 1000,
  "bankId": 1
}
```

利润汇总查询：

```http
GET /api/acc/profits/summary?dateFrom=2026-05-01&dateTo=2026-05-31&groupBy=customer
```

## 8. 新智慧财务外部 API 清单

新智慧接口不作为生产写入目标。当前仅用于只读抓取、页面反推和表单字段验证。

基础域名由环境变量或部署配置提供，文档中不固化真实第三方地址：

```text
XQT_BASE_URL
```

通用基础 body：

```json
{
  "timeLimit": 0,
  "scenes": 1
}
```

已整理完整请求 body 的文档：

| 文档 | 说明 |
| --- | --- |
| `docs/xqt-finance-api-requests.md` | 新智慧财务模块 19 个 POST 请求与 body 模板 |
| `docs/xqt-readonly-api-crawl.md` | 新智慧只读接口抓取结果 |

财务模块 POST API 总览：

| 模块 | POST API |
| --- | --- |
| 财务流水 | `/rest/tms/aos/financial_detail/lists` |
| 运单审计 | `/rest/tms/aos/shipment/lists` |
| 应收报表 | `/rest/tms/aos/user_report/lists` |
| 应付报表 | `/rest/tms/aos/partner_report/lists` |
| 运价维护 | `/rest/tms/aos/rates/lists` |
| 客户流水 | `/rest/tms/aos/invoice_detail/lists` |
| 客户账单 | `/rest/tms/aos/invoice/lists` |
| 供应商流水 | `/rest/tms/aos/detail_partner/lists` |
| 供应商账单 | `/rest/tms/aos/invoice_partner/lists` |
| 销售成本流水 | `/rest/tms/aos/detail_seller/lists` |
| 销售提成流水 | `/rest/tms/aos/detail_seller_commission/lists` |
| 销售提成单 | `/rest/tms/aos/invoice_seller_commission/lists` |
| 账户 | `/rest/tms/aos/financial_account/lists` |
| 账户流水 | `/rest/tms/aos/financial_account_record/lists` |
| 汇率 | `/rest/tms/aos/currency/lists` |
| 费用类型 | `/rest/tms/aos/charge_type_mod/lists` |
| 月结单 | `/rest/tms/aos/lock_invoice_time/lists` |
| 费用审批 | `/rest/tms/aos/charge_approval/lists` |
| 审批 | `/rest/tms/aos/approval/lists` |

## 9. 迁移标记

| 接口范围 | 当前归属 | 目标归属 | 优先级 |
| --- | --- | --- | --- |
| `/api/auth/*` | Spring Boot | Spring Boot | 已完成 |
| `/api/admin/*` | Spring Boot | Spring Boot | 已完成基础查询 |
| `/api/sys/*` | Fastify | Spring Boot | P1 |
| `/api/finance/*` | Fastify | Spring Boot | P1 |
| `/api/acc/*` | Fastify | Spring Boot adapter | P3 |
| 新智慧 `/rest/tms/aos/*` | 外部系统 | 主系统 finance/order API | P1-P2 |
