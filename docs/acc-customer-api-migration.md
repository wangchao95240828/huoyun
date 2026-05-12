# ACC Customer-API 重构落地

生成日期：2026-05-12
范围：把 `acc/api/APIClass.php`、`acc/CustomerAPI.php`、`acc/api/getNewLabel.php` 涉及的客户外部 API 能力迁移到 xqt-saas 的 Spring Boot 后端，作为 ACC 全量重构的第一个模板模块。

## 1. 模块边界

| 旧 ACC 入口 | 新接口 | 当前状态 |
| --- | --- | --- |
| `api/APIClass.php?act=Balance` | `GET /api/customer-api/balance` | 已实现，对照样本通过 |
| `api/APIClass.php?act=PreOrder` | `POST /api/customer-api/orders` | 已实现到 DRAFT 落表，未做费率/取号 |
| `api/APIClass.php?act=Submit` | 未实现，留待 `rates` + 渠道适配器模块 | L1 |
| `api/APIClass.php?act=Cancel` / `Query` / `Track` / `Label` / `Price` / `Status` / `Product` / `Sync` | 未实现 | L1 |
| `CustomerAPI.php`（后台 CRUD） | 走 xqt-saas `admin` 模块统一后台，不单独重构 | 跳过 |
| `api/getNewLabel.php` | 移交 `labels` 模块 | 跳过 |

## 2. 包结构

```
com.xqt.saas.customerapi
├── AccErrorCode              旧系统 output($N) 数字码 → 字符串 errorCode
├── CachedBodyHttpServletRequest  让 filter 和 controller 共享 body
├── CustomerApiAuthFilter     签名 + 时间窗 + 凭证有效性
├── CustomerApiCredential     api_credentials 行模型
├── CustomerApiPrincipal      SecurityContext 里的客户身份
├── CustomerApiRepository     api_credentials / financial_accounts / orders 读写
├── CustomerApiRequests       PreOrder body
├── CustomerApiResponses      BalanceList / PreOrderResult
├── CustomerApiController     /api/customer-api/{balance,orders,ping}
├── CustomerApiService        余额查询 + 草稿落表 + 字段校验
└── SignatureValidator        md5(join(',', sorted_values) + secret)
```

## 3. 签名协议

兼容 ACC 原算法的"排序值逗号拼接 + 末尾追加 APIKey"结构，但请求由 form-encoded 切到 JSON body + header：

| Header | 含义 | 旧字段 |
| --- | --- | --- |
| `X-API-User` | access_key（旧 APIID） | `user` |
| `X-API-Time` | unix epoch 秒，允许 ±3600 秒 | `time` |
| `X-API-Version` | 协议版本号 | `version` |
| `X-API-Sign` | md5 小写 hex（32 位） | `sign` |

签名参数集（去掉 sign）：

```
body    = sha256_hex(http_request_body_bytes)
time    = X-API-Time
user    = X-API-User
version = X-API-Version

sorted_values = [body, time, user, version]   // 按 key 升序
expected_sign = md5( join(",", sorted_values) + secret )
```

旧 PHP 是把整个 $_GET+$_POST 排序，新版把 body 折叠为一个 sha256 hash 加入签名集合，避免嵌套 JSON 在签名时的歧义。

## 4. 错误码映射

| 旧 output(N) | 新 errorCode | 说明 |
| --- | --- | --- |
| 100 | `ACC_100` | 功能未启用 |
| 101 | `ACC_101` | 缺 user |
| 102 | `ACC_102` | 缺 act |
| 103 | `ACC_103` | 时间漂移 > 1h |
| 104 | `ACC_104` | 缺 version |
| 105 | `ACC_105` | sign 缺失或长度错 |
| 106 | `ACC_106` | access_key 未注册 |
| 107 | `ACC_107` | access_key 绑定的客户找不到 |
| 108 | `ACC_108` | sign 校验不匹配 |
| 112 | `ACC_112` | 必填参数缺失 |
| 113 | `ACC_113` | 必填参数为空 |

HTTP 状态：400（缺参/时间漂移）、401（用户不存在/签名错）。

## 5. 数据模型映射

### 5.1 凭证

| ACC `Customer_API` | 新 `api_credentials` | 备注 |
| --- | --- | --- |
| `Customer` | `owner_id`（owner_type='CUSTOMER'） | |
| `APIID` | `access_key` | 唯一索引 (tenant_id, access_key) |
| `APIKey` | `secret_hash` | 当前直存原始 key 以兼容 md5 签名 |
| `Count` | `last_used_at` 替代 | 不再记总调用数 |
| `APIURL` | 未引入 | 回调地址，后续如需再加 |

### 5.2 余额

| ACC `Customer_Balance` | 新 `financial_accounts` | 备注 |
| --- | --- | --- |
| `Customer` | `owner_id`（owner_type='CUSTOMER'） | |
| `Currency` | `currency` (char 3) | 旧版用整数 ID，新版直接币种代码 |
| `Balance` | `balance` (numeric 18,2) | 旧值是负数（账方向），新值取正 |
| `Credits` | `customers.credit_limit` | 已存在 |
| — | `metadata.purpose='customer-api.balance'` | 区分 API 余额账户与其他财务账户 |

### 5.3 预报订单

ACC 的 `Online`/`Express`/`Express_Item` 三表对应一个 PreOrder。新系统只先落 `orders`（status='DRAFT', source='LOCAL', customer_direction='DOCUMENT_CUSTOMER', service_mode='DOCUMENT_SHIPPING', order_entry_type='API_ORDER'），ACC 原始字段进入 `orders.metadata.acc_compat`。后续 Submit/费率/取号需要拆分到 `shipments`、`charges`、`tracking_events`。

## 6. RLS 注意事项

`api_credentials` 和 `financial_accounts` 都启用了 `force row level security`，policy 是 `app_tenant_matches(tenant_id)` → 服务角色或 `app.current_tenant_id` 匹配才放行。

- **filter 查 access_key 时还不知道 tenant** → CustomerApiRepository.findByAccessKey / markCalled 用 `set_config('app.service_role','true', true)`，is_local=true 让 service_role 在事务提交时自动复位
- **controller 查 balance 时 tenant 已知** → CustomerApiService.queryBalance 走 `set_config('app.current_tenant_id', tenantId, true)`，走正常 RLS 通道
- **不要给 xqt 直接 BYPASSRLS**，那会破坏多租户隔离的强约束

## 7. 演示凭证（DOC-DEMO）

`db/migrations/016_acc_customerapi_seed.sql` 已应用到服务器：

```
access_key:  60000DEMO
secret:      DEMOAPIKEY32CHARS00000000000DEMO
tenant:      xqt
customer:    DOC-DEMO
balances:    CNY 10000.00, USD 500.00
```

## 8. 对照样本约定

每个 ACC 模块需要在 `comparison_cases` 表登记 3-5 条样本。customer-api 当前已落 1 条（余额查询契约）。其余样本待业务提供 ACC 真实抓包后补。

## 9. 后续模块按本模板复刻

模板顺序：

1. `com.xqt.saas.rates` — Price 试算
2. `com.xqt.saas.labels` — Label / Relabel / 文件下载
3. `com.xqt.saas.tracking` — Track 多源聚合
4. `com.xqt.saas.shipments` — Submit + 渠道取号 + 状态机
5. `com.xqt.saas.documentcharges` — Express_Charge 预扣 + 账单闭环

每个模块复用：
- CustomerApiAuthFilter（已在 /api/customer-api/** 路径下生效）
- ACC 字段进 metadata.acc_compat 与 external_field_mappings
- 至少 1 个 contract test 锁定 ACC 旧响应字段
