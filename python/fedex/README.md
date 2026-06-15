# FedEx 物流轨迹爬虫 → xqt-saas 集成

## 架构

```
┌─────────────┐  1. 拉待跟踪号           ┌─────────────────┐
│  runner.py  │─────────────────────────►│  xqt-saas       │
│             │                           │  /api/acc/      │
│             │  2. GET /{trackingNo}     │  shipments      │
│             │     调爬虫 HTTP           │                 │
│             │     ↓                     │                 │
│             │  ┌──────────────────┐    │                 │
│             │  │ main.py (FastAPI │    │                 │
│             │  │  + patchright)   │    │                 │
│             │  │  端口 8080       │    │                 │
│             │  └──────────────────┘    │                 │
│             │                           │                 │
│             │  3. POST ingest          │  /api/acc/      │
│             │─────────────────────────►│  tracking/      │
└─────────────┘                           │  ingest         │
                                          └─────────────────┘
                                                  │
                                                  ▼
                                       tracking_events 表
                                       + shipments.delivered_at
```

## 文件

| 文件 | 来源 | 作用 |
|---|---|---|
| `main.py` | dev-jiang 分支 | FedEx 爬虫 FastAPI 服务 (用 patchright/playwright) |
| `schemas.py` | dev-jiang 分支 | Result/Response/Event pydantic 模型 |
| `data.py` | dev-jiang 分支 | USA_STATES / 时区数据 |
| `pyproject.toml` | dev-jiang 分支 | uv 依赖管理 |
| `pusher.py` | 本分支 | 把现成 result.json 推到 xqt-saas（手动批量用） |
| `runner.py` | 本分支 | 全自动：拉 pending → 调爬虫 HTTP → 推 ingest |
| `.env.example` | 本分支 | 环境变量样例 |

## 一次性推已有 JSON

```bash
export XQT_BASE_URL=http://127.0.0.1:18103
export XQT_INGEST_TOKEN=<和后端 application.yml 一致>

# 单个文件
python pusher.py /Users/chaowang/新航线/result.json

# 整个目录
python pusher.py "/data/fedex-results/*.json"

# 从 stdin
cat result.json | python pusher.py -
```

## 定时自动跑（两个 systemd 单元）

### 1. 爬虫常驻服务 (main.py)

```
# /etc/systemd/system/fedex-crawler.service
[Unit]
Description=FedEx crawler FastAPI service
After=network.target

[Service]
Type=simple
WorkingDirectory=/opt/xqt-saas/python/fedex
EnvironmentFile=/opt/xqt-saas/python/fedex/.env
ExecStart=/root/.local/bin/uv run main.py --port=8080
Restart=always
RestartSec=10
StandardOutput=append:/var/log/fedex-crawler.log

[Install]
WantedBy=multi-user.target
```

### 2. runner timer (每 30 min)

```
# /etc/systemd/system/fedex-tracking.service
[Unit]
Description=FedEx tracking puller
After=fedex-crawler.service

[Service]
Type=oneshot
WorkingDirectory=/opt/xqt-saas/python/fedex
EnvironmentFile=/opt/xqt-saas/python/fedex/.env
ExecStart=/usr/bin/python3 /opt/xqt-saas/python/fedex/runner.py
StandardOutput=append:/var/log/fedex-tracking.log

# /etc/systemd/system/fedex-tracking.timer
[Unit]
Description=FedEx tracking every 30 min

[Timer]
OnBootSec=2min
OnUnitActiveSec=30min
Persistent=true

[Install]
WantedBy=timers.target
```

### 启用

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now fedex-crawler.service
sudo systemctl enable --now fedex-tracking.timer
journalctl -u fedex-tracking -f   # 看运行
```

## result.json 格式（必须）

```json
{
  "carrier": "FEDEX",
  "tracking_number": "870076729148",
  "status": "Delivered",
  "service": "FedEx Ground",
  "ship_datetime": "2026-05-28T00:00:00Z",
  "delivery_datetime": "2026-06-03T12:38:48-04:00",
  "signed_by": "CCINDY",
  "weight_kg": 18.87,
  "scan_history": [
    { "datetime": "2026-05-28T00:00:00-07:00", "status": "Picked up",
      "location": "WALNUT, CA", "delivered": false },
    { "datetime": "2026-06-03T12:38:48-04:00", "status": "Delivered",
      "location": "New Castle, DE", "delivered": true }
  ]
}
```

字段说明：
- `tracking_number` **必填**
- `carrier` 可选，默认 `FEDEX`
- `scan_history[]` **必填**，每条 `datetime` + `status` 必填
- `delivery_datetime` 有值时自动更新 `shipments.delivered_at` 和 `status=DELIVERED`

## 状态映射（FedEx 原文 → xqt 内部 enum）

后端 `AccTrackingIngestController.normalize()` 自动映射：

| FedEx 原始 | xqt normalized_status |
|---|---|
| Picked up / Arrived / Departed / Left FedEx / At local | `IN_TRANSIT` |
| On FedEx vehicle for delivery / Out for delivery | `OUT_FOR_DELIVERY` |
| Delivered | `DELIVERED` |
| Exception / Delay / Unable to deliver | `EXCEPTION` |
| Returned / Return to sender | `RETURNED` |

## 幂等

后端按 `(tracking_no, event_time, raw_status)` 三元组去重，同一事件多次推送只落 1 条。所以可以放心重复跑 runner。

## 鉴权

ingest endpoint **不走** JWT，用 service-to-service token：
- 服务端 `application.yml`: `xqt.tracking.ingest-token: <random>`
- 爬虫端 `.env`: `XQT_INGEST_TOKEN=<same>`
- 请求头 `X-Ingest-Token: <same>`

生产建议：用 `openssl rand -hex 32` 生成 32 字节随机串，nginx 把 `/api/acc/tracking/ingest` 限制到内网 IP。

## 故障排查

| 现象 | 原因 |
|---|---|
| HTTP 401 invalid X-Ingest-Token | token 不匹配，检查两端是否一致 |
| inserted=0 skipped=N | 全部去重，事件之前已 ingest 过 |
| shipmentId=null | tracking_no 在 cartons 表里找不到匹配；事件仍写入但未关联 shipment |
| crawler 超时 | 单 trackingNo 60s 超时，调 `runner.py` 的 timeout 或排查 FedEx 风控 |
