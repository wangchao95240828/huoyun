# Karrio 多承运商网关接入 xqt-saas

> 把 Karrio 当承运商 API 中间件, 一次接入 30+ 承运商 (UPS/FedEx/DHL/SF Express/YunExpress).
> xqt-saas RateEngine 全部走 Karrio 统一 API.

---

## 架构

```
┌─────────────────────────────────────────────────────┐
│            xqt-saas Backend (Spring Boot)            │
│   ┌──────────────────────┐                          │
│   │ RateEngine / Submit  │                          │
│   └────────┬─────────────┘                          │
│            │ provider_code='KARRIO'                  │
│            ▼                                         │
│   ┌──────────────────────┐                          │
│   │ CarrierGatewayRegistry│                          │
│   │   .forChannel(ch)     │                          │
│   └────────┬─────────────┘                          │
│            ▼                                         │
│   ┌──────────────────────┐                          │
│   │ KarrioCarrierGateway │                          │
│   │   submit(ctx)        │                          │
│   └────────┬─────────────┘                          │
└────────────┼─────────────────────────────────────────┘
             │ HTTP POST /v1/shipments
             ▼
┌─────────────────────────────────────────────────────┐
│           Karrio (docker-compose)                    │
│   ┌──────────┬──────────┬──────────┬──────────┐    │
│   │  server  │  worker  │   db     │ dashboard│    │
│   │  :5002   │          │  pg:5432 │  :5001   │    │
│   └─────┬────┴──────────┴──────────┴──────────┘    │
│         │                                            │
│         ▼  (carrier adapters)                       │
│   ┌──────────────────────────────────────────┐     │
│   │ UPS │ FedEx │ DHL │ SF │ YunExpress │... │     │
│   └──────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────┘
             │
             ▼  HTTPS API
       ┌──────────────────────┐
       │ 真承运商 onlinetools.ups.com│
       │ apis.sandbox.fedex.com    │
       │ ...                       │
       └──────────────────────┘
```

---

## 部署步骤 (生产服务器)

### 1. 拷文件到生产

```bash
# 本地
scp -r /Users/chaowang/新航线/xqt-saas/ops/karrio \
  root@8.148.227.76:/opt/xqt-saas/

# 服务器
ssh root@8.148.227.76
cd /opt/xqt-saas/karrio
```

### 2. 改 secret

```bash
# 用真随机值替换 docker-compose.yml 里 3 处 'change_me_in_prod_...'
NEW_KEY=$(openssl rand -base64 32)
sed -i "s/change_me_in_prod_xxxxxxxxxxxxxxxxxx/$NEW_KEY/g" docker-compose.yml

# 改 DB 密码
NEW_DB_PW=$(openssl rand -base64 16)
sed -i "s/karrio_change_me/$NEW_DB_PW/g" docker-compose.yml
```

### 3. 启动

```bash
docker compose up -d
# 等 30 秒初始化, 看 logs
docker compose logs -f server --tail=50
```

### 4. 验证 + 初始化

```bash
curl -i http://localhost:5002/health
# HTTP 200 OK + {"status":"ok"}
```

### 5. 配 Carrier Connection (Dashboard)

```
浏览器: http://8.148.227.76:5001
登录: admin@example.com / demo (改密码!)

→ Settings → Carrier Connections → + Add Connection
   Carrier: UPS
   Carrier ID:  ups_rushing_hub        ← 自己起名, xqt-saas 会用这个
   Account Number: J602B0
   Username/Password: UPS REST API 凭证
   Test Mode: 否 (生产)
   保存

→ 同理加 FedEx / DHL / SF Express / YunExpress
```

### 6. 拿 API Token

```
Dashboard → Settings → API Keys → Generate
复制 token (长串字符)
```

### 7. xqt-saas application.yml 加配置

```yaml
karrio:
  base-url: http://localhost:5002  # 同机部署
  api-token: <步骤 6 的 token>
  timeout-sec: 30
```

### 8. xqt-saas 渠道账号配 KARRIO provider

```sql
-- 在 acc_channel_accounts 表加一条 Karrio 渠道账号
INSERT INTO acc_channel_accounts (
  tenant_id, channel_id, account_no, account_name, provider_code,
  api_key, endpoint_url, is_active
) VALUES (
  '2bda8c16-7b19-4ce6-ab71-9584f5a140ed',
  (SELECT id FROM channels WHERE code='UPS-GROUND-US'),
  'KARRIO-UPS-GROUND',
  'Karrio UPS Ground 网关',
  'KARRIO',
  'ups_rushing_hub',           -- api_key = Karrio carrier_id (步骤 5)
  'ups_ground',                -- endpoint_url = Karrio service code
  true
);
```

### 9. 测制单 — xqt-saas 自动路由到 Karrio

```bash
# 走 UPS-GROUND-US 渠道下 KARRIO-UPS-GROUND 制单账号
# RateEngine 会查 provider_code='KARRIO' → 命中 KarrioCarrierGateway
# 提交时 KarrioCarrierGateway.submit() POST 到 Karrio API
# Karrio 内部调 UPS 真实 API → 返 1Z + PDF
# xqt-saas 落 shipment + label
```

---

## Karrio 支持的承运商 (内置 adapter)

```
UPS / FedEx / DHL Express / USPS / Canada Post / Royal Mail / Australia Post
SF Express / YunExpress / Yanwen / Yto Express / EMS
Amazon MWS / Shippo (proxy) / EasyPost (proxy)
Sendle / Aramex / DPD / TNT / GLS / La Poste
... 总计 30+
```

完整列表:  https://docs.karrio.io/carriers

---

## 为什么用 Karrio (vs. 自己每家接)

| 项 | 自己接 (xqt-saas 现状) | Karrio 网关 |
|---|---|---|
| 接 5 家承运商 | 5 个独立 SDK / 5 套 OAuth / 5 套异常处理 | 1 套 |
| 升级 UPS API v2 → v3 | 自己改 + 测 + 部署 | Karrio 升级即可 |
| 添加新承运商 | 1-2 周开发 | Dashboard 加 connection 10 分钟 |
| 面单/跟踪/费率统一 | 5 种返回格式 | 1 套 schema |
| 多租户隔离 | 走 xqt-saas tenant | Karrio EE 才支持 (开源版需自行隔离) |

---

## ⚠ 局限

1. **Karrio 开源版无 multi-tenant** — 一个 Karrio 实例所有租户共享
   - 解决: xqt-saas 内层做隔离 (查询时带 tenant_id)
   - 或: 每个租户跑独立 Karrio docker
   - 或: 买 Karrio Enterprise Edition (有 tenant 隔离)

2. **AGPL/LGPL license** — 不能闭源分发 Karrio 代码
   - 但 Karrio 作为独立服务 (HTTP 调用) 不传染 xqt-saas
   - 跟 xqt-saas 解耦, 不修改 Karrio 源码就安全

3. **额外资源消耗** — Karrio 自己要 PG + Redis + 2 Python 容器
   - 大约 2GB RAM + 0.5 CPU

---

## 测试 — 不部署 Karrio 时 xqt-saas 还能用吗?

可以. KarrioCarrierGateway 只在 `provider_code='KARRIO'` 时触发. 现有 UPS-GROUND-US 渠道继续走 UpsGroundCarrierGateway (直连 UPS API), 不影响。

---

## 滚动迁移路线

1. **Week 1**: 部署 Karrio, 配 UPS connection, 灰度 1 个测试客户走 Karrio
2. **Week 2**: 对比 Karrio vs. 直连 UPS 性能 + 成功率, 没问题切全部 UPS 走 Karrio
3. **Week 3**: 加 FedEx connection (Karrio 5 分钟搞定)
4. **Week 4**: 加 DHL / SF Express / YunExpress
5. **Week 5**: 老的 UpsGroundCarrierGateway 设 deprecated, 计划 1 个月后下线
