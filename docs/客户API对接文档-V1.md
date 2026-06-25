# 跨境物流系统 — 客户 API 对接文档

> **版本**: v1.0 (2026-06-25)
> **基地址**: `http://8.148.227.76` (生产) / `https://api.xqt-saas.com` (改 DNS 后)
> **路径前缀**: `/api/customer-api/`

---

## 一、对接流程总览

```
1. 联系运营开通账号 → 拿 access_key + secret
2. 用 access_key + secret 自己组装签名头
3. 调 /rates/quote 试报价 → 拿到运费估算
4. 调 /orders 创建草稿单 (传清单 packageList)
5. 调 /orders/{no}/submit 真提交 → 取号 + 出面单
6. 调 /tracking/query 跟踪运单
7. 异步: 注册 webhook 接收物流事件
```

---

## 二、认证 — 每客户独立 API Key

### 1. 申请凭证

| 方式 | 步骤 |
|---|---|
| **运营自助** | 销售中心 → API 凭证 → 「+ 新增」→ 选客户 → 系统生成 access_key + secret |
| **运营 SQL** | INSERT `api_credentials (customer_id, access_key, secret_hash)` |

返回:
```
access_key:  AK_4f3a8b7c2d9e1f6a    (20+ 字符)
secret:      sk_8d2c5e9f1a4b7c3e6f5a8b9c2d3e4f5a  (32+ 字符, 只显示一次!)
```

⚠ secret 只在创建瞬间返回, 之后系统只存哈希。客户必须立即妥善保存。

### 2. 签名算法 (5 步)

每个请求必须带 4 个 header:

| Header | 含义 | 例 |
|---|---|---|
| `X-API-User` | 你的 access_key | `AK_4f3a8b7c2d9e1f6a` |
| `X-API-Time` | Unix 秒级时间戳 (±3600 秒漂移内) | `1782394028` |
| `X-API-Version` | 协议版本号 | `1` |
| `X-API-Sign` | 签名 (MD5 lowercase hex) | 32 位 hex |

签名构造:

```
Step 1: 计算 body 的 SHA-256 (空 body 取 sha256(""))
   body_hash = sha256(request_body_string).hexdigest()  # lowercase

Step 2: 构造排序 map
   {
     body:    body_hash,
     time:    "1782394028",
     user:    "AK_4f3a8b7c2d9e1f6a",
     version: "1"
   }

Step 3: 按 key 升序 (body → time → user → version), 取 value, 逗号拼接
   joined = body_hash + "," + time + "," + access_key + "," + version

Step 4: 末尾追加 secret
   to_sign = joined + secret

Step 5: MD5 → lowercase hex
   X-API-Sign = md5(to_sign).hexdigest()
```

### Python 签名工具函数

```python
import hashlib, time, json

def sign_request(access_key: str, secret: str, version: int, body: dict | None = None) -> dict:
    body_str = json.dumps(body, separators=(',', ':'), ensure_ascii=False) if body else ''
    body_hash = hashlib.sha256(body_str.encode('utf-8')).hexdigest()
    ts = str(int(time.time()))
    joined = ','.join([body_hash, ts, access_key, str(version)])  # sorted by key
    sign = hashlib.md5((joined + secret).encode('utf-8')).hexdigest()
    return {
        'X-API-User':    access_key,
        'X-API-Time':    ts,
        'X-API-Version': str(version),
        'X-API-Sign':    sign,
        'Content-Type':  'application/json',
    }
```

---

## 三、核心接口

### 3.1 试报价 (无需先建单, 只算)

```
POST /api/customer-api/rates/quote
```

**Body**:
```json
{
  "channelCode": "UPS-GROUND-US",
  "countryCode": "US",
  "postalCode": "90210",
  "weightKg": 1.5,
  "pieces": 1,
  "volumeCbm": 0.003,
  "declaredValue": 18.88,
  "currency": "USD"
}
```

**返回**:
```json
{
  "ok": true,
  "data": {
    "item": {
      "actualWeightKg": 1.5,
      "volumetricWeightKg": 0.5,
      "chargeableWeightKg": 1.5,
      "freight": 13.85,
      "fuelAmount": 2.42,
      "totalAmount": 18.77,
      "charges": [...]
    }
  }
}
```

### 3.2 创建订单 (上传清单)

```
POST /api/customer-api/orders
```

**Body**:
```json
{
  "no": "MY-ORDER-001",
  "product": "UPS-GROUND-US",
  "country": "US",
  "weight": 1.5,
  "piece": 1,
  "volume": 0.003,
  "currency": "USD",

  "receiver": {
    "company": "Test Co",
    "name": "John Smith",
    "phone": "3105551234",
    "address": "123 Main Street",
    "city": "Beverly Hills",
    "province": "CA",
    "postcode": "90210",
    "country": "US"
  },

  "shipper": {
    "name": "Shanghai Sender Co",
    "phone": "+86-21-55556666",
    "address": "Pudong New Area, Shanghai"
  },

  "declare": [
    {
      "name": "T-shirt",
      "cnName": "T恤",
      "origin": "CN",
      "quantity": 1,
      "price": 18.88,
      "hsCode": "6109100000"
    }
  ],

  "packageList": [
    {
      "no": "PKG-1",
      "name": "T-shirt",
      "cnName": "T恤",
      "weight": 1.5,
      "quantity": 1,
      "price": 18.88,
      "hsCode": "6109100000",
      "length": 20,
      "width": 15,
      "height": 10
    }
  ],

  "type": "PARCEL",
  "label": "PDF",
  "materialsCN": "T恤",
  "materialsEN": "T-shirt"
}
```

**字段说明**:

| 字段 | 必填 | 说明 |
|---|---|---|
| `no` | 是 | 你的客户单号 (你公司内部唯一) |
| `product` | 是 | 渠道产品代码 (从 `/channels` 查) |
| `country` | 是 | ISO 二字码 (`US`/`CN`/`JP`) |
| `weight` | 是 | kg, > 0 |
| `piece` | 是 | 件数, > 0 |
| `volume` | 否 | 体积 m³ |
| `currency` | 否 | 默认 USD/CNY |
| `receiver.country` | 是 | 收件国 (跟 country 一致) |
| `receiver.postcode` | 是 | 美国 5 位 |
| `receiver.phone` | 是 | ≥ 10 位纯数字 (UPS 强制) |
| `declare[*].origin` | 是 | 产地, **二字码 `CN` 不是 "中国"** |
| `declare[*].hsCode` | 是 | **6-10 位纯数字 不带点** (e.g. `6109100000`) |
| `packageList[*].no` | 是 | 装箱单号, 同行不能重复 |
| `packageList[*].weight` | 是 | 装箱总重 = 货物总重 (强校验) |
| `packageList[*].length/width/height` | 否 | cm, UPS Ground 单边 ≤ 274cm |

**返回**:
```json
{
  "ok": true,
  "data": {
    "no": "MY-ORDER-001",
    "orderId": "uuid",
    "status": "DRAFT"
  }
}
```

### 3.3 提交订单 (取号 + 出面单)

```
POST /api/customer-api/orders/{no}/submit
```

**Body**: 空 `{}`

**返回**:
```json
{
  "ok": true,
  "data": {
    "no": "MY-ORDER-001",
    "trackingNo": "1ZJ602B00xxxxxxxx",
    "carrierMasterTrackingNo": "1ZJ602B00xxxxxxxx",
    "status": "SUBMITTED",
    "labelUrl": "/api/customer-api/labels/{labelId}"
  }
}
```

⚠ 提交即真出单, **真扣承运商账户运费**, 出错 (邮编/地址/HS) 会拒。

### 3.4 跟踪查询

```
POST /api/customer-api/tracking/query
```

**Body**:
```json
{
  "trackingNos": ["1ZJ602B00xxxxxxxx", "1ZJ602B00yyyyyyyy"]
}
```

**返回**:
```json
{
  "ok": true,
  "data": {
    "items": [
      {
        "trackingNo": "1ZJ602B00xxxxxxxx",
        "status": "DELIVERED",
        "events": [
          {"time": "2026-06-25T10:00:00Z", "location": "Shenzhen", "event": "PICKED_UP"},
          {"time": "2026-06-26T14:00:00Z", "location": "Beverly Hills, CA", "event": "DELIVERED"}
        ]
      }
    ]
  }
}
```

### 3.5 查余额 (预付客户必看)

```
GET /api/customer-api/balance
```

**返回**:
```json
{
  "ok": true,
  "data": {
    "currency": "USD",
    "balance": 1250.50,
    "frozenAmount": 80.00,
    "availableAmount": 1170.50
  }
}
```

### 3.6 取消订单

```
POST /api/customer-api/orders/{no}/cancel
```

**返回**:
```json
{
  "ok": true,
  "data": {"no": "MY-ORDER-001", "status": "CANCELLED"}
}
```

⚠ DRAFT 可直接取消; SUBMITTED 需调用承运商 API 取消 (24h 内 UPS void 不扣钱)。

---

## 四、辅助接口

| 路径 | 方法 | 用途 |
|---|---|---|
| `/api/customer-api/channels` | GET | 列出可用渠道 (product 字段值来自这) |
| `/api/customer-api/products` | GET | 列出产品 SKU |
| `/api/customer-api/orders/query` | POST | 按条件查订单 |
| `/api/customer-api/orders/status` | POST | 批量查订单状态 |
| `/api/customer-api/orders/sync` | POST | 增量同步 (按 updated_at 拉) |
| `/api/customer-api/finance/balance` | GET | 详细财务余额 |
| `/api/customer-api/finance/invoices` | GET | 账单列表 |
| `/api/customer-api/finance/prepay-details.csv` | GET | 预扣明细 CSV 下载 |
| `/api/customer-api/ping` | GET | 心跳, 检查 token 有效 |

---

## 五、批量上传清单 (xlsx)

对接 SDK 走不通时, 客户可以上传 xlsx 批量建单 (走运营端入口):

```
POST /api/acc/batch-import/orders   (multipart, admin/客服 token)
```

xlsx 表头 (中文/英文均可):

| 客户编码 | 渠道产品 | 制单账号 | 重量(kg) | 国家 | 邮编 | 地址 | 收件人 | 电话 | 申报品名 | 数量 | 单价 | HS编码 |

详见 `批量导单模板.xlsx`。

---

## 六、完整对接代码示例

### Python SDK 雏形

```python
import requests, hashlib, json, time

class XqtClient:
    def __init__(self, base_url, access_key, secret):
        self.base = base_url.rstrip('/')
        self.key = access_key
        self.secret = secret

    def _headers(self, body=None):
        body_str = json.dumps(body, separators=(',', ':'), ensure_ascii=False) if body else ''
        body_hash = hashlib.sha256(body_str.encode('utf-8')).hexdigest()
        ts = str(int(time.time()))
        joined = ','.join([body_hash, ts, self.key, '1'])
        sign = hashlib.md5((joined + self.secret).encode('utf-8')).hexdigest()
        return {
            'X-API-User': self.key,
            'X-API-Time': ts,
            'X-API-Version': '1',
            'X-API-Sign': sign,
            'Content-Type': 'application/json',
        }, body_str

    def quote(self, body):
        h, b = self._headers(body)
        return requests.post(f'{self.base}/api/customer-api/rates/quote',
                            headers=h, data=b.encode('utf-8')).json()

    def create_order(self, body):
        h, b = self._headers(body)
        return requests.post(f'{self.base}/api/customer-api/orders',
                            headers=h, data=b.encode('utf-8')).json()

    def submit_order(self, order_no):
        h, b = self._headers({})
        return requests.post(f'{self.base}/api/customer-api/orders/{order_no}/submit',
                            headers=h, data=b.encode('utf-8')).json()


# 使用
client = XqtClient('http://8.148.227.76', 'AK_xxx', 'sk_xxx')

# 1. 试报价
quote = client.quote({
    'channelCode': 'UPS-GROUND-US',
    'countryCode': 'US',
    'postalCode': '90210',
    'weightKg': 1.5,
    'pieces': 1,
})
print(quote)  # {'ok': True, 'data': {...freight: 13.85...}}

# 2. 创建订单
order = client.create_order({
    'no': 'MY-001',
    'product': 'UPS-GROUND-US',
    'country': 'US',
    'weight': 1.5,
    'piece': 1,
    'receiver': {...},
    'declare': [{...}],
    'packageList': [{...}],
})

# 3. 提交
result = client.submit_order('MY-001')
tracking = result['data']['trackingNo']  # 1ZJ602B0...
```

### Node.js 同等实现

```javascript
const crypto = require('crypto');
const axios = require('axios');

class XqtClient {
  constructor(baseUrl, accessKey, secret) {
    this.base = baseUrl.replace(/\/+$/, '');
    this.key = accessKey;
    this.secret = secret;
  }

  _headers(body) {
    const bodyStr = body ? JSON.stringify(body) : '';
    const bodyHash = crypto.createHash('sha256').update(bodyStr, 'utf-8').digest('hex');
    const ts = String(Math.floor(Date.now() / 1000));
    const joined = [bodyHash, ts, this.key, '1'].join(',');
    const sign = crypto.createHash('md5').update(joined + this.secret, 'utf-8').digest('hex');
    return {
      'X-API-User': this.key,
      'X-API-Time': ts,
      'X-API-Version': '1',
      'X-API-Sign': sign,
      'Content-Type': 'application/json',
    };
  }

  async createOrder(body) {
    return (await axios.post(`${this.base}/api/customer-api/orders`, body,
      { headers: this._headers(body) })).data;
  }
}
```

---

## 七、错误码

| HTTP | errorCode | 含义 |
|---|---|---|
| 400 | `MISSING_USER` | 缺 X-API-User |
| 400 | `TIME_DRIFT` | 时间漂移 > 3600 秒 |
| 401 | `INVALID_SIGN` | 签名验证失败 |
| 401 | `UNKNOWN_KEY` | access_key 不存在 / 已禁用 |
| 400 | `ACC_210` | 重量超渠道上限 |
| 400 | `ACC_216` | 客户单号重复 |
| 400 | `ACC_219` | HS 编码必须 6-10 位 |
| 400 | `ACC_220` | 件数 ≠ 装箱单行数 |
| 400 | `ACC_221` | 装箱总重 ≠ 货物重量 |
| 400 | `ACC_222` | 装箱单号同行重复 |
| 400 | `ACC_223` | 客户余额 + 授信不足 |
| 400 | `UPS_120207` | UPS 邮编错 |
| 400 | `UPS_120213` | 电话 < 10 位 |

---

## 八、Webhook 异步事件

订阅地址在: 销售中心 → Webhook 端点 → + 新增

事件类型:
- `order.submitted` (取号成功)
- `order.shipped` (出货)
- `order.delivered` (签收)
- `order.exception` (异常)
- `invoice.generated` (账单生成)

请求会带 `X-Webhook-Signature` (HMAC-SHA256), 客户验签。

---

## 九、限流 / 配额

| 项 | 限制 |
|---|---|
| 单 access_key QPS | 10 req/s |
| 单 access_key 每日量 | 10,000 单 |
| 签名时间漂移 | ±60 分钟 |

超限返 `429 Too Many Requests`, 客户需等冷却。

---

## 十、运营自助控台

客户上线后, 给客户开 portal 账号 (跟 access_key 是两套):
- 浏览器登录: `http://8.148.227.76` 用 `customer_logins` 表的密码
- 看自己的订单 / 账单 / 余额 / 实时跟踪
- 改不了别人的, RLS 严格隔离

---

## 联系

| | |
|---|---|
| 商务 | sales@xqt-saas.com |
| 技术 | tech@xqt-saas.com |
| 紧急 | +86-XXX-XXXX-XXXX (7×24) |
