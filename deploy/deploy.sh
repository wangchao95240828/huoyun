#!/usr/bin/env bash
# deploy/deploy.sh — 一键部署到远程服务器
#
# 工作流：
#   1. 本地构建 backend / web docker images
#   2. docker save 成 tar
#   3. scp tar + docker-compose.prod.yml + db/ 到服务器
#   4. SSH 进去 docker load + docker compose up -d
#
# 使用：
#   DEPLOY_HOST=user@host DEPLOY_PATH=/opt/xqt-saas ./deploy/deploy.sh
# 或者用 .env：
#   echo "DEPLOY_HOST=root@8.148.227.76" > deploy/.deploy.env
#   echo "DEPLOY_PATH=/opt/xqt-saas" >> deploy/.deploy.env
#   ./deploy/deploy.sh
#
# 依赖（本地）：docker, ssh, scp, tar
# 依赖（远端）：docker, docker compose

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# 加载 .deploy.env（如果有）
if [[ -f "$SCRIPT_DIR/.deploy.env" ]]; then
  # shellcheck source=/dev/null
  source "$SCRIPT_DIR/.deploy.env"
fi

DEPLOY_HOST="${DEPLOY_HOST:?DEPLOY_HOST is required (e.g. root@8.148.227.76)}"
DEPLOY_PATH="${DEPLOY_PATH:-/opt/xqt-saas}"
SSH_PORT="${SSH_PORT:-22}"
SSH_OPTS="${SSH_OPTS:--o StrictHostKeyChecking=accept-new}"

BACKEND_IMG="xqt-backend:prod"
WEB_IMG="xqt-web:prod"

step() { echo -e "\n\033[1;34m▶ $*\033[0m"; }
ok() { echo -e "\033[1;32m✓ $*\033[0m"; }
warn() { echo -e "\033[1;33m! $*\033[0m"; }

# ─── 1. 本地构建 ───
step "1/5 本地构建 backend 镜像"
docker build --platform linux/amd64 -t "$BACKEND_IMG" "$REPO_ROOT/apps/backend"
ok "backend image ready"

step "2/5 本地构建 web 镜像"
docker build --platform linux/amd64 -t "$WEB_IMG" "$REPO_ROOT/apps/web"
ok "web image ready"

# ─── 3. 打包镜像 + 配置 ───
step "3/5 打包镜像 + 配置"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
docker save "$BACKEND_IMG" "$WEB_IMG" | gzip > "$TMP/images.tar.gz"
ok "images.tar.gz: $(du -h "$TMP/images.tar.gz" | cut -f1)"

# ─── 4. 上传到远端 ───
step "4/5 上传到 $DEPLOY_HOST:$DEPLOY_PATH"
ssh -p "$SSH_PORT" $SSH_OPTS "$DEPLOY_HOST" "mkdir -p '$DEPLOY_PATH/db'"

# 镜像 + compose + db/
scp -P "$SSH_PORT" $SSH_OPTS "$TMP/images.tar.gz" "$DEPLOY_HOST:$DEPLOY_PATH/images.tar.gz"
scp -P "$SSH_PORT" $SSH_OPTS "$SCRIPT_DIR/docker-compose.prod.yml" "$DEPLOY_HOST:$DEPLOY_PATH/docker-compose.yml"

if [[ -f "$SCRIPT_DIR/.env" ]]; then
  scp -P "$SSH_PORT" $SSH_OPTS "$SCRIPT_DIR/.env" "$DEPLOY_HOST:$DEPLOY_PATH/.env"
else
  warn "deploy/.env 不存在，使用 docker-compose 默认值（仅适合演示）"
  scp -P "$SSH_PORT" $SSH_OPTS "$SCRIPT_DIR/.env.example" "$DEPLOY_HOST:$DEPLOY_PATH/.env"
fi

# db migrations + seeds（首次 init 用）
rsync -az -e "ssh -p $SSH_PORT $SSH_OPTS" \
  --delete \
  "$REPO_ROOT/db/migrations" "$REPO_ROOT/db/seeds" \
  "$DEPLOY_HOST:$DEPLOY_PATH/db/"
ok "上传完成"

# ─── 5. 远端 load + up ───
step "5/5 远端 docker load + compose up"
ssh -p "$SSH_PORT" $SSH_OPTS "$DEPLOY_HOST" bash <<EOF
set -euo pipefail
cd '$DEPLOY_PATH'
echo "→ 加载镜像"
docker load < images.tar.gz
rm -f images.tar.gz
echo "→ 停旧容器（如果有）"
docker compose down 2>/dev/null || true
echo "→ 启动"
docker compose up -d
echo "→ 等待 backend healthy（最多 90s）"
for i in \$(seq 1 18); do
  if docker compose exec -T backend wget -q -O- http://localhost:8080/actuator/health 2>/dev/null | grep -q UP; then
    echo "✓ backend healthy"
    break
  fi
  sleep 5
done
echo "→ 容器状态"
docker compose ps
EOF
ok "部署完成 — 访问 http://${DEPLOY_HOST#*@}/"
