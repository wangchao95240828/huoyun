# Comparison Case：sumy.php 第三方推单迁移

> 任务：docs/acc-logic-gap-claude-task-2026-05-28.md §3.8 + 任务7
> 旧系统：`acc/api/sumy.php`（act=push / channel / aboutus）
> 新系统：`/api/customer-api/external-orders/sumy`

## 1. 迁移映射

| ACC sumy.php | 新系统 |
|---|---|
| `POST /api/sumy.php?act=push` | `POST /api/customer-api/external-orders/sumy` |
| `GET /api/sumy.php?act=channel&token=` | `GET /api/customer-api/external-orders/sumy/channels` |
| `act=aboutus`（静态文本） | 不迁（无业务价值） |
| ApiToken（Customer_API.APIKey，body 内） | customer-api 签名鉴权（X-API-User/Time/Sign），CustomerApiPrincipal |
| 内联 Online 建单 + 插件 addOrder | 复用 `preOrder` + `submitOrder`（标准 orders/shipments/cartons/charges） |
| `Freight::getFee(ProductType=2)` 选最便宜渠道 | `RateEngine.quote()`（Submit 内） |

## 2. 请求/响应契约（保持 PascalCase 兼容旧客户端）

请求：
```json
{
  "ApiUserName": "thirdparty",
  "ApiToken": "<保留，不再作鉴权>",
  "OrderList": [{
    "PlatformOrderID": "ORDER123",
    "Channel": "EU-AIR-UPS",
    "Weight": 1500,
    "CustomsName": "Toy", "CustomsNameCN": "玩具", "CustomsValue": 10.0,
    "Receiver": { "Name": "John Doe", "Province": "CA", "City": "Los Angeles",
                  "Address": "1 Main St", "PostCode": "90001",
                  "CountryCode": "US", "Mobile": "13800000000" },
    "ProductList": [{ "CustomsName": "Toy", "CustomsCnName": "玩具",
                      "HSCode": "9503", "DeclareValue": 5.0, "Quantity": 2 }]
  }]
}
```

响应：
```json
{ "Success": true, "Message": "",
  "Result": [{ "PlatformOrderID": "ORDER123", "Success": true,
               "TrackingNumber": "1Z999AA10123456784", "ErrorMessage": "" }] }
```

## 3. 校验对照（1:1 复刻 sumy.php）

| 规则 | ACC | 新系统 | 错误消息 |
|---|---|---|---|
| 单号 6-30 + [0-9a-zA-Z-] | ✅ | ✅ | 订单号长度必须是6-30，并且只能为【数字,字母,-】组合！ |
| 重量 > 0 | ✅ | ✅ | 重量【x】必须为大于零的数字 |
| 收货人 ≤ 60 | ✅ | ✅ | 收货人长度不能超过60个字符！ |
| 省/洲 ≤ 50 | ✅ | ✅ | 省/洲长度不能超过50个字符！ |
| 城市 ≤ 50 | ✅ | ✅ | 城市长度不能超过50个字符！ |
| 邮编 ≤ 20 | ✅ | ✅ | 邮编长度不能超过20个字符！ |
| 单号唯一 | Online.No | preOrder 内查重（重复抛错被逐单捕获） | 相同运单号已存在 |
| 国家存在 | Country.Code2 | submitOrder 渠道/国家校验 | 找不到国家 |
| 渠道存在且启用 | Product.isOpen | submitOrder findChannelIdByCode | 渠道未启用 |

## 4. 字段映射（sumy → PreOrder）

| sumy | PreOrder | 说明 |
|---|---|---|
| PlatformOrderID | no | |
| Channel | product | 渠道 code |
| Receiver.CountryCode | country | Code2 |
| Weight（克） | weight | ÷1000 → kg，保留 3 位 |
| — | piece | 固定 1 |
| Receiver.{Name,Province,City,Address,PostCode,Tel‖Mobile} | receiver | Tel 优先，空则 Mobile |
| ProductList[] | declare[] | name←CustomsName‖订单级, material←CustomsCnName‖订单级, hsCode, price←DeclareValue‖CustomsValue, quantity |

## 5. 测试用例（SumyServiceTest）

| # | 场景 | ACC 行为 | 新系统结果 | 通过 |
|---|---|---|---|---|
| 1 | 正常推单 1500g | 建单+取号 | 映射 weight=1.500/product/country，preOrder+submitOrder，返回 TrackingNumber | ✅ |
| 2 | 单号 "ABC"（<6） | outResult 错误 | Success=false，不调下单 | ✅ |
| 3 | 重量 0 | outResult 错误 | Success=false | ✅ |
| 4 | 两单，第一单余额不足 | 逐单隔离 | result[0] 失败、result[1] 成功 | ✅ |
| 5 | OrderList 空 | output 错误 | 整体 400 | ✅ |

## 6. 刻意偏离 ACC 的点

1. **鉴权**：旧版 ApiToken 在 body；新版统一 customer-api 签名鉴权（更安全，与其它客户 API 一致）。body 的 ApiToken/ApiUserName 保留字段但不再校验。
2. **不另写建单逻辑**：复用 preOrder + submitOrder，自动获得 RateEngine 报价、AR/AP 费用行、余额预扣、资金流水、审计——比旧版仅 Online 建单更完整。
3. **aboutus** 静态文本不迁。

## 7. 待补（下一轮）

- 真实 sumy 旧请求样本回放（需要旧库 APIKey + 真实第三方报文）逐字段 diff。
- 真实渠道取号（当前 submitOrder 走 Demo gateway，trackingNo 为 NOOP/DEMO 格式）。
