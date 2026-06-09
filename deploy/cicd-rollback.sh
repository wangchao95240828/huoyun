#!/usr/bin/env bash
# deploy/cicd-rollback.sh — 紧急回滚：把上一次部署的 jar + web 切回来
#
# 用法（在服务器上）：
#   sudo /opt/xqt-saas/scripts/cicd-rollback.sh
#
# 适用：CD 部署后线上挂了，要快速回到上一个版本

set -euo pipefail

BACKEND_JAR=/opt/xqt-saas/backend/xqt-backend.jar
BACKEND_JAR_BAK=/opt/xqt-saas/backend/xqt-backend.jar.bak
WEB_ROOT=/opt/xqt-saas/web
WEB_ROOT_BAK=/opt/xqt-saas/web.bak

if [ ! -f "$BACKEND_JAR_BAK" ]; then
  echo "✗ 没有 backend 备份 $BACKEND_JAR_BAK，无法回滚"
  exit 1
fi

echo "▶ 1/4 回滚 backend jar"
cp "$BACKEND_JAR_BAK" "$BACKEND_JAR"

echo "▶ 2/4 重启 backend"
systemctl restart xqt-backend

echo "▶ 3/4 等健康检查（60s 超时）"
for i in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:18103/actuator/health 2>/dev/null | grep -q UP; then
    echo "  ✓ healthy"
    break
  fi
  sleep 2
done

if [ -d "$WEB_ROOT_BAK" ]; then
  echo "▶ 4/4 回滚 web dist"
  rm -rf "$WEB_ROOT"
  mv "$WEB_ROOT_BAK" "$WEB_ROOT"
  echo "  ✓ web rolled back"
else
  echo "▶ 4/4 跳过 web 回滚（没有备份）"
fi

echo
echo "✓ 回滚完成。当前版本："
md5sum "$BACKEND_JAR" | head -1
ls -la "$WEB_ROOT/index.html"
