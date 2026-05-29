# XQT-SaaS API 全量参考手册

> 生成日期：2026-05-29
> 自动从 `apps/backend/src/main/java/com/xqt/saas/` 抓取
> 共 **113 个 controller**、**531 个 endpoint**

---

## 鉴权说明

除标注「无鉴权」的端点外，全部 API 需 Bearer JWT：

```
Authorization: Bearer <token>
```

获取 token：`POST /api/auth/login` body `{tenantCode, username, password}`，详见 §00。

**通用响应包装**：
```json
{"ok": true, "data": <实际数据>, "error": null, "errorCode": null}
```

**通用错误码**：BAD_REQUEST / UNAUTHORIZED / FORBIDDEN / NOT_FOUND /
CONFLICT / VALIDATION_FAILED / INTERNAL_ERROR

**通用分页参数**（所有 ACC 列表 endpoint 通用）：

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| `page` | `Integer` | 否 | 1 | 页码（从 1 开始） |
| `pageSize` | `Integer` | 否 | 20 | 每页大小，最大 200 |
| `keyword` | `String` | 否 | - | 关键词模糊搜索 |
| `dateFrom` | `String` (YYYY-MM-DD) | 否 | - | 创建时间下界 |
| `dateTo` | `String` (YYYY-MM-DD) | 否 | - | 创建时间上界 |

**列表响应结构**：
```json
{"data": [...], "total": <number>}
```

**审核操作**（所有支持审核的 controller 通用）：
`PUT /api/acc/audit` body `{table, id, status: AUDITED|UNAUDITED, auditName}`

---

## §00. 认证 / 鉴权

包路径：`com.xqt.saas.auth`

### AuthController

**Base path**：`/api/auth`

#### `POST /api/auth/login` → `login()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `LoginRequest` | 是 |

返回：`ApiResponse<LoginResponse>`

#### `GET /api/auth/me` → `me()`

返回：`ApiResponse<AuthMeResponse>`

#### `POST /api/auth/logout` → `logout()`

返回：`ApiResponse<CommandResponse>`

---

## §01. 系统管理

包路径：`com.xqt.saas.admin`

### AdminController

**Base path**：`/api/admin`

（本 controller 无 HTTP 端点）

---

## §02. 客户 API（第三方下单接口）

包路径：`com.xqt.saas.customerapi`

### CustomerApiController

**Base path**：`/api/customer-api`

#### `GET /api/customer-api/balance` → `balance()`

返回：`ApiResponse<ItemResponse<BalanceList>>`

#### `POST /api/customer-api/orders` → `preOrder()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `body` | `@RequestBody` | `CustomerApiRequests.PreOrder` | 是 |

返回：`ApiResponse<ItemResponse<PreOrderResult>>`

#### `PUT /api/customer-api/orders/{no}` → `modifyOrder()`

返回：`ApiResponse<ItemResponse<OrderDetail>>`

#### `POST /api/customer-api/orders/{no}/submit` → `submitOrder()`

返回：`ApiResponse<ItemResponse<SubmitResult>>`

#### `POST /api/customer-api/orders/{no}/cancel` → `cancelOrder()`

返回：`ApiResponse<ItemResponse<CancelResult>>`

#### `POST /api/customer-api/rates/quote` → `quote()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `body` | `@RequestBody` | `RateQuoteRequest` | 是 |

返回：`ApiResponse<ItemResponse<Quote>>`

#### `POST /api/customer-api/orders/status` → `orderStatus()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `body` | `@RequestBody` | `CustomerApiRequests.OrderRefList` | 是 |

返回：`ApiResponse<ItemResponse<StatusList>>`

#### `POST /api/customer-api/orders/query` → `orderQuery()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `body` | `@RequestBody` | `CustomerApiRequests.OrderRefList` | 是 |

返回：`ApiResponse<ItemResponse<OrderDetailList>>`

#### `GET /api/customer-api/channels` → `channels()`

返回：`ApiResponse<ItemResponse<ChannelList>>`

#### `POST /api/customer-api/tracking/query` → `trackingQuery()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `body` | `@RequestBody` | `CustomerApiRequests.OrderRefList` | 是 |

返回：`ApiResponse<ItemResponse<TrackingList>>`

#### `GET /api/customer-api/ping` → `ping()`

返回：`ApiResponse<ItemResponse<String>>`

### SumyController

_复刻 ACC acc/api/sumy.php 第三方推单入口。 走 customer-api 签名鉴权（CustomerApiPrincipal 由签名过滤器注入）。 路径对应文档建议：/api/customer-api/external-orders/sumy POST /api/customer-api/external-orders/sumy        → act=push 批量推单 GET  /api/customer-api/external-orders/sumy/channels → act=channel 列可用渠道 响应保持 sumy 兼容格式（PascalCase），_

**Base path**：`/api/customer-api/external-orders/sumy`

#### `GET /api/customer-api/external-orders/sumy/channels` → `channels()`

/** 复刻 ACC acc/api/sumy.php 第三方推单入口。 走 customer-api 签名鉴权（CustomerApiPrincipal 由签名过滤器注入）。 路径对应文档建议：/api/customer-api/external-orders/sumy POST /api/customer-api/external-orders/sumy        → act=push 批量推单 GET  /api/customer-api/external-orders/sumy/channels → act=channel 列可用渠道 响应保持 sumy 兼容格式（PascalCase），方便旧第三方客户端最小改造接入。 / public class SumyController { private final SumyService sumyService; private final CustomerApiService customerApiService; public SumyController(SumyService sumyService, CustomerApiService customerApiService) { this.sumyService = sumyService; this.customerApiService = customerApiService; } public Map<String, Object> push(@RequestBody Map<String, Object> body) { return sumyService.push(principal(), body); } /** 对应 sumy act=channel：列出客户可用渠道 [{ Name, Code, Logistics }]。 */

返回：`List<Map<String, Object>>`

---

## §03. 报价 / 计费

包路径：`com.xqt.saas.rates`

### DocumentRateController

**Base path**：`/api/document/rates`

（本 controller 无 HTTP 端点）

---

## §04. 面单

包路径：`com.xqt.saas.labels`

### LabelController

_把 ACC act=Label 和 api/getNewLabel.php 暴露为 customer-api 子路径。 走 CustomerApiAuthFilter 自动鉴权，路径必须在 /api/customer-api/** 下。_

**Base path**：`/api/customer-api/labels`

#### `POST /api/customer-api/labels/generate` → `generate()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `body` | `@RequestBody` | `LabelRequests.GenerateLabel` | 是 |

返回：`ApiResponse<ItemResponse<LabelBatch>>`

#### `POST /api/customer-api/labels/relabel` → `relabel()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `body` | `@RequestBody` | `LabelRequests.RelabelPdf` | 是 |

返回：`ApiResponse<ItemResponse<RelabelResult>>`

---

## §05. 电子秤 / 称重

包路径：`com.xqt.saas.scale`

### ScaleController

_复刻 ACC acc/api/Scale.php：电子秤设备接口（无 JWT，设备用 hid 自鉴权）。 POST /api/device/scale/{pluginCode}          → Goodscan doReceive：接收称重报文 GET  /api/device/scale/{pluginCode}/records  → Goodscan readData：列未签入记录 路径在 SecurityConfig permitAll，设备身份由 ScaleService 内的 hid 校验保证。 接收 @RequestBody String 原始报文（保留字节用于 md5 幂等_

**Base path**：`/api/device/scale`

#### `POST /api/device/scale/{pluginCode}` → `receive()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `pluginCode` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `GET /api/device/scale/{pluginCode}/records` → `records()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `pluginCode` | `@PathVariable` | `String` | 是 |

返回：`List<Map<String, Object>>`

---

## §06. 配载（客户 API）

包路径：`com.xqt.saas.stowage`

### StowageController

_对应 ACC api/APIClass.php?act=Sync。在 customer-api 前缀下，鉴权由 CustomerApiAuthFilter 自动覆盖。_

**Base path**：`/api/customer-api/stowages`

#### `POST /api/customer-api/stowages/sync` → `sync()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `body` | `@RequestBody` | `StowageRequests.SyncRequest` | 是 |

返回：`ApiResponse<ItemResponse<SyncResult>>`

---

## §07. 单据费用 / AR-AP

包路径：`com.xqt.saas.documentcharges`

### DocumentChargeController

_/api/document/charges /api/document/invoices —— ACC 闭环对外端点。_

**Base path**：`/api/document`

#### `POST /api/document/charges/generate-from-order` → `generate()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `body` | `@RequestBody` | `GenerateFromOrder` | 是 |

返回：`Object`

#### `POST /api/document/charges/{id}/void` → `voidCharge()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Object`

#### `POST /api/document/invoices/generate` → `generateInvoice()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `body` | `@RequestBody` | `GenerateCustomerInvoice` | 是 |

返回：`Object`

#### `POST /api/document/invoices/{id}/settle` → `settleInvoice()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `body` | `@RequestBody` | `SettleCustomerInvoice` | 是 |

返回：`Object`

#### `POST /api/document/partner-invoices/generate` → `generatePartnerInvoice()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `body` | `@RequestBody` | `GeneratePartnerInvoice` | 是 |

返回：`Object`

#### `POST /api/document/partner-invoices/{id}/settle` → `settlePartnerInvoice()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `body` | `@RequestBody` | `SettlePartnerInvoice` | 是 |

返回：`Object`

#### `GET /api/document/profits/summary` → `profitSummary()`

返回：`Map<String, Object>`

---

## §08. 公共轨迹（无鉴权）

包路径：`com.xqt.saas.publictracking`

### PublicTrackingController

_公开 Track 端点：对应 acc/api/Track.php。 安全语义和旧 ACC 一致 —— 单号即凭证，没有签名或 token；不在 /api/customer-api/** 前缀， 因此 {@link com.xqt.saas.customerapi.CustomerApiAuthFilter} 自动跳过。_

**Base path**：`/api/public/tracking`

#### `POST /api/public/tracking/query` → `query()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `body` | `@RequestBody` | `TrackRequest` | 是 |

返回：`ApiResponse<ItemResponse<TrackingResult>>`

---

## §09. 订单

包路径：`com.xqt.saas.orders`

### DocumentOrderController

**Base path**：`/api/document/orders`

（本 controller 无 HTTP 端点）

### SellerOrderController

**Base path**：`/api/seller/orders`

（本 controller 无 HTTP 端点）

---

## §10. 业务流

包路径：`com.xqt.saas.flows`

### BusinessFlowController

**Base path**：`/api/business-flows`

（本 controller 无 HTTP 端点）

---

## §11. 看板

包路径：`com.xqt.saas.dashboard`

### DashboardController

_前端 App.vue 老的 /api/finance/dashboard + /api/finance/branches 直接对接到这里。 不走 ApiResponse 包装，因为前端是按裸 JSON 解析的 (fetchOptionalJson)。_

**Base path**：`/api/finance`

#### `GET /api/finance/dashboard/health` → `dashboardHealth()`

/** 前端 App.vue 老的 /api/finance/dashboard + /api/finance/branches 直接对接到这里。 不走 ApiResponse 包装，因为前端是按裸 JSON 解析的 (fetchOptionalJson)。 / public class DashboardController { private final JdbcTemplate jdbc; private final JsonSupport json; public DashboardController(JdbcTemplate jdbc, JsonSupport json) { this.jdbc = jdbc; this.json = json; } public Map<String, Object> dashboard() { long orderCount = countOrFallback("SELECT count(*) FROM orders"); long shipmentCount = countOrFallback("SELECT count(*) FROM shipments"); BigDecimal receivable = sumOrZero(""" SELECT coalesce(sum(amount), 0) FROM customer_invoices """); BigDecimal payable = sumOrZero(""" SELECT coalesce(sum(amount), 0) FROM payments """); BigDecimal profit = receivable.subtract(payable); LocalDate today = LocalDate.now(); return Map.ofEntries( Map.entry("acc", Map.of( "label", "ACC 制单", "revenue", BigDecimal.ZERO, "cost", BigDecimal.ZERO, "profit", BigDecimal.ZERO, "orderCount", 0, "byBranch", List.of() )), Map.entry("xqt", Map.of( "label", "新智慧卖货", "revenue", BigDecimal.ZERO, "shipmentCount", 0, "invoiceCount", 0, "paid", BigDecimal.ZERO, "unpaid", BigDecimal.ZERO )), Map.entry("local", Map.of( "label", "新平台总览", "orders", orderCount, "shipments", shipmentCount, "receivable", receivable, "payable", payable, "profit", profit )), Map.entry("combined", Map.of( "totalRevenue", receivable, "totalCost", payable, "totalProfit", profit )), Map.entry("flows", List.of()), Map.entry("tracking", Map.of( "summary", Map.of( "activeShipments", 0, "exceptionCount", 0, "deliveredToday", 0, "trackedShipments", 0, "destinationCountries", 0 ), "routes", List.of() )), Map.entry("period", Map.of( "from", today.withDayOfMonth(1).toString(), "to", today.toString() )) ); } /** 给老 App.vue 用的 health 形状（upstreams.postgres/acc/xqt）。 /api/health 走 ApiResponse 包装，shape 不一样，前端 dashboard 不直接消费。 /

返回：`Map<String, Object>`

#### `GET /api/finance/branches` → `branches()`

返回：`Map<String, Object>`

---

## §12. 财务模块

包路径：`com.xqt.saas.finance.controller`

### FinanceAccountController

_账户控制器 提供账户的RESTful API接口_

**Base path**：`/api/finance-accounts`

#### `POST /api/finance-accounts` → `save()`

/** 账户控制器 提供账户的RESTful API接口 / public class FinanceAccountController { private FinanceAccountService financeAccountService; /** 保存账户 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceAccountSaveRequest` | 是 |

返回：`R`

#### `PUT /api/finance-accounts` → `update()`

/** 更新账户 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceAccountSaveRequest` | 是 |

返回：`R`

#### `DELETE /api/finance-accounts/{id}` → `delete()`

/** 删除账户 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `GET /api/finance-accounts/{id}` → `getById()`

/** 根据ID查询账户 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `POST /api/finance-accounts/page` → `page()`

/** 分页查询账户列表 /

返回：`R`

#### `POST /api/finance-accounts/list` → `list()`

/** 查询账户列表 /

返回：`R`

### FinanceAccountTransactionController

_账户流水控制器 提供账户流水的RESTful API接口_

**Base path**：`/api/finance-account-transactions`

#### `POST /api/finance-account-transactions` → `save()`

/** 账户流水控制器 提供账户流水的RESTful API接口 / public class FinanceAccountTransactionController { private FinanceAccountTransactionService financeAccountTransactionService; /** 保存账户流水 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceAccountTransactionSaveRequest` | 是 |

返回：`R`

#### `PUT /api/finance-account-transactions` → `update()`

/** 更新账户流水 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceAccountTransactionSaveRequest` | 是 |

返回：`R`

#### `DELETE /api/finance-account-transactions/{id}` → `delete()`

/** 删除账户流水 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `GET /api/finance-account-transactions/{id}` → `getById()`

/** 根据ID查询账户流水 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `GET /api/finance-account-transactions/page` → `page()`

/** 分页查询账户流水列表 /

返回：`R`

#### `GET /api/finance-account-transactions/list` → `list()`

/** 查询账户流水列表 /

返回：`R`

### FinanceApprovalController

**Base path**：`/api/finance-approvals`

#### `POST /api/finance-approvals` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `req` | `@RequestBody` | `FinanceApprovalSaveRequest` | 是 |

返回：`R`

#### `DELETE /api/finance-approvals/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `GET /api/finance-approvals` → `getAll()`

返回：`R`

#### `GET /api/finance-approvals/page` → `getPage()`

返回：`R`

### FinanceCurrencyController

**Base path**：`/api/finance-currencies`

#### `POST /api/finance-currencies` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `req` | `@RequestBody` | `FinanceCurrencySaveRequest` | 是 |

返回：`R`

#### `DELETE /api/finance-currencies/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `GET /api/finance-currencies` → `getAll()`

返回：`R`

#### `GET /api/finance-currencies/page` → `pageCurrencies()`

返回：`R`

#### `POST /api/finance-currencies/{id}` → `createExchange()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |
| `req` | `@RequestBody` | `FinanceCurrencyExchangeSaveRequest` | 是 |

返回：`R`

#### `DELETE /api/finance-currencies/{currencyId}/{exchangeId}` → `deleteExchange()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `currencyId` | `@PathVariable` | `Long` | 是 |
| `exchangeId` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `GET /api/finance-currencies/{id}` → `pageCurrencyExchanges()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

### FinanceCustomerBillController

_客户账单控制器 提供客户账单的RESTful API接口_

**Base path**：`/api/finance-customer-bills`

#### `POST /api/finance-customer-bills` → `save()`

/** 客户账单控制器 提供客户账单的RESTful API接口 / public class FinanceCustomerBillController { private FinanceCustomerBillService financeCustomerBillService; /** 保存客户账单 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceCustomerBillSaveRequest` | 是 |

返回：`R`

#### `PUT /api/finance-customer-bills` → `update()`

/** 更新客户账单 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceCustomerBillSaveRequest` | 是 |

返回：`R`

#### `DELETE /api/finance-customer-bills/{id}` → `delete()`

/** 删除客户账单 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `GET /api/finance-customer-bills/{id}` → `getById()`

/** 根据ID查询客户账单 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `POST /api/finance-customer-bills/page` → `page()`

/** 分页查询客户账单列表 /

返回：`R`

#### `POST /api/finance-customer-bills/list` → `list()`

/** 查询客户账单列表 /

返回：`R`

#### `POST /api/finance-customer-bills/import` → `importExcel()`

/** 导入客户账单Excel /

返回：`R`

#### `GET /api/finance-customer-bills/export` → `exportExcel()`

/** 导出客户账单Excel /

返回：`ResponseEntity<byte[]>`

### FinanceCustomerTransactionController

_客户流水控制器 提供客户流水的RESTful API接口_

**Base path**：`/api/finance-customer-transactions`

#### `POST /api/finance-customer-transactions` → `save()`

/** 客户流水控制器 提供客户流水的RESTful API接口 / public class FinanceCustomerTransactionController { private FinanceCustomerTransactionService financeCustomerTransactionService; /** 保存客户流水 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceCustomerTransactionSaveRequest` | 是 |

返回：`R`

#### `PUT /api/finance-customer-transactions` → `update()`

/** 更新客户流水 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceCustomerTransactionSaveRequest` | 是 |

返回：`R`

#### `DELETE /api/finance-customer-transactions/{id}` → `delete()`

/** 删除客户流水 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `GET /api/finance-customer-transactions/{id}` → `getById()`

/** 根据ID查询客户流水 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `POST /api/finance-customer-transactions/page` → `page()`

/** 分页查询客户流水列表 /

返回：`R`

#### `POST /api/finance-customer-transactions/list` → `list()`

/** 查询客户流水列表 /

返回：`R`

#### `POST /api/finance-customer-transactions/import` → `importExcel()`

/** 导入客户流水Excel /

返回：`R`

#### `GET /api/finance-customer-transactions/export` → `exportExcel()`

/** 导出客户流水Excel /

返回：`ResponseEntity<byte[]>`

### FinanceFeeTypeController

_费用类型控制器 提供费用类型的RESTful API接口_

**Base path**：`/api/finance-fee-types`

#### `POST /api/finance-fee-types/save` → `save()`

/** 费用类型控制器 提供费用类型的RESTful API接口 / public class FinanceFeeTypeController { private FinanceFeeTypeService financeFeeTypeService; /** 保存费用类型 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceFeeTypeSaveRequest` | 是 |

返回：`R`

#### `PUT /api/finance-fee-types` → `update()`

/** 更新费用类型 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceFeeTypeSaveRequest` | 是 |

返回：`R`

#### `DELETE /api/finance-fee-types/{id}` → `delete()`

/** 删除费用类型 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `GET /api/finance-fee-types/{id}` → `getById()`

/** 根据ID查询费用类型 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `POST /api/finance-fee-types/page` → `page()`

/** 分页查询费用类型列表 /

返回：`R`

#### `POST /api/finance-fee-types/list` → `list()`

/** 查询费用类型列表 /

返回：`R`

#### `POST /api/finance-fee-types/import` → `importExcel()`

/** 导入费用类型Excel /

返回：`R`

#### `GET /api/finance-fee-types/export` → `exportExcel()`

/** 导出费用类型Excel /

返回：`ResponseEntity<byte[]>`

### FinanceMonthlyStatementController

_月结单控制器 提供月结单的RESTful API接口_

**Base path**：`/api/finance-monthly-statements`

#### `POST /api/finance-monthly-statements` → `save()`

/** 月结单控制器 提供月结单的RESTful API接口 / public class FinanceMonthlyStatementController { private FinanceMonthlyStatementService financeMonthlyStatementService; /** 保存月结单 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceMonthlyStatementSaveRequest` | 是 |

返回：`R`

#### `PUT /api/finance-monthly-statements` → `update()`

/** 更新月结单 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceMonthlyStatementSaveRequest` | 是 |

返回：`R`

#### `DELETE /api/finance-monthly-statements/{id}` → `delete()`

/** 删除月结单 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `GET /api/finance-monthly-statements/{id}` → `getById()`

/** 根据ID查询月结单 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `POST /api/finance-monthly-statements/page` → `page()`

/** 分页查询月结单列表 /

返回：`R`

#### `POST /api/finance-monthly-statements/list` → `list()`

/** 查询月结单列表 /

返回：`R`

### FinancePayableReportController

_应付报表控制器 提供应付报表的RESTful API接口_

**Base path**：`/api/finance-payable-reports`

#### `POST /api/finance-payable-reports` → `save()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinancePayableReportSaveRequest` | 是 |

返回：`R`

#### `PUT /api/finance-payable-reports` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinancePayableReportSaveRequest` | 是 |

返回：`R`

#### `DELETE /api/finance-payable-reports/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `GET /api/finance-payable-reports/{id}` → `getById()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `POST /api/finance-payable-reports/page` → `page()`

返回：`R`

#### `POST /api/finance-payable-reports/list` → `list()`

返回：`R`

#### `GET /api/finance-payable-reports/export` → `exportExcel()`

返回：`ResponseEntity<byte[]>`

### FinancePriceMaintenanceController

_运价维护控制器 提供运价维护的RESTful API接口_

**Base path**：`/api/finance-price-maintenances`

#### `POST /api/finance-price-maintenances` → `save()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinancePriceMaintenanceSaveRequest` | 是 |

返回：`R`

#### `PUT /api/finance-price-maintenances` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinancePriceMaintenanceSaveRequest` | 是 |

返回：`R`

#### `DELETE /api/finance-price-maintenances/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `GET /api/finance-price-maintenances/{id}` → `getById()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `POST /api/finance-price-maintenances/page` → `page()`

返回：`R`

#### `POST /api/finance-price-maintenances/list` → `list()`

返回：`R`

### FinanceReceivableReportController

_应收报表控制器 提供应收报表的RESTful API接口_

**Base path**：`/api/finance-receivable-reports`

#### `POST /api/finance-receivable-reports` → `save()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceReceivableReportSaveRequest` | 是 |

返回：`R`

#### `PUT /api/finance-receivable-reports` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceReceivableReportSaveRequest` | 是 |

返回：`R`

#### `DELETE /api/finance-receivable-reports/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `GET /api/finance-receivable-reports/{id}` → `getById()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `POST /api/finance-receivable-reports/page` → `page()`

返回：`R`

#### `POST /api/finance-receivable-reports/list` → `list()`

返回：`R`

#### `GET /api/finance-receivable-reports/export` → `exportExcel()`

返回：`ResponseEntity<byte[]>`

### FinanceSalesCommissionController

_销售提成单 提供销售提成单的RESTful API接口_

**Base path**：`/api/finance-sales-commissions`

#### `POST /api/finance-sales-commissions` → `save()`

/** 销售提成单 提供销售提成单的RESTful API接口 / public class FinanceSalesCommissionController { private FinanceSalesCommissionService financeSalesCommissionService; /** 保存销售提成单 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceSalesCommissionSaveRequest` | 是 |

返回：`R`

#### `PUT /api/finance-sales-commissions` → `update()`

/** 更新销售提成单 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceSalesCommissionSaveRequest` | 是 |

返回：`R`

#### `DELETE /api/finance-sales-commissions/{id}` → `delete()`

/** 删除销售提成单 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `GET /api/finance-sales-commissions/{id}` → `getById()`

/** 根据ID查询销售提成单 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `POST /api/finance-sales-commissions/page` → `page()`

/** 分页查询销售提成单列表 /

返回：`R`

#### `POST /api/finance-sales-commissions/list` → `list()`

/** 查询销售提成单列表 /

返回：`R`

#### `GET /api/finance-sales-commissions/export` → `exportExcel()`

/** 导出销售提成单Excel /

返回：`ResponseEntity<byte[]>`

### FinanceSalesCommissionTransactionController

_销售提成流水控制器 提供销售提成流水的RESTful API接口_

**Base path**：`/api/finance-sales-commission-transactions`

#### `POST /api/finance-sales-commission-transactions` → `save()`

/** 销售提成流水控制器 提供销售提成流水的RESTful API接口 / public class FinanceSalesCommissionTransactionController { private FinanceSalesCommissionTransactionService financeSalesCommissionTransactionService; /** 保存销售提成流水 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceSalesCommissionTransactionSaveRequest` | 是 |

返回：`R`

#### `PUT /api/finance-sales-commission-transactions` → `update()`

/** 更新销售提成流水 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceSalesCommissionTransactionSaveRequest` | 是 |

返回：`R`

#### `DELETE /api/finance-sales-commission-transactions/{id}` → `delete()`

/** 删除销售提成流水 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `GET /api/finance-sales-commission-transactions/{id}` → `getById()`

/** 根据ID查询销售提成流水 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `POST /api/finance-sales-commission-transactions/page` → `page()`

/** 分页查询销售提成流水列表 /

返回：`R`

#### `POST /api/finance-sales-commission-transactions/list` → `list()`

/** 查询销售提成流水列表 /

返回：`R`

#### `GET /api/finance-sales-commission-transactions/export` → `exportExcel()`

/** 导出销售提成流水Excel /

返回：`ResponseEntity<byte[]>`

### FinanceSalesCostTransactionController

_销售成本流水控制器 提供销售成本流水的RESTful API接口_

**Base path**：`/api/finance-sales-cost-transactions`

#### `POST /api/finance-sales-cost-transactions` → `save()`

/** 销售成本流水控制器 提供销售成本流水的RESTful API接口 / public class FinanceSalesCostTransactionController { private FinanceSalesCostTransactionService financeSalesCostTransactionService; /** 保存销售成本流水 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceSalesCostTransactionSaveRequest` | 是 |

返回：`R`

#### `PUT /api/finance-sales-cost-transactions` → `update()`

/** 更新销售成本流水 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceSalesCostTransactionSaveRequest` | 是 |

返回：`R`

#### `DELETE /api/finance-sales-cost-transactions/{id}` → `delete()`

/** 删除销售成本流水 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `GET /api/finance-sales-cost-transactions/{id}` → `getById()`

/** 根据ID查询销售成本流水 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `POST /api/finance-sales-cost-transactions/page` → `page()`

/** 分页查询销售成本流水列表 /

返回：`R`

#### `POST /api/finance-sales-cost-transactions/list` → `list()`

/** 查询销售成本流水列表 /

返回：`R`

#### `POST /api/finance-sales-cost-transactions/import` → `importExcel()`

/** 导入销售成本流水Excel /

返回：`R`

#### `GET /api/finance-sales-cost-transactions/export` → `exportExcel()`

/** 导出销售成本流水Excel /

返回：`ResponseEntity<byte[]>`

### FinanceSupplierBillController

_供应商账单控制器 提供供应商账单的RESTful API接口_

**Base path**：`/api/finance-supplier-bills`

#### `POST /api/finance-supplier-bills` → `save()`

/** 供应商账单控制器 提供供应商账单的RESTful API接口 / public class FinanceSupplierBillController { private FinanceSupplierBillService financeSupplierBillService; /** 保存供应商账单 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceSupplierBillSaveRequest` | 是 |

返回：`R`

#### `PUT /api/finance-supplier-bills` → `update()`

/** 更新供应商账单 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceSupplierBillSaveRequest` | 是 |

返回：`R`

#### `DELETE /api/finance-supplier-bills/{id}` → `delete()`

/** 删除供应商账单 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `GET /api/finance-supplier-bills/{id}` → `getById()`

/** 根据ID查询供应商账单 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `POST /api/finance-supplier-bills/page` → `page()`

/** 分页查询供应商账单列表 /

返回：`R`

#### `POST /api/finance-supplier-bills/list` → `list()`

/** 查询供应商账单列表 /

返回：`R`

#### `GET /api/finance-supplier-bills/export` → `exportExcel()`

/** 导出供应商账单Excel /

返回：`ResponseEntity<byte[]>`

### FinanceSupplierTransactionController

_供应商流水控制器 提供供应商流水的RESTful API接口_

**Base path**：`/api/finance-supplier-transactions`

#### `POST /api/finance-supplier-transactions` → `save()`

/** 供应商流水控制器 提供供应商流水的RESTful API接口 / public class FinanceSupplierTransactionController { private FinanceSupplierTransactionService financeSupplierTransactionService; /** 保存供应商流水 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceSupplierTransactionSaveRequest` | 是 |

返回：`R`

#### `PUT /api/finance-supplier-transactions` → `update()`

/** 更新供应商流水 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceSupplierTransactionSaveRequest` | 是 |

返回：`R`

#### `DELETE /api/finance-supplier-transactions/{id}` → `delete()`

/** 删除供应商流水 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `GET /api/finance-supplier-transactions/{id}` → `getById()`

/** 根据ID查询供应商流水 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `POST /api/finance-supplier-transactions/page` → `page()`

/** 分页查询供应商流水列表 /

返回：`R`

#### `POST /api/finance-supplier-transactions/list` → `list()`

/** 查询供应商流水列表 /

返回：`R`

#### `POST /api/finance-supplier-transactions/import` → `importExcel()`

/** 导入供应商流水Excel /

返回：`R`

#### `GET /api/finance-supplier-transactions/export` → `exportExcel()`

/** 导出供应商流水Excel /

返回：`ResponseEntity<byte[]>`

### FinanceTransactionController

_财务流水控制器 提供财务流水的RESTful API接口_

**Base path**：`/api/finance-transactions`

#### `POST /api/finance-transactions` → `save()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceTransactionSaveRequest` | 是 |

返回：`R`

#### `PUT /api/finance-transactions` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceTransactionSaveRequest` | 是 |

返回：`R`

#### `DELETE /api/finance-transactions/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `GET /api/finance-transactions/{id}` → `getById()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `POST /api/finance-transactions/page` → `page()`

返回：`R`

#### `POST /api/finance-transactions/list` → `list()`

返回：`R`

#### `POST /api/finance-transactions/import` → `importExcel()`

返回：`R`

#### `GET /api/finance-transactions/export` → `exportExcel()`

返回：`ResponseEntity<byte[]>`

### FinanceWaybillAuditController

_运单审计控制器 提供运单审计的RESTful API接口_

**Base path**：`/api/finance-waybill-audits`

#### `POST /api/finance-waybill-audits` → `save()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceWaybillAuditSaveRequest` | 是 |

返回：`R`

#### `PUT /api/finance-waybill-audits` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `request` | `@RequestBody` | `FinanceWaybillAuditSaveRequest` | 是 |

返回：`R`

#### `DELETE /api/finance-waybill-audits/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `GET /api/finance-waybill-audits/{id}` → `getById()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`R`

#### `POST /api/finance-waybill-audits/page` → `page()`

返回：`R`

#### `POST /api/finance-waybill-audits/list` → `list()`

返回：`R`

#### `POST /api/finance-waybill-audits/import` → `importExcel()`

返回：`R`

#### `GET /api/finance-waybill-audits/export` → `exportExcel()`

返回：`ResponseEntity<byte[]>`

---

## §13. ACC 业务（80 个 controller）

包路径：`com.xqt.saas.acc`

### AccAsksController

_/api/acc/asks — 问题件，前端列：expressNo / content / source / type / status / addName / addTime。_

**Base path**：`/api/acc/asks`

#### `GET /api/acc/asks` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/asks/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/asks` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/asks/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/asks/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccAssetsController

**Base path**：`/api/acc/assets`

#### `GET /api/acc/assets` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/assets/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/assets` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/assets/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/assets/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccAttendancesController

**Base path**：`/api/acc/attendances`

#### `GET /api/acc/attendances` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/attendances/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/attendances` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/attendances/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/attendances/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccAuditController

_通用 ACC 审核流入口。前端 fetchAccBiz / batchAudit 会直接 POST 这里： POST /api/acc/{tab}/{id}/audit-biz POST /api/acc/{tab}/{id}/undo-biz POST /api/acc/{tab}/batch-audit         body: { ids: ["...", ...] } GET  /api/acc/{tab}/{id}/audit-history 这里把 tab 名映射到底层 table 名（绝大部分一致，只有 acc-branches → organizations 等几条例外）。_

**Base path**：`/api/acc`

#### `POST /api/acc/{tab}/{id}/audit-biz` → `audit()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `tab` | `@PathVariable` | `String` | 是 |
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/{tab}/{id}/undo-biz` → `undo()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `tab` | `@PathVariable` | `String` | 是 |
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/{tab}/batch-audit` → `batchAudit()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `tab` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `GET /api/acc/{tab}/{id}/audit-history` → `history()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `tab` | `@PathVariable` | `String` | 是 |
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccBankNamesController

_/api/acc/bank-names — 前端列：name / remark。_

**Base path**：`/api/acc/bank-names`

#### `GET /api/acc/bank-names` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/bank-names/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/bank-names` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/bank-names/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/bank-names/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccBanksController

_/api/acc/banks — 复用 financial_accounts，只取 account_type='BANK' 的行作为银行账户视图。_

**Base path**：`/api/acc/banks`

#### `GET /api/acc/banks` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/banks/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/banks` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/banks/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/banks/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccBillsController

_/api/acc/bills — 前端列：no / customerName / settlement / theDate / amount / paid / unpay / quantity / status / salesman 来源：customer_invoices + customers join。paid 由 payments.amount 聚合（按 reference_no=invoice_no）。_

**Base path**：`/api/acc/bills`

#### `GET /api/acc/bills/{id}/items` → `items()`

/** /api/acc/bills — 前端列：no / customerName / settlement / theDate / amount / paid / unpay / quantity / status / salesman 来源：customer_invoices + customers join。paid 由 payments.amount 聚合（按 reference_no=invoice_no）。 / public class AccBillsController { private static final String TABLE = "customer_invoices"; private final JdbcTemplate jdbc; private final JsonSupport json; private final CascadeChecker cascadeChecker; private final FieldGate fieldGate; private final com.xqt.saas.documentcharges.DocumentChargeService docService; public AccBillsController(JdbcTemplate jdbc, JsonSupport json, CascadeChecker cascadeChecker, FieldGate fieldGate, com.xqt.saas.documentcharges.DocumentChargeService docService) { this.jdbc = jdbc; this.json = json; this.cascadeChecker = cascadeChecker; this.fieldGate = fieldGate; this.docService = docService; } public Map<String, Object> list( @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String keyword, @RequestParam(required = false) String dateFrom, @RequestParam(required = false) String dateTo ) { try { int limit = AccPaging.pageSize(pageSize); int offset = AccPaging.offset(page, pageSize); String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%"; Long total = jdbc.queryForObject(""" SELECT count(*) FROM customer_invoices i WHERE (?::text IS NULL OR i.invoice_no ILIKE ?) AND (?::date IS NULL OR i.issued_at >= ?::date) AND (?::date IS NULL OR i.issued_at < (?::date + 1)) """, Long.class, search, search, dateFrom, dateFrom, dateTo, dateTo); List<Map<String, Object>> rows = jdbc.queryForList(""" SELECT i.id::text       AS id, i.invoice_no, i.template_code, i.currency, i.total_amount, i.status, i.issued_at, i.audit_status, i.audited_at, i.audit_name, c.name           AS customer_name, c.account_mode   AS settlement, u.display_name   AS salesman_name, ( SELECT coalesce(sum(p.amount), 0) FROM payments p WHERE p.tenant_id = i.tenant_id AND p.reference_no = i.invoice_no ) AS paid_amount, ( SELECT count(*) FROM customer_invoice_lines l WHERE l.invoice_id = i.id ) AS line_count FROM customer_invoices i LEFT JOIN customers c ON c.id = i.customer_id LEFT JOIN users u ON u.id = c.salesman_user_id WHERE (?::text IS NULL OR i.invoice_no ILIKE ?) AND (?::date IS NULL OR i.issued_at >= ?::date) AND (?::date IS NULL OR i.issued_at < (?::date + 1)) ORDER BY i.issued_at DESC NULLS LAST, i.invoice_no LIMIT ? OFFSET ? """, search, search, dateFrom, dateFrom, dateTo, dateTo, limit, offset); return AccPaging.result(rows.stream().map(this::project).toList(), total == null ? 0 : total); } catch (DataAccessException ex) { return AccPaging.result(List.of(), 0); } } public Map<String, Object> raw(@PathVariable String id) { List<Map<String, Object>> rows = jdbc.queryForList( "SELECT * FROM customer_invoices WHERE id = ?::uuid LIMIT 1", id); return rows.isEmpty() ? Map.of() : json.row(rows.get(0)); } /** 对应前端 "查看账单明细" — bills/{id}/items 拉 customer_invoice_lines。 */

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/bills` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/bills/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/bills/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/bills/generate` → `generate()`

/** 前端 "生成账单"。统一走 documentcharges service（消除双轨：以前前端调这个但后端没实现）。 入参兼容旧前端 { customerId, dateFrom, dateTo, currency? }，UUID 与 integer 都接受。 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/bills/reload` → `reload()`

/** 前端 "账单重算"：作废原账单 + 用同客户/日期段重新生成。 旧前端只传 { id }，从原账单读出客户和日期段。 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

### AccBorrowingsController

**Base path**：`/api/acc/borrowings`

#### `GET /api/acc/borrowings` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/borrowings/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/borrowings` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/borrowings/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/borrowings/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccBranchesController

_/api/acc/branches — 前端 ACC tab "acc-branches"，列：name / code / contact / phone / address / remark 来源 organizations WHERE org_type='branch'。contact/phone/address/remark 新模型未建模，先空值。_

**Base path**：`/api/acc/branches`

#### `GET /api/acc/branches` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/branches/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/branches` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/branches/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/branches/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccChannelAccountsController

**Base path**：`/api/acc/channel-accounts`

#### `GET /api/acc/channel-accounts` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/channel-accounts/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/channel-accounts` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/channel-accounts/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/channel-accounts/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccChannelsController

_/api/acc/channels — 对应前端 ACC tab "channels"。读 channels 表。 前端列：name, code, isOpen, isDebug, remark；DB 没有 isDebug/remark，分别用 false / lane 兜底。_

**Base path**：`/api/acc/channels`

#### `GET /api/acc/channels` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/channels/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/channels` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/channels/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/channels/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccChargesController

_/api/acc/charges — AR 应收，前端列：expressNo / customerName / productName / country / chargeWeight / type / amount / paid / theDate / auditName 来源：charges WHERE side='AR' + shipments + customers + channels + charge_items。_

**Base path**：`/api/acc/charges`

#### `GET /api/acc/charges` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/charges/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/charges` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/charges/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/charges/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccCollectsController

_/api/acc/collects — 总单/留仓，前端列：no / piece / weight / status / remark / addName / addTime。_

**Base path**：`/api/acc/collects`

#### `GET /api/acc/collects` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/collects/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/collects` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/collects/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/collects/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccCommissionRulesController

**Base path**：`/api/acc/commission-rules`

#### `GET /api/acc/commission-rules` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/commission-rules/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/commission-rules` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/commission-rules/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/commission-rules/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccCommissionsController

**Base path**：`/api/acc/commissions`

#### `GET /api/acc/commissions` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/commissions/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/commissions` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/commissions/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/commissions/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccCostsController

_/api/acc/costs — AP 应付，前端列：expressNo / supplierName / channelName / country / channelWeight / type / amount / paid / theDate / auditName 来源：charges WHERE side='AP' + shipments + channels + charge_items + partner_payments 聚合实付。 supplierName 暂从 channels.last_mile_method 兜底（旧 partners 关联在 channel_cost__

**Base path**：`/api/acc/costs`

#### `GET /api/acc/costs` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/costs/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/costs` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/costs/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/costs/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccCountriesController

_/api/acc/countries — 前端列：cn / name / code / isOpen。_

**Base path**：`/api/acc/countries`

#### `GET /api/acc/countries` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/countries/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/countries` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/countries/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/countries/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccCurrenciesController

_/api/acc/currencies — 对应前端 ACC tab "currencies"。 读 finance_currency 表（id BIGINT，注意与 UUID 表的区别）。 前端列 symbol/rate/decimal 在新表里没建模，分别给 ''/1/2 兜底。_

**Base path**：`/api/acc/currencies`

#### `GET /api/acc/currencies` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/currencies/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/currencies` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/currencies/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/currencies/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `Long` | 是 |

返回：`Map<String, Object>`

### AccCustomerAdjustsController

**Base path**：`/api/acc/customer-adjusts`

#### `GET /api/acc/customer-adjusts` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/customer-adjusts/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/customer-adjusts` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/customer-adjusts/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/customer-adjusts/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccCustomerFinesController

_/api/acc/customer-fines —— acc_fines WHERE side='CUSTOMER'。_

**Base path**：`/api/acc/customer-fines`

#### `GET /api/acc/customer-fines` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/customer-fines/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/customer-fines` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/customer-fines/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/customer-fines/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccCustomerGroupsController

_/api/acc/customer-groups — 前端列：name / remark。_

**Base path**：`/api/acc/customer-groups`

#### `GET /api/acc/customer-groups` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/customer-groups/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/customer-groups` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/customer-groups/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/customer-groups/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccCustomerRebatesController

**Base path**：`/api/acc/customer-rebates`

#### `GET /api/acc/customer-rebates` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/customer-rebates/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/customer-rebates` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/customer-rebates/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/customer-rebates/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccCustomerRefundsController

**Base path**：`/api/acc/customer-refunds`

#### `GET /api/acc/customer-refunds` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/customer-refunds/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/customer-refunds` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/customer-refunds/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/customer-refunds/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccCustomersController

_/api/acc/customers — 对应前端 ACC tab "customers"。读 customers 表， 把 DB 字段映射到前端列名（contact/mobile/balance/settlement/branch/group/salesman 都是新平台没建模的字段，先返回空值）。_

**Base path**：`/api/acc/customers`

#### `GET /api/acc/customers` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/customers/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/customers` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/customers/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/customers/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccCyclesController

**Base path**：`/api/acc/cycles`

#### `GET /api/acc/cycles` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/cycles/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/cycles` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/cycles/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/cycles/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccDepartmentsController

_/api/acc/departments — 前端列：name / branchName / remark 来源 organizations WHERE org_type='department'，branchName 由 parent_id 自连接得到。_

**Base path**：`/api/acc/departments`

#### `GET /api/acc/departments` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/departments/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/departments` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/departments/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/departments/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccDetainsController

_/api/acc/detains — 扣件，前端列：no / customerName / type / status / reason / addName / addTime。_

**Base path**：`/api/acc/detains`

#### `GET /api/acc/detains` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/detains/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/detains` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/detains/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/detains/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccDispatchesController

**Base path**：`/api/acc/dispatches`

#### `GET /api/acc/dispatches` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/dispatches/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/dispatches` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/dispatches/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/dispatches/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccDistrictsController

_/api/acc/districts — 前端列：name / cn / code2 / code3 / phone（旧 ACC 是国家代码+区号；新表用层级行政区，做最简映射）。_

**Base path**：`/api/acc/districts`

#### `GET /api/acc/districts` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/districts/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/districts` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/districts/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/districts/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccDividendsController

**Base path**：`/api/acc/dividends`

#### `GET /api/acc/dividends` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/dividends/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/dividends` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/dividends/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/dividends/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccEmployeesController

**Base path**：`/api/acc/employees`

#### `GET /api/acc/employees` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/employees/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/employees` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/employees/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/employees/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccExpenseCategoriesController

_/api/acc/expense-categories — 前端列：name / type / isComing / remark。_

**Base path**：`/api/acc/expense-categories`

#### `GET /api/acc/expense-categories` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/expense-categories/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/expense-categories` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/expense-categories/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/expense-categories/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccExpensesController

**Base path**：`/api/acc/expenses`

#### `GET /api/acc/expenses` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/expenses/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/expenses` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/expenses/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/expenses/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccFeeItemTypesController

_/api/acc/fee-item-types — 前端列：name / type / color / remark。_

**Base path**：`/api/acc/fee-item-types`

#### `GET /api/acc/fee-item-types` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/fee-item-types/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/fee-item-types` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/fee-item-types/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/fee-item-types/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccFeeTypesController

_/api/acc/fee-types — 前端列：name / type / unit / method / remark 来源 charge_items：name=name、type=category、unit=default_uom、method=default_side。 注意路径包含连字符，Spring 的 RequestMapping 支持。_

**Base path**：`/api/acc/fee-types`

#### `GET /api/acc/fee-types` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/fee-types/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/fee-types` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/fee-types/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/fee-types/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccFeesController

_/api/acc/fees — 杂费套餐，前端列：name / itemCount / linkedProducts / remark。_

**Base path**：`/api/acc/fees`

#### `GET /api/acc/fees` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/fees/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/fees` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/fees/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/fees/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccForecastsController

**Base path**：`/api/acc/forecasts`

#### `GET /api/acc/forecasts` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/forecasts/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/forecasts` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/forecasts/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/forecasts/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccFuelsController

_/api/acc/fuels — 前端列：name / rate / startDate / endDate 来源 fuel_surcharge_rates + channels.name 当 name；start/end 从 year_month 推断（当月 1 号 ~ 月末）。_

**Base path**：`/api/acc/fuels`

#### `GET /api/acc/fuels` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/fuels/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/fuels` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/fuels/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/fuels/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccFundPersonsController

**Base path**：`/api/acc/fund-persons`

#### `GET /api/acc/fund-persons` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/fund-persons/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/fund-persons` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/fund-persons/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/fund-persons/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccFundsController

**Base path**：`/api/acc/funds`

#### `GET /api/acc/funds` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/funds/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/funds` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/funds/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/funds/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccHscodesController

_/api/acc/hscodes — 前端列：code / nameEN / nameCN。_

**Base path**：`/api/acc/hscodes`

#### `GET /api/acc/hscodes` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/hscodes/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/hscodes` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/hscodes/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/hscodes/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccLogisticsInterfacesController

**Base path**：`/api/acc/logistics-interfaces`

#### `GET /api/acc/logistics-interfaces` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/logistics-interfaces/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/logistics-interfaces` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/logistics-interfaces/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/logistics-interfaces/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccMessageTemplatesController

**Base path**：`/api/acc/templates`

#### `GET /api/acc/templates` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/templates/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/templates` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/templates/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/templates/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccNoticesController

**Base path**：`/api/acc/notices`

#### `GET /api/acc/notices` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/notices/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/notices` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/notices/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/notices/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccOrdersController

_/api/acc/orders — 前端列：orderNo / trackNo / customerName / product / country / piece / chargeWeight / sellCharge / costCharge / branch / addTime 数据来源：orders + customers join，cartons 聚合件数，shipments 一对一拿 country。 sellCharge = SUM(charges where side='AR' and settlement_status<>'VOID') by customer_ref joi_

**Base path**：`/api/acc/orders`

#### `GET /api/acc/orders` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/orders/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/orders` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/orders/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/orders/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccPackagesController

_/api/acc/packages — 装箱单。前端列：no / theDate / consignee / company / country / piece / quantity / declaredValue / postcode 新模型里没有独立 Online_Package 表；每张 shipment 自带 cartons + declarations，相当于一个装箱单。 收件人/邮编/公司从 orders.metadata.acc_compat.receiver 还原（API 下单时存在那里）。_

**Base path**：`/api/acc/packages`

#### `GET /api/acc/packages` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/packages/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/packages` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/packages/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/packages/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccPaymentsController

_/api/acc/payments — 前端列：no / supplierName / bankName / amount / theDate / auditName / remark AP 付款，来源 partner_payments + partners。_

**Base path**：`/api/acc/payments`

#### `GET /api/acc/payments` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/payments/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/payments` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/payments/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/payments/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccPortsController

**Base path**：`/api/acc/ports`

#### `GET /api/acc/ports` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/ports/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/ports` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/ports/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/ports/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccPostcodesController

_/api/acc/postcodes — 前端列：postcode / country / province / city。_

**Base path**：`/api/acc/postcodes`

#### `GET /api/acc/postcodes` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/postcodes/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/postcodes` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/postcodes/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/postcodes/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccPotentialsController

**Base path**：`/api/acc/potentials`

#### `GET /api/acc/potentials` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/potentials/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/potentials` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/potentials/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/potentials/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccProductItemsController

**Base path**：`/api/acc/product-items`

#### `GET /api/acc/product-items` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/product-items/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/product-items` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/product-items/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/product-items/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccProductsController

_/api/acc/products — 价格表聚合视图，join rate_cards + service_channel_links + services。 只读，无 audit 流（价格表由 rate_cards 本身管理）。_

**Base path**：`/api/acc/products`

#### `GET /api/acc/products` → `list()`

返回：`Map<String, Object>`

### AccProfitsController

_/api/acc/profits — 利润查询。前端列：no / customerName / productName / country / chargeWeight / channelWeight / revenue / cost / profit / theDate 数据来源：按 shipment 聚合 charges 表 AR / AP 差值。视图聚合，无 CRUD。 另外 `/summary?dateFrom=&dateTo=&groupBy=` 给前端 "利润汇总" 弹窗用。_

**Base path**：`/api/acc/profits`

#### `GET /api/acc/profits/summary` → `summary()`

/** /api/acc/profits — 利润查询。前端列：no / customerName / productName / country / chargeWeight / channelWeight / revenue / cost / profit / theDate 数据来源：按 shipment 聚合 charges 表 AR / AP 差值。视图聚合，无 CRUD。 另外 `/summary?dateFrom=&dateTo=&groupBy=` 给前端 "利润汇总" 弹窗用。 / public class AccProfitsController { private final JdbcTemplate jdbc; private final JsonSupport json; public AccProfitsController(JdbcTemplate jdbc, JsonSupport json) { this.jdbc = jdbc; this.json = json; } public Map<String, Object> list( @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String keyword, @RequestParam(required = false) String dateFrom, @RequestParam(required = false) String dateTo ) { try { int limit = AccPaging.pageSize(pageSize); int offset = AccPaging.offset(page, pageSize); String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%"; // 每个 shipment 一行：AR/AP sum + 客户/渠道/国家。计费重/渠道重都来自 cartons。 Long total = jdbc.queryForObject(""" SELECT count(DISTINCT s.id) FROM shipments s WHERE (?::text IS NULL OR s.shipment_no ILIKE ? OR s.customer_ref ILIKE ?) AND (?::date IS NULL OR s.created_at >= ?::date) AND (?::date IS NULL OR s.created_at < (?::date + 1)) AND EXISTS (SELECT 1 FROM charges ch WHERE ch.shipment_id = s.id) """, Long.class, search, search, search, dateFrom, dateFrom, dateTo, dateTo); List<Map<String, Object>> rows = jdbc.queryForList(""" SELECT s.id::text       AS id, s.shipment_no, s.customer_ref, s.destination_country, s.created_at, cu.name           AS customer_name, cn.name           AS channel_name, coalesce(( SELECT sum(c.chargeable_weight_kg) FROM cartons c WHERE c.shipment_id = s.id ), 0) AS charge_weight, coalesce(( SELECT sum(c.actual_weight_kg) FROM cartons c WHERE c.shipment_id = s.id ), 0) AS channel_weight, coalesce(( SELECT sum(ch.amount) FROM charges ch WHERE ch.shipment_id = s.id AND ch.side = 'AR' AND ch.settlement_status <> 'VOID' ), 0) AS revenue, coalesce(( SELECT sum(ch.amount) FROM charges ch WHERE ch.shipment_id = s.id AND ch.side = 'AP' AND ch.settlement_status <> 'VOID' ), 0) AS cost, -- 运单级赔偿（apply_amount 是公司实际支付，已审才入账） coalesce(( SELECT sum(r.apply_amount) FROM acc_reparations r WHERE r.shipment_id = s.id AND r.audit_status = 'AUDITED' ), 0) AS reparation FROM shipments s LEFT JOIN customers cu ON cu.id = s.customer_id LEFT JOIN channels cn  ON cn.id = s.channel_id WHERE (?::text IS NULL OR s.shipment_no ILIKE ? OR s.customer_ref ILIKE ?) AND (?::date IS NULL OR s.created_at >= ?::date) AND (?::date IS NULL OR s.created_at < (?::date + 1)) AND EXISTS (SELECT 1 FROM charges ch WHERE ch.shipment_id = s.id) ORDER BY s.created_at DESC LIMIT ? OFFSET ? """, search, search, search, dateFrom, dateFrom, dateTo, dateTo, limit, offset); return AccPaging.result(rows.stream().map(this::project).toList(), total == null ? 0 : total); } catch (DataAccessException ex) { return AccPaging.result(List.of(), 0); } } /** /summary?dateFrom=&dateTo=&groupBy={customer|channel|country|day} 真实期间利润：profit = revenue(AR) - cost(AP) + adjustments - reparation adjustments = finance_txns(调账/退款/返利) + fines(罚款) 方向：side='CUSTOMER' → +amount（公司收）；side='SUPPLIER' → -amount（公司付） 维度可分摊性： customer / day：可按 customer_id / the_date 聚合 finance_txns + fines channel / country：finance_txns/fines 无渠道/国家信息，不分摊（返 0 + 注释说明） /

返回：`Map<String, Object>`

### AccQuickOrdersController

_/api/acc/quick-orders — orders 表的简化视图（quick-order 入口或全量简化展示）。 只读，无 audit 流。_

**Base path**：`/api/acc/quick-orders`

#### `GET /api/acc/quick-orders` → `list()`

返回：`Map<String, Object>`

### AccReceivedSmsController

_/api/acc/received-sms — 收款短信，半人工记录，含汇率快照。_

**Base path**：`/api/acc/received-sms`

#### `GET /api/acc/received-sms` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/received-sms/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/received-sms` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/received-sms/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/received-sms/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccReceivedsController

_/api/acc/receiveds — 前端列：no / customerName / bankName / amount / theDate / auditName / remark AR 收款，来源 payments + customers。_

**Base path**：`/api/acc/receiveds`

#### `POST /api/acc/receiveds/quick` → `quick()`

/** /api/acc/receiveds — 前端列：no / customerName / bankName / amount / theDate / auditName / remark AR 收款，来源 payments + customers。 / public class AccReceivedsController { private static final String TABLE = "payments"; private final JdbcTemplate jdbc; private final JsonSupport json; private final CascadeChecker cascadeChecker; private final FieldGate fieldGate; private final MoneySnapshotService moneySnapshotService; private final com.xqt.saas.documentcharges.DocumentChargeService docService; public AccReceivedsController(JdbcTemplate jdbc, JsonSupport json, CascadeChecker cascadeChecker, FieldGate fieldGate, MoneySnapshotService moneySnapshotService, com.xqt.saas.documentcharges.DocumentChargeService docService) { this.jdbc = jdbc; this.json = json; this.cascadeChecker = cascadeChecker; this.fieldGate = fieldGate; this.moneySnapshotService = moneySnapshotService; this.docService = docService; } public Map<String, Object> list( @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String keyword, @RequestParam(required = false) String dateFrom, @RequestParam(required = false) String dateTo ) { try { int limit = AccPaging.pageSize(pageSize); int offset = AccPaging.offset(page, pageSize); String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%"; Long total = jdbc.queryForObject(""" SELECT count(*) FROM payments p WHERE (?::text IS NULL OR p.reference_no ILIKE ?) AND (?::date IS NULL OR p.received_at >= ?::date) AND (?::date IS NULL OR p.received_at < (?::date + 1)) """, Long.class, search, search, dateFrom, dateFrom, dateTo, dateTo); List<Map<String, Object>> rows = jdbc.queryForList(""" SELECT p.id::text       AS id, p.reference_no, p.currency, p.amount, p.received_at, p.remark, p.audit_status, p.audited_at, p.audit_name, c.name           AS customer_name, coalesce(fa.bank_name, fa.account_name) AS bank_name FROM payments p LEFT JOIN customers c ON c.id = p.customer_id LEFT JOIN financial_accounts fa ON fa.id = p.financial_account_id WHERE (?::text IS NULL OR p.reference_no ILIKE ?) AND (?::date IS NULL OR p.received_at >= ?::date) AND (?::date IS NULL OR p.received_at < (?::date + 1)) ORDER BY p.received_at DESC LIMIT ? OFFSET ? """, search, search, dateFrom, dateFrom, dateTo, dateTo, limit, offset); return AccPaging.result(rows.stream().map(this::project).toList(), total == null ? 0 : total); } catch (DataAccessException ex) { return AccPaging.result(List.of(), 0); } } public Map<String, Object> raw(@PathVariable String id) { List<Map<String, Object>> rows = jdbc.queryForList( "SELECT * FROM payments WHERE id = ?::uuid LIMIT 1", id); return rows.isEmpty() ? Map.of() : json.row(rows.get(0)); } public Map<String, Object> create(@RequestBody Map<String, Object> body) { Object customerId = body.get("customer_id"); BigDecimal amount = body.get("amount") instanceof Number n ? new BigDecimal(n.toString()) : BigDecimal.ZERO; String currency = (String) body.getOrDefault("currency", "CNY"); String referenceNo = (String) body.getOrDefault("reference_no", body.get("no")); Object bankId = body.getOrDefault("financial_account_id", body.get("bankId")); String remark = (String) body.get("remark"); String id = jdbc.queryForObject(""" INSERT INTO payments ( tenant_id, customer_id, currency, amount, received_at, reference_no, financial_account_id, remark ) VALUES ( current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, ?, now(), ?, ?::uuid, ? ) RETURNING id::text """, String.class, customerId == null ? null : customerId.toString(), currency, amount, referenceNo, bankId == null ? null : bankId.toString(), remark); // AR 收款的汇率快照 moneySnapshotService.snapshot(TABLE, id, amount, currency); return Map.of("id", id); } public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) { String currentAudit = jdbc.queryForObject( "SELECT audit_status FROM payments WHERE id = ?::uuid", String.class, id); FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body); if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) { throw ApiException.badRequest("收款已审核，字段不可修改: " + String.join(",", gate.rejected()) + "；请先反审"); } Map<String, Object> allowed = gate.allowed(); jdbc.update(""" UPDATE payments SET reference_no = coalesce(?, reference_no), amount       = coalesce(?, amount) WHERE id = ?::uuid """, (String) allowed.get("reference_no"), allowed.get("amount") instanceof Number n ? new BigDecimal(n.toString()) : null, id); // 改了金额刷新汇率快照 if (allowed.containsKey("amount") && allowed.get("amount") instanceof Number n) { String currency = jdbc.queryForObject( "SELECT currency FROM payments WHERE id = ?::uuid", String.class, id); moneySnapshotService.snapshot(TABLE, id, new BigDecimal(n.toString()), currency); } return Map.of("id", id, "rejectedFields", gate.rejected()); } public Map<String, Object> delete(@PathVariable String id) { String auditStatus = jdbc.queryForObject( "SELECT audit_status FROM payments WHERE id = ?::uuid", String.class, id); if ("AUDITED".equals(auditStatus)) { throw ApiException.badRequest("收款已审核，不能删除，请先反审"); } cascadeChecker.checkBeforeDelete(TABLE, id); jdbc.update("DELETE FROM payments WHERE id = ?::uuid", id); return Map.of("id", id, "deleted", true); } /** 对应前端 "快速收款"。统一走 documentcharges：raw INSERT payments 后再调 docService.recordStandaloneReceipt 写资金流水（balance_ledger RECEIPT）， 让快速收款也进资金账本，不再绕过流水（消除"双轨"）。 前端 body: { customerId, amount, bankId }；返回 { ok, id, ledgerWritten }。 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

### AccRemotesController

_/api/acc/remotes — 前端列：postcode / country / supplierName / type。来源 remote_zones + channels（作为 supplierName 代偿）。_

**Base path**：`/api/acc/remotes`

#### `GET /api/acc/remotes` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/remotes/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/remotes` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/remotes/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/remotes/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccReparationsController

_/api/acc/reparations — 赔偿，前端列：expressNo / customerName / applyAmount / paidAmount / reason / addName / addTime。_

**Base path**：`/api/acc/reparations`

#### `GET /api/acc/reparations` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/reparations/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/reparations` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/reparations/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/reparations/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccReturnsController

_/api/acc/returns — 前端列：expressNo / customerName / reason / amount / status / addTime。 来源：return_orders + shipments + customers join。amount 暂无字段，先空。_

**Base path**：`/api/acc/returns`

#### `GET /api/acc/returns` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/returns/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/returns` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/returns/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/returns/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccScheduledTasksController

**Base path**：`/api/acc/tasks`

#### `GET /api/acc/tasks` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/tasks/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/tasks` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/tasks/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/tasks/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccShipmentsController

_/api/acc/shipments — 前端列：no / channelName / supplierName / country / totalPiece / totalWeight / totalCharge / totalCost / auditName / addTime 来源：shipments + channels + cartons 聚合。supplierName / auditName 暂空（新模型未建模）。_

**Base path**：`/api/acc/shipments`

#### `GET /api/acc/shipments/{id}/evidence` → `evidence()`

/** /api/acc/shipments — 前端列：no / channelName / supplierName / country / totalPiece / totalWeight / totalCharge / totalCost / auditName / addTime 来源：shipments + channels + cartons 聚合。supplierName / auditName 暂空（新模型未建模）。 / public class AccShipmentsController { private static final String TABLE = "shipments"; private final JdbcTemplate jdbc; private final JsonSupport json; private final CascadeChecker cascadeChecker; private final FieldGate fieldGate; private final com.xqt.saas.tracking.TrackingAggregator trackingAggregator; public AccShipmentsController(JdbcTemplate jdbc, JsonSupport json, CascadeChecker cascadeChecker, FieldGate fieldGate, com.xqt.saas.tracking.TrackingAggregator trackingAggregator) { this.jdbc = jdbc; this.json = json; this.cascadeChecker = cascadeChecker; this.fieldGate = fieldGate; this.trackingAggregator = trackingAggregator; } public Map<String, Object> list( @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String keyword, @RequestParam(required = false) String dateFrom, @RequestParam(required = false) String dateTo ) { try { int limit = AccPaging.pageSize(pageSize); int offset = AccPaging.offset(page, pageSize); String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%"; Long total = jdbc.queryForObject(""" SELECT count(*) FROM shipments s WHERE (?::text IS NULL OR (s.shipment_no ILIKE ? OR s.customer_ref ILIKE ?)) AND (?::date IS NULL OR s.created_at >= ?::date) AND (?::date IS NULL OR s.created_at < (?::date + 1)) """, Long.class, search, search, search, dateFrom, dateFrom, dateTo, dateTo); List<Map<String, Object>> rows = jdbc.queryForList(""" SELECT s.id::text       AS id, s.shipment_no, s.customer_ref, s.status::text   AS status, s.destination_country, s.created_at, s.audit_status, s.audited_at, s.audit_name, ch.name          AS channel_name, -- 物流商：从最新生效的 channel_cost_policies 拿 carrier 名 ( SELECT car.name FROM channel_cost_policies ccp LEFT JOIN carriers car ON car.id = ccp.carrier_id WHERE ccp.tenant_id = s.tenant_id AND ccp.channel_id = s.channel_id AND ccp.effective_from <= current_date AND (ccp.effective_to IS NULL OR ccp.effective_to >= current_date) ORDER BY ccp.effective_from DESC LIMIT 1 ) AS supplier_name, (SELECT count(*) FROM cartons c WHERE c.shipment_id = s.id) AS piece_count, (SELECT coalesce(sum(c.actual_weight_kg), 0) FROM cartons c WHERE c.shipment_id = s.id) AS total_weight, ( SELECT coalesce(sum(ch2.amount), 0) FROM charges ch2 WHERE ch2.shipment_id = s.id AND ch2.side = 'AR' AND ch2.settlement_status <> 'VOID' ) AS total_charge, ( SELECT coalesce(sum(ch3.amount), 0) FROM charges ch3 WHERE ch3.shipment_id = s.id AND ch3.side = 'AP' AND ch3.settlement_status <> 'VOID' ) AS total_cost FROM shipments s LEFT JOIN channels ch ON ch.id = s.channel_id WHERE (?::text IS NULL OR (s.shipment_no ILIKE ? OR s.customer_ref ILIKE ?)) AND (?::date IS NULL OR s.created_at >= ?::date) AND (?::date IS NULL OR s.created_at < (?::date + 1)) ORDER BY s.created_at DESC LIMIT ? OFFSET ? """, search, search, search, dateFrom, dateFrom, dateTo, dateTo, limit, offset); return AccPaging.result(rows.stream().map(this::project).toList(), total == null ? 0 : total); } catch (DataAccessException ex) { return AccPaging.result(List.of(), 0); } } public Map<String, Object> raw(@PathVariable String id) { List<Map<String, Object>> rows = jdbc.queryForList( "SELECT * FROM shipments WHERE id = ?::uuid LIMIT 1", id); return rows.isEmpty() ? Map.of() : json.row(rows.get(0)); } /** Provider evidence 聚合：cartons.carrier_evidence + label_files.evidence + charges.evidence。 前端运单详情对话框展示用，便于客服/运维排障看到取号 / 面单 / 费用 provider 的实际报文。 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `GET /api/acc/shipments/{id}/timeline` → `timeline()`

/** 内部视角时间线（任务 S1）：UNION 5 源事件 + operator/internal remark 全字段。 对应 ACC 旧系统 Express_Process / Stowage_Process / 上门揽收等综合查看。 /

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `GET /api/acc/shipments/{id}/items` → `items()`

/** 前端原页面的 "查看装箱清单"，对应 ACC `shipments/{id}/items`。返回该 shipment 下所有 cartons + declarations。 */

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/shipments` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/shipments/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/shipments/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccSocialPersonsController

**Base path**：`/api/acc/social-persons`

#### `GET /api/acc/social-persons` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/social-persons/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/social-persons` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/social-persons/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/social-persons/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccSocialsController

**Base path**：`/api/acc/socials`

#### `GET /api/acc/socials` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/socials/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/socials` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/socials/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/socials/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccSoldTosController

**Base path**：`/api/acc/sold-tos`

#### `GET /api/acc/sold-tos` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/sold-tos/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/sold-tos` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/sold-tos/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/sold-tos/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccStatsController

_/api/acc/stats — ACC 顶栏统计。 走 AccTenantTxFilter 的事务，所有查询通过 app.current_tenant_id + RLS 自动过滤租户， 因此 SQL 里不需要显式 WHERE tenant_id。 失败时返回 0，避免前端首屏报错；具体单项失败不影响其它项。_

**Base path**：`/api/acc/stats`

#### `GET /api/acc/stats` → `stats()`

返回：`Map<String, Object>`

### AccStowageCategoriesController

**Base path**：`/api/acc/stowage-categories`

#### `GET /api/acc/stowage-categories` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/stowage-categories/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/stowage-categories` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/stowage-categories/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/stowage-categories/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccStowageStepsController

**Base path**：`/api/acc/stowage-steps`

#### `GET /api/acc/stowage-steps` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/stowage-steps/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/stowage-steps` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/stowage-steps/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/stowage-steps/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccStowagesController

**Base path**：`/api/acc/stowages`

#### `GET /api/acc/stowages` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/stowages/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/stowages` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/stowages/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/stowages/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccSupplierAdjustsController

**Base path**：`/api/acc/supplier-adjusts`

#### `GET /api/acc/supplier-adjusts` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/supplier-adjusts/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/supplier-adjusts` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/supplier-adjusts/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/supplier-adjusts/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccSupplierFinesController

_/api/acc/supplier-fines —— acc_fines WHERE side='SUPPLIER'。_

**Base path**：`/api/acc/supplier-fines`

#### `GET /api/acc/supplier-fines` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/supplier-fines/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/supplier-fines` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/supplier-fines/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/supplier-fines/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccSupplierRebatesController

**Base path**：`/api/acc/supplier-rebates`

#### `GET /api/acc/supplier-rebates` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/supplier-rebates/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/supplier-rebates` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/supplier-rebates/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/supplier-rebates/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccSupplierRefundsController

**Base path**：`/api/acc/supplier-refunds`

#### `GET /api/acc/supplier-refunds` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/supplier-refunds/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/supplier-refunds` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/supplier-refunds/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/supplier-refunds/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccSuppliersController

_/api/acc/suppliers — 前端列：name / contact / mobile / phone / product / balance / settlement 来源：partners WHERE partner_type='SUPPLIER'。contact/mobile/phone/product/balance 新模型未建模，先空值。_

**Base path**：`/api/acc/suppliers`

#### `GET /api/acc/suppliers` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/suppliers/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/suppliers` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/suppliers/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/suppliers/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccTracksController

**Base path**：`/api/acc/tracks`

#### `GET /api/acc/tracks` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/tracks/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/tracks` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/tracks/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/tracks/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccTransfersController

**Base path**：`/api/acc/transfers`

#### `GET /api/acc/transfers` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/transfers/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/transfers` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/transfers/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/transfers/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccTransitsController

**Base path**：`/api/acc/transits`

#### `GET /api/acc/transits` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/transits/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/transits` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/transits/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/transits/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccVoidOrdersController

_/api/acc/void-orders — 订单作废视图。只读，是 orders WHERE status='CANCELLED' 的快捷查询。 前端列：No / TrackNo / CustomerName / Status / Amount / Paid / AddName / AddTime（大写驼峰，沿用旧 ACC）。_

**Base path**：`/api/acc/void-orders`

#### `GET /api/acc/void-orders` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/void-orders/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccWagesController

**Base path**：`/api/acc/wages`

#### `GET /api/acc/wages` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/wages/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/wages` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/wages/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/wages/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccWarehousesController

_/api/acc/warehouses — 前端列：name / code / consignee / company / country / province / postcode / type。 复用现有 warehouses 表。consignee/company/postcode 新模型不在主表，先空字符串占位。_

**Base path**：`/api/acc/warehouses`

#### `GET /api/acc/warehouses` → `list()`

返回：`Map<String, Object>`

#### `GET /api/acc/warehouses/{id}/raw` → `raw()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

#### `POST /api/acc/warehouses` → `create()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `PUT /api/acc/warehouses/{id}` → `update()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |
| `Object` | `@RequestBody` | `Map<String,` | 是 |

返回：`Map<String, Object>`

#### `DELETE /api/acc/warehouses/{id}` → `delete()`

| 参数 | 来源 | 类型 | 必填 |
|------|------|------|------|
| `id` | `@PathVariable` | `String` | 是 |

返回：`Map<String, Object>`

### AccZonesController

_/api/acc/zones — 价格分区聚合视图，从 rate_card_lines.zone_code 去重聚合。 只读，无 audit 流。_

**Base path**：`/api/acc/zones`

#### `GET /api/acc/zones` → `list()`

返回：`Map<String, Object>`

---

## §99. 健康检查

包路径：`com.xqt.saas.health`

### HealthController

**Base path**：`(无)`

#### `GET /api/health` → `health()`

返回：`ApiResponse<HealthResponse>`

---
