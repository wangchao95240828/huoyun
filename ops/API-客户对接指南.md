# xqt-saas 客户 API 对接指南

**Base URL**: http://8.148.227.76/api/customer-api  
**Swagger UI** (在线试用): http://8.148.227.76/swagger-ui/index.html  
**鉴权**: 双方案 (HMAC 签名 / Bearer Token), 推荐前者用于服务到服务

---

## 1. 申请 API 凭证

### 客户侧
1. 联系运营开通账号
2. 内部走: 销售中心 → 客户 API → **+ 新增** → 选客户 → 点击保存
3. 系统返回 `accessKey` + `secret` (secret 仅显示一次, 立刻抄走)

### 凭证示例 (示例非真实)
```
accessKey: ak_GN3Sfp6eUTdTnCWiSDFPyA
secret:    sk_XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
```

---

## 2. 鉴权方式

### 方式 A: HMAC 签名 (ACC 兼容, 推荐)

每个请求带 4 个 query 参数:
- `user`: accessKey
- `time`: Unix 时间戳 (秒)
- `version`: API 版本号 (固定 1)
- `sign`: 见下

签名算法 (对齐 ACC `api/APIClass.php`):
1. 把请求所有参数 (含 body 字段, 不含 `sign` 自己) 按 key 字典序排序
2. 取所有 value, 英文逗号拼接: `join(',', sorted_values)`
3. 末尾追加 `secret`
4. md5 取小写 hex (32 字符) = `sign`

#### Python 示例
```python
import hashlib, time, requests, json

ACCESS_KEY = "ak_..."
SECRET     = "sk_..."
BASE       = "http://8.148.227.76/api/customer-api"

def sign(params: dict) -> str:
    sorted_keys = sorted(k for k in params if k != "sign" and params[k] is not None)
    joined = ",".join(str(params[k]) for k in sorted_keys)
    return hashlib.md5((joined + SECRET).encode()).hexdigest()

def call(path, body=None):
    params = {
        "user": ACCESS_KEY,
        "time": str(int(time.time())),
        "version": "1",
    }
    if body:
        params.update({k: str(v) for k, v in body.items() if not isinstance(v, (dict, list))})
    params["sign"] = sign(params)
    r = requests.post(f"{BASE}{path}", params=params, json=body or {})
    return r.json()

# 例: 查余额
print(call("/balance"))

# 例: 下单
print(call("/orders", {
    "orderNo": "TEST-20260623-001",
    "product": "UPS-GROUND-US",
    "channelAccount": "J602B0",
    "country": "US",
    "postcode": "90001",
    "recipient": "John Smith",
    "address": "123 Main St",
    "weight": 1.5,
    "piece": 1,
    "declaredValue": 18.88,
    "currency": "USD",
}))
```

#### curl 示例 (查余额)
```bash
ACCESS_KEY="ak_..."
SECRET="sk_..."
TS=$(date +%s)
SIGN=$(printf "1,${TS},${ACCESS_KEY}${SECRET}" | md5sum | cut -d' ' -f1)
curl "http://8.148.227.76/api/customer-api/balance?user=${ACCESS_KEY}&time=${TS}&version=1&sign=${SIGN}"
```

### 方式 B: Bearer Token

简单方案 (不对齐 ACC), 适合调试:
```bash
curl -H "Authorization: Bearer ${ACCESS_KEY}:${SECRET}" \
  "http://8.148.227.76/api/customer-api/balance"
```

---

## 3. 核心 endpoints

### 查询类
| Endpoint | Method | 用途 |
|---|---|---|
| `/ping` | GET | 探活 |
| `/balance` | GET | 客户当前预扣余额 |
| `/finance/balance` | GET | 含到期账单/逾期天数 |
| `/finance/invoices` | GET | 账单列表 |
| `/finance/prepay-details.csv` | GET | 预扣明细 CSV 下载 |
| `/channels` | GET | 当前客户可用渠道列表 |
| `/products` | GET | 当前客户可用产品列表 |

### 制单类
| Endpoint | Method | body 关键字段 |
|---|---|---|
| `/orders` | POST | orderNo, customerRef, product, channelAccount, weight, piece, recipient... |
| `/orders/{no}` | PUT | 同上, 改未提交的订单 |
| `/orders/{no}/pre-submit` | POST | 试算运费 + 校验 (不真提交) |
| `/orders/{no}/submit` | POST | 正式提交, 取转单号 |
| `/orders/{no}/cancel` | POST | 取消 (限 DRAFT 状态) |
| `/rates/quote` | POST | 报价 (channel/weight/destination) |
| `/orders/status` | POST | 批量查状态 [orderNo1, orderNo2, ...] |
| `/orders/query` | POST | 高级查询 (多字段) |
| `/orders/sync` | POST | 增量同步 (since 时间戳) |

### 追踪类
| Endpoint | Method | 用途 |
|---|---|---|
| `/tracking/query` | POST | 批量查 tracking_no 最新事件 |
| `/tracking/{trackingNo}/timeline` | GET | 单号完整轨迹 |

---

## 4. 错误码

| code | 含义 | 怎么办 |
|---|---|---|
| `ACC_100` | 鉴权失败 / signature 不对 | 检查 secret 是否过期, 重算 sign |
| `ACC_101` | 缺 X-API-User 或 user 参数 | 加上 |
| `ACC_102` | API 凭证已禁用 | 联系运营激活 |
| `ACC_200` | 业务参数错 | 看 error 文本 |
| `ACC_300` | 余额不足 | 充值或还款 |
| `ACC_400` | 订单状态不允许操作 | 看 currentStatus |
| `ACC_500` | 系统错误 | 联系运维, 给 traceId |

---

## 5. 沙箱 vs 生产

- **当前 demo 环境** = 生产 (但只有 87 单 demo 数据)
- 真上线后建议:
  1. 申请 `SANDBOX_` 前缀的 accessKey (走 SandboxCarrierGateway, 不真调 UPS/FedEx)
  2. 沙箱通过后切到生产 accessKey

---

## 6. 限流 / 配额

当前未启用强限流, 内部规则:
- 单 accessKey 每秒 ≤ 10 次
- `/orders` POST 每分钟 ≤ 60 次
- `/tracking/*` 每分钟 ≤ 200 次

超限返回 `ACC_429`. 大批量制单走 `/orders/batch` (待开发) 或 Excel 导入工具.

---

## 7. Webhook 出站 (推送给客户)

客户可以注册 webhook 接收异步通知:
- POST `/webhook-endpoints` 注册 URL + secret
- 我们在以下事件时推送:
  - 订单状态变化 (SUBMITTED/ACCEPTED/IN_TRANSIT/DELIVERED/EXCEPTION)
  - 追踪轨迹更新
  - 异常事件 (问题件自动建)
  - 账单生成 / 支付到账

推送 body 含 HMAC 签名:
```
X-XQT-Signature: hmac-sha256=...
```
客户验签:
```python
import hmac, hashlib
sig = hmac.new(webhook_secret.encode(), request.body, hashlib.sha256).hexdigest()
if request.headers["X-XQT-Signature"] != "hmac-sha256=" + sig:
    return 401
```

---

## 8. 常见对接问题

**Q: signature mismatch (ACC_100)**  
A: 99% 是 value 拼接顺序错。**只按 key 排序后取 value**, value 不要再 url-encode。

**Q: time skew (ACC_103)**  
A: 时间戳 ±60 分钟内有效, 检查服务器时间。

**Q: 下单返回 `余额不足`**  
A: 客户 account_mode=PREPAY 时强校验, 切 MONTHLY 或先收款。

**Q: 报价跟实际账单不一致**  
A: 报价用挂牌汇率, 出账用月末汇率, 差 1-3% 正常。详见 [SOP-财务对账](SOP-财务对账.md)。

**Q: tracking 一直返 `pending`**  
A: 订单 status=DRAFT 时还没生成 tracking_no, 必须 `/submit` 之后。
