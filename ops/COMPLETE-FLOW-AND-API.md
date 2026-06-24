# xqt-saas 完整业务流程 + 客户 API 对接指南

> 生产: **http://8.148.227.76** · 数据库: PG 15 · UPS gateway: 真生产 API

---

# Part 1: 完整业务流程 (UI 操作, 真出单)

## 0. 登录

```
URL:    http://8.148.227.76/
用户:   admin
密码:   admin123
```

Ctrl+Shift+R 强刷一次后再操作。

---

## Stage 1: 制单 (5 分钟)

### 1.1 进入制单页

**制单中心 → 快件订单** → 右上角点 **「+ 新增订单」**

### 1.2 填表 9 段 (按这个真实参数填就能出单)

| 段 | 字段 | 填什么 |
|---|---|---|
| 基本 | 客户单号 | 留空 (自动生成) |
| 基本 | 客户 | `SELLER-DEMO` |
| 渠道 | 发货产品 | `UPS-GROUND-US` |
| 渠道 | 制单账号 | `J602B0` (真 UPS 凭证已配) |
| 渠道 | 包裹类型 | `PARCEL` |
| 货物 | 英文品名 | `T-shirt` |
| 货物 | 中文品名 | `T 恤` |
| 货物 | 件数 | `1` |
| 货物 | 重量(kg) | `1.5` |
| 货物 | 货币 | `USD` |
| **收件人** | 公司 | `Test Co` |
| **收件人** | 收件人 | `John Smith` |
| **收件人** | **电话** | `3105551234` ⚠ **必须 ≥ 10 位数字** |
| **收件人** | **邮编** | `90210` ⚠ **必须 5 位美国邮编** |
| **收件人** | 地址 | `123 Main Street` |
| **收件人** | 城市 | `Beverly Hills` |
| **收件人** | 省/州 | `CA` (2 位州码) |
| **收件人** | 国家 | `US` |
| 申报 | 英文/中文 | T-shirt / T 恤 |
| 申报 | 数量 / 单价 | 1 / 18.88 |
| 申报 | HS 编码 | 点 ▼ 下拉选 `6109.10` |
| 装箱 | 装箱单号 | `PKG-1` |
| 装箱 | 重量 | `1.5` (跟货物重量一致!) |
| 装箱 | 数量 / 单价 | 1 / 18.88 |
| 装箱 | HS 编码 | `6109100020` |

### 1.3 保存

点 **「确认添加」** → 弹"保存成功" → 订单 status=`DRAFT`

---

## Stage 2: 测 8 个阻止下单点 (5 分钟)

故意填错,验证拦截:

| 试啥 | 预期红字 |
|---|---|
| 客户单号填 Stage 1 已建的 | "客户单号已存在 (ACC_216)" |
| 电话填 `555` (短) | UPS 拒单 120213 中文化 |
| 邮编填 `1234` (4 位) | UPS 拒单 120207 中文化 |
| HS 填 `6109` (4 位) | "HS 编码必须 6-10 位数字" |
| 重量 `1.5`, 装箱重 `2.5` | "装箱总重 vs 货物重量 不一致" |
| 件数 `2`, 装箱 1 行 | "装箱单行数 1 跟件数 2 不一致" |
| 装箱单 2 行同号 | "装箱单号 PKG-1 跟前面行重复" |
| 重量 `1000` | "重量超渠道上限 70kg (ACC_210)" |

---

## Stage 3: 提交订单 → 真 UPS 出单 (3 分钟)

### ⚠ 不在详情 modal 里, 在列表顶部工具栏

1. **制单中心 → 未提交 tab** (orders-draft)
2. 找到 Stage 1 那单 → **左侧 checkbox 勾上** ☑
3. **顶部工具栏** → 点 **「✓ 批量提交」** (绿色对勾)

### 成功后返:

```json
{
  "trackingNo": "1ZJ602B0XXXXXXXXX",   ← 真 UPS 1Z 开头
  "shipmentNo": "SHP-XXX",
  "carrierMasterTrackingNo": "1ZJ602B0XXXXXXXXX",
  "status": "SUBMITTED"
}
```

### 失败常见原因 (UPS 拒单):

| 拒单码 | 原因 | 解决 |
|---|---|---|
| 120213 | 电话 < 10 位 | 重新编辑修电话 |
| 120207 | 邮编错 | 5 位美国邮编 |
| 120103 | 收件人姓名空 | 填上 |
| 120536 | 地址超 35 字符 | 缩短 |
| 120548 | 重量单位混 | 走默认即可 |

---

## Stage 4: 下载真 UPS 面单 (1 分钟)

订单提交后:

1. 列表里**点订单行**进详情
2. 操作菜单 → **「下载面单」**
3. 浏览器下载 **真 UPS PDF (28KB, PDF v1.7)**
4. 打开 PDF → 含真 UPS 1Z tracking 条码 + 收件人地址 + UPS 服务级 (Ground)

或者直接调:

```bash
TOKEN=...  # 从登录返回拿
curl -o label.pdf -H "Authorization: Bearer $TOKEN" \
  http://8.148.227.76/api/acc/labels/by-order/{orderId}
```

---

## Stage 5: 自动建 charges + 审核 → balance 联动 (5 分钟)

### 5.1 看自动建的 charges

**核算中心 → 运费核算** → 找刚才那单

✅ **预期**: 3 笔 AR charges
- 运费 (Freight): ~6.26 USD, status=ESTIMATED
- 燃油 (Fuel): ~1.10 USD
- 偏远 (Remote): ~2.50 USD (90210 是否触发偏远视配置)

### 5.2 审核 charges

**核算中心 → 待核费用** → 行尾点 **「✓ 审核」**

✅ **自动发生** (P0-B2 验证):
- charges.audit_status = `AUDITED`
- balance_ledger 新增 DEBIT 6.26 USD (客户欠款 +6.26)
- acc_gl_vouchers 自动写借/贷凭证

### 5.3 验证客户欠款

**财务中心 → 应收款项** → `SELLER-DEMO`

✅ **预期**: 未结金额 **+6.26 USD** (之前 0, 现在 6.26)

---

## Stage 6: 出账单 + 收款 (5 分钟)

### 6.1 生成账单

**财务中心 → 客户账单** → **「+ 生成账单」**

- 客户: `SELLER-DEMO`
- 区间: 今天-今天
- 保存 → 行尾点 **「确认」** → status=`CONFIRMED`

### 6.2 录收款

**财务中心 → 收款记录** → **「+ 新增」**

| 字段 | 值 |
|---|---|
| 收款单号 | `RCV-001` |
| 客户 | `SELLER-DEMO` |
| 账户 | USD 主结算户 |
| 金额 | 6.26 |
| 日期 | 今天 |

保存 → status=`UNAUDITED`

### 6.3 审核收款

**待审收款 tab** → 行尾点 **「审核」**

✅ **自动发生**:
- payments.audit_status = `AUDITED`
- balance_ledger 新增 CREDIT 6.26 USD (客户欠款 -6.26)
- financial_accounts (USD 主结算户) balance +6.26
- 凭证: 借 银行 / 贷 应收账款

### 6.4 验证欠款归零

**财务中心 → 应收款项** → `SELLER-DEMO` 未结金额回到 **0** ✓

---

## Stage 7: 异常流测试 (5 分钟)

### 7.1 申请作废

1. 找一单 → 操作菜单 → **「申请作废」** → 填理由
2. **作废待审 tab** → 行尾点 **「审核」**

✅ 验证: charges 全 VOID, balance_ledger 反向 CREDIT 退钱

### 7.2 退件 4 步

1. **客服中心 → 退件 → 「+ 新增」**
2. 走 **confirm → verify → audit** 4 步
3. 验证 shipment.status → `RETURNING` → `RETURNED`
4. 反向 charge ADJUSTED 退款

### 7.3 测**额度不足拒提交**

1. 建订单, 客户选 `TEST-NEW-001` (信用 0)
2. 提交 → 拒绝 "余额+授信不足"

---

## Stage 8: 报表查询 (3 分钟)

1. **财务中心 → 利润查询** → 看 revenue / cost / profit (汇率快照 USD→CNY)
2. **报表统计 → 运营日报** → 4 KPI
3. **报表统计 → 客户分析** → piece/weight/profitRate/mainCountry
4. **财务中心 → 账期已到** → 看哪些客户欠款过期
5. **财务中心 → 应付账龄 by 供应商**

---

# Part 2: 客户 API 对接指南 (你的 ToB 客户调你的 API)

## 1. 凭证体系

xqt-saas 用 4 字段签名认证 (兼容 ACC 老 SDK):

| 字段 | HTTP Header | 说明 |
|---|---|---|
| access_key | `X-API-User` | 凭证 ID (类似 username) |
| timestamp | `X-API-Time` | unix 秒级时间戳 (UTC) |
| version | `X-API-Version` | API 版本号, 当前传 `1` |
| signature | `X-API-Sign` | MD5 签名 (32 位小写) |

## 2. 给客户开通凭证 (你管理员操作)

### 方式 A: UI 开通

**API 对接中心 → 客户凭证 → 「+ 新增」**

- 客户: 选具体客户 (e.g. `SELLER-DEMO`)
- 备注: 客户公司名
- 保存

弹出框显示一次性 **secret_key** (e.g. `sk_2026_abcd1234...`)

⚠ **secret 只显示一次, 客户务必复制保存**, 数据库只存 hash, 丢了只能 revoke 重发。

### 方式 B: SQL 直接插

```sql
INSERT INTO api_credentials (tenant_id, owner_type, owner_id, access_key, secret_hash, status, scopes)
SELECT '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid,
       'CUSTOMER',
       id,
       'ak_seller_demo_' || substring(md5(random()::text), 1, 8),
       crypt('sk_seller_demo_' || substring(md5(random()::text), 1, 16), gen_salt('bf')),
       'ACTIVE',
       ARRAY['orders', 'tracking', 'balance', 'rates']
  FROM customers WHERE code = 'SELLER-DEMO'
RETURNING access_key;
```

## 3. 签名算法 (兼容 ACC 老 SDK)

默认走 **COMPAT 模式** (ACC 散字段签名)。

```
1. 把 body 的每个 top-level 字段拆出来 (a=1, b=2, c=3)
2. 加上 4 个 header 字段: time, user, version
3. 按 key ASCII 升序排
4. 把所有 value 拼成 join(',', values) (注意: 不带 key, 只拼 value)
5. 末尾追加 secret_key 明文
6. md5(整串).toLowerCase() = signature
```

### 例子 (Python)

```python
import hashlib, time, json, requests

ACCESS_KEY = "ak_seller_demo_xxx"
SECRET_KEY = "sk_seller_demo_xxx"     # 一次性, 妥善保存
BASE_URL   = "http://8.148.227.76"

def sign_compat(body_dict, ts, version='1'):
    """ACC 兼容签名 — 散字段拼接"""
    # 1. 把 body 字段平铺到 signedParams
    signed = dict(body_dict)
    # 2. 加 header 字段
    signed['time']    = str(ts)
    signed['user']    = ACCESS_KEY
    signed['version'] = version
    # 3. 按 key 排序, 拼 values
    sorted_keys = sorted(signed.keys())
    values = [str(signed[k]) if not isinstance(signed[k], (dict, list)) 
              else json.dumps(signed[k], separators=(',', ':'), ensure_ascii=False)
              for k in sorted_keys]
    payload = ','.join(values) + SECRET_KEY
    # 4. md5
    return hashlib.md5(payload.encode('utf-8')).hexdigest()

# 调创建订单
body = {
    'no':       'CUST-API-001',
    'product':  'UPS-GROUND-US',
    'country':  'US',
    'weight':   1.5,
    'piece':    1,
    'currency': 'USD',
    'receiver': {
        'company':  'Test Co', 'name': 'John Smith',
        'phone':    '3105551234', 'address': '123 Main St',
        'city':     'Beverly Hills', 'province': 'CA',
        'postcode': '90210', 'country': 'US'
    },
    'declare': [{
        'name': 'T-shirt', 'cnName': 'T恤', 
        'quantity': 1, 'price': 18.88, 'hsCode': '6109100020'
    }]
}

ts = int(time.time())
sig = sign_compat(body, ts)

r = requests.post(f'{BASE_URL}/api/customer-api/orders', json=body, headers={
    'X-API-User':    ACCESS_KEY,
    'X-API-Time':    str(ts),
    'X-API-Version': '1',
    'X-API-Sign':    sig,
    'Content-Type':  'application/json'
})
print(r.json())
```

### V2 模式 (推荐新客户, 更安全)

V2 不把 body 字段散开, 而是 `body=sha256(整 body)`, 然后只签 4 个 token:

```python
def sign_v2(body_str, ts, version='1'):
    body_hash = hashlib.sha256(body_str.encode('utf-8')).hexdigest()
    signed = {'body': body_hash, 'time': str(ts), 'user': ACCESS_KEY, 'version': version}
    sorted_keys = sorted(signed.keys())
    values = [signed[k] for k in sorted_keys]
    payload = ','.join(values) + SECRET_KEY
    return hashlib.md5(payload.encode('utf-8')).hexdigest()

# 请求头加上
headers['X-API-Sign-Mode'] = 'V2'
```

## 4. API endpoint 全表

### 4.1 基础

| Method | Path | 说明 |
|---|---|---|
| GET | `/api/customer-api/ping` | 健康检查 |
| GET | `/api/customer-api/balance` | 客户预扣余额 |
| GET | `/api/customer-api/channels` | 可用渠道列表 |
| GET | `/api/customer-api/products` | 可用产品 |

### 4.2 订单

| Method | Path | 说明 |
|---|---|---|
| POST | `/api/customer-api/orders` | 预报订单 (DRAFT) |
| POST | `/api/customer-api/orders/{no}/pre-submit` | 提交前校验+预报价 |
| POST | `/api/customer-api/orders/{no}/submit` | 真提交 → 调 UPS 拿 tracking + label |
| POST | `/api/customer-api/orders/{no}/cancel` | 取消订单 |
| POST | `/api/customer-api/orders/status` | 批量查状态 |
| POST | `/api/customer-api/orders/query` | 列表查询 |
| POST | `/api/customer-api/orders/sync` | 增量同步 |

### 4.3 报价 / 标签 / 轨迹

| Method | Path | 说明 |
|---|---|---|
| POST | `/api/customer-api/rates/quote` | 实时运费报价 |
| GET | `/api/customer-api/labels/{trackingNo}` | 下载 label PDF |
| POST | `/api/customer-api/tracking/query` | 批量轨迹 |
| GET | `/api/customer-api/tracking/{trackingNo}/timeline` | 单单完整轨迹 |

### 4.4 财务

| Method | Path | 说明 |
|---|---|---|
| GET | `/api/customer-api/finance/balance` | 余额 + 信用 |
| GET | `/api/customer-api/finance/prepay-details.csv` | 预扣流水 CSV |
| GET | `/api/customer-api/finance/invoices` | 账单列表 |

## 5. 错误码字典 (50 个 ACC_2xx 业务码)

| 错误码 | 含义 |
|---|---|
| ACC_100 | 功能未开通 |
| ACC_101-113 | 鉴权阶段错 (签名/时间/用户) |
| ACC_201 | 申报+装箱二选一缺 |
| ACC_203 | 客户无产品权限 |
| ACC_204 | 找不到产品 |
| ACC_210 | 重量超渠道范围 |
| ACC_213 | 申报金额不合理 |
| ACC_214 | 电池货物必填 BatteryCode |
| ACC_216 | 客户单号重复 |
| ACC_219 | HS 编码格式错 |
| ACC_223 | 客户余额 + 授信不足 |
| ACC_233 | 转单号已被占用 |
| ACC_237 | 重提守卫 (已审费用) |
| ACC_240 | carrier gateway 报错 |
| ACC_429 | 限流 |
| ACC_500 | 系统错 |

完整 50 个码: 见 `apps/backend/src/main/java/com/xqt/saas/customerapi/AccErrorCode.java`

## 6. Webhook (异步推送给客户)

xqt-saas 在以下事件发生时主动 POST 到客户 webhook URL:

| 事件 | 触发 |
|---|---|
| `order.submitted` | 订单成功取号 |
| `order.cancelled` | 订单取消 |
| `tracking.event` | 新轨迹节点 (含异常) |
| `tracking.delivered` | 已签收 |
| `tracking.exception` | 扣件/退件/丢件 |
| `invoice.issued` | 新账单生成 |

### 配置 webhook

**API 对接中心 → Webhook endpoints → 「+ 新增」**

- URL: 客户自己的回调地址 (HTTPS)
- Secret: 自动生成
- 订阅事件: 多选

### Webhook 签名校验 (客户端)

```python
import hmac, hashlib

def verify_webhook(body_bytes, signature_header, webhook_secret):
    expected = hmac.new(webhook_secret.encode(), body_bytes, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, signature_header)
```

xqt-saas 每个 webhook 请求带:
- `X-Webhook-Sign`: HMAC SHA256(body, secret)
- `X-Webhook-Event`: 事件类型
- `X-Webhook-Delivery`: 唯一 delivery ID (重试用同一个)

---

# Part 3: 客户对接最小可用代码 (cURL + Python + Node.js)

## cURL 测连接

```bash
# 1. ping
curl http://8.148.227.76/api/customer-api/ping \
  -H "X-API-User: YOUR_ACCESS_KEY" \
  -H "X-API-Time: $(date +%s)" \
  -H "X-API-Version: 1" \
  -H "X-API-Sign: <calculated_signature>"

# 2. 查余额
curl http://8.148.227.76/api/customer-api/balance \
  -H ...
```

## Python 完整 SDK 雏形

```python
# xqt_client.py
import hashlib, time, json, requests
from typing import Dict, Any

class XqtClient:
    def __init__(self, access_key, secret_key, base='http://8.148.227.76', mode='COMPAT'):
        self.ak, self.sk, self.base, self.mode = access_key, secret_key, base, mode
    
    def _sign(self, body, ts):
        if self.mode == 'V2':
            body_str = json.dumps(body, separators=(',', ':'), ensure_ascii=False)
            body_hash = hashlib.sha256(body_str.encode()).hexdigest()
            signed = {'body': body_hash, 'time': str(ts), 'user': self.ak, 'version': '1'}
        else:
            signed = {k: v for k, v in body.items()}
            signed.update({'time': str(ts), 'user': self.ak, 'version': '1'})
        
        keys = sorted(signed.keys())
        values = [str(signed[k]) if not isinstance(signed[k], (dict, list))
                  else json.dumps(signed[k], separators=(',', ':'), ensure_ascii=False)
                  for k in keys]
        return hashlib.md5((','.join(values) + self.sk).encode()).hexdigest()
    
    def _call(self, method, path, body=None):
        ts = int(time.time())
        body = body or {}
        sig = self._sign(body, ts)
        headers = {
            'X-API-User': self.ak, 'X-API-Time': str(ts),
            'X-API-Version': '1', 'X-API-Sign': sig,
            'X-API-Sign-Mode': self.mode,
            'Content-Type': 'application/json'
        }
        url = self.base + path
        if method == 'GET':
            return requests.get(url, headers=headers).json()
        return requests.post(url, json=body, headers=headers).json()
    
    # 业务方法
    def ping(self): return self._call('GET', '/api/customer-api/ping')
    def balance(self): return self._call('GET', '/api/customer-api/balance')
    def channels(self): return self._call('GET', '/api/customer-api/channels')
    def create_order(self, body): return self._call('POST', '/api/customer-api/orders', body)
    def submit_order(self, no): return self._call('POST', f'/api/customer-api/orders/{no}/submit')
    def track(self, tracking_no): return self._call('GET', f'/api/customer-api/tracking/{tracking_no}/timeline')

# 使用
c = XqtClient('YOUR_AK', 'YOUR_SK')
print(c.ping())
print(c.balance())
print(c.channels())

# 完整下单
order = c.create_order({
    'no': 'API-001', 'product': 'UPS-GROUND-US', 'country': 'US',
    'weight': 1.5, 'piece': 1, 'currency': 'USD',
    'receiver': {'company': 'Test', 'name': 'John', 'phone': '3105551234',
                 'address': '123 Main', 'city': 'Beverly Hills', 'province': 'CA',
                 'postcode': '90210', 'country': 'US'},
    'declare': [{'name': 'T-shirt', 'cnName': 'T恤', 'quantity': 1, 'price': 18.88, 'hsCode': '6109100020'}]
})
print('创建:', order)

submitted = c.submit_order(order['no'])
print('提交:', submitted)
print('tracking:', submitted['result']['trackingNo'])
```

## Node.js 版

```javascript
const crypto = require('crypto');
const fetch = require('node-fetch');

class XqtClient {
  constructor(ak, sk, base = 'http://8.148.227.76', mode = 'COMPAT') {
    this.ak = ak; this.sk = sk; this.base = base; this.mode = mode;
  }
  
  sign(body, ts) {
    let signed;
    if (this.mode === 'V2') {
      const bodyStr = JSON.stringify(body);
      const bodyHash = crypto.createHash('sha256').update(bodyStr).digest('hex');
      signed = { body: bodyHash, time: String(ts), user: this.ak, version: '1' };
    } else {
      signed = { ...body, time: String(ts), user: this.ak, version: '1' };
    }
    const keys = Object.keys(signed).sort();
    const values = keys.map(k => typeof signed[k] === 'object' ? JSON.stringify(signed[k]) : String(signed[k]));
    return crypto.createHash('md5').update(values.join(',') + this.sk).digest('hex');
  }
  
  async call(method, path, body = {}) {
    const ts = Math.floor(Date.now() / 1000);
    const sig = this.sign(body, ts);
    const r = await fetch(this.base + path, {
      method, body: method === 'GET' ? undefined : JSON.stringify(body),
      headers: {
        'X-API-User': this.ak, 'X-API-Time': String(ts),
        'X-API-Version': '1', 'X-API-Sign': sig,
        'X-API-Sign-Mode': this.mode,
        'Content-Type': 'application/json'
      }
    });
    return r.json();
  }
  
  ping()        { return this.call('GET', '/api/customer-api/ping'); }
  balance()     { return this.call('GET', '/api/customer-api/balance'); }
  channels()    { return this.call('GET', '/api/customer-api/channels'); }
  createOrder(body) { return this.call('POST', '/api/customer-api/orders', body); }
  submitOrder(no)   { return this.call('POST', `/api/customer-api/orders/${no}/submit`); }
  track(no)         { return this.call('GET', `/api/customer-api/tracking/${no}/timeline`); }
}

const c = new XqtClient('YOUR_AK', 'YOUR_SK');
c.ping().then(console.log);
```

---

# Part 4: 沙箱 vs 生产

| 环境 | URL | 凭证 | UPS gateway |
|---|---|---|---|
| **生产** | http://8.148.227.76 | 真客户 ak/sk | UPS 真 API (扣真钱) |
| **沙箱** | 暂无独立沙箱 | 同生产 | UPS 切 `endpoint_url=https://wwwcie.ups.com/api` (UPS 测试库) |

切到沙箱: 修改 `acc_channel_accounts.endpoint_url` 即可。

---

# Part 5: 给你的客户的对接邮件模板

```
Subject: 您的 xqt-saas API 凭证 + 对接指南

您好,

您的 API 凭证已开通:

  access_key: ak_xxxxxxxx
  secret_key: sk_yyyyyyyy        ← 仅显示一次, 请妥善保存

测试 URL: http://8.148.227.76/api/customer-api/ping

签名说明 + Python/Node SDK 雏形见附件:
  - https://github.com/wangchao95240828/huoyun/blob/main/ops/COMPLETE-FLOW-AND-API.md

支持的接口:
  - 下单/提交/取消/查询/批量
  - 实时报价
  - 真 UPS 出单 + label PDF 下载
  - 轨迹查询/批量同步
  - 余额查询
  - Webhook 推送 (提交/签收/异常)

错误码字典: 50 个 ACC_2xx 业务码

技术问题反馈: support@yourcompany.com
```
