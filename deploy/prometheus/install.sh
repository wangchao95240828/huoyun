#!/bin/bash
# Prometheus + Alertmanager 一键部署（在生产服务器上跑）
# 用法: scp deploy/prometheus/*.{yml,sh} root@8.148.227.76:/tmp/ && \
#       ssh root@8.148.227.76 'bash /tmp/install.sh'
set -euo pipefail

CONF_DIR=/etc/prometheus
DATA_DIR=/var/lib/prometheus

mkdir -p "$CONF_DIR" "$DATA_DIR"
cp -f /tmp/prometheus.yml /tmp/alerts.yml "$CONF_DIR/"
chown -R 65534:65534 "$DATA_DIR"

# 启停旧容器
docker rm -f prometheus 2>/dev/null || true

docker run -d \
  --name prometheus \
  --restart unless-stopped \
  --network host \
  -v "$CONF_DIR":/etc/prometheus:ro \
  -v "$DATA_DIR":/prometheus \
  prom/prometheus:latest \
  --config.file=/etc/prometheus/prometheus.yml \
  --storage.tsdb.path=/prometheus \
  --storage.tsdb.retention.time=30d

echo "等 5 秒让 Prometheus 启动 ..."
sleep 5

# 验证抓取
curl -s 'http://127.0.0.1:9090/api/v1/targets' | \
  python3 -c 'import sys,json; [print(t["labels"]["job"], t["health"]) for t in json.load(sys.stdin)["data"]["activeTargets"]]'

# 验证 backend 指标已暴露
curl -s -o /dev/null -w 'backend /actuator/prometheus → %{http_code}\n' \
  http://127.0.0.1:18103/actuator/prometheus

echo "完成。Prometheus UI: http://8.148.227.76:9090"
echo "告警规则状态: http://8.148.227.76:9090/alerts"
