#!/usr/bin/env bash
# deploy/deploy-native.sh — 原生（非 Docker）一键部署
#
# 对应生产实际拓扑：
#   - 后端：systemd service xqt-backend，jar 在 /opt/xqt-saas/backend/xqt-backend.jar
#   - 前端：nginx 直接 serve /opt/xqt-saas/web/（不是 web/dist/）
#       nginx 配置在 /www/server/panel/vhost/nginx/xqt-saas.conf
#       root /opt/xqt-saas/web; index index.html;
#   - DB：postgres 在 127.0.0.1:15432，不动
#
# 历史踩坑（这个脚本就是为了避免再踩）：
#   ✗ jar 传到 /opt/xqt/backend/        → systemd 路径是 /opt/xqt-saas/backend/，白部署
#   ✗ web 传到 /opt/xqt-saas/web/dist/  → nginx root 是 /opt/xqt-saas/web/，白部署
#
# 用法（自动检测要部署什么）：
#   ./deploy/deploy-native.sh              # 部署 backend + web 全套
#   ./deploy/deploy-native.sh backend      # 只部署 backend
#   ./deploy/deploy-native.sh web          # 只部署 web
#   ./deploy/deploy-native.sh skip-build   # 跳过 build 直接传现有 artifact
#
# 凭据：通过环境变量或 deploy/.deploy.env
#   DEPLOY_HOST=root@8.148.227.76
#   DEPLOY_PASSWORD=...            # 可选；不设则用 SSH key
#
# 依赖：本地 mvnw / npm / scp / ssh（用 sshpass 处理密码）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# ─── 配置 ───
if [[ -f "$SCRIPT_DIR/.deploy.env" ]]; then
  # shellcheck source=/dev/null
  source "$SCRIPT_DIR/.deploy.env"
fi

DEPLOY_HOST="${DEPLOY_HOST:-root@8.148.227.76}"
SSH_PORT="${SSH_PORT:-22}"
SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=10"

# 远端路径（重要：这是 nginx + systemd 实际路径，别改）
REMOTE_BACKEND_JAR="/opt/xqt-saas/backend/xqt-backend.jar"
REMOTE_WEB_ROOT="/opt/xqt-saas/web"
REMOTE_HEALTH_URL="http://127.0.0.1:18103/actuator/health"

# 本地产物
LOCAL_JAR="$REPO_ROOT/apps/backend/target/xqt-backend-0.0.1-SNAPSHOT.jar"
LOCAL_WEB_DIST="$REPO_ROOT/apps/web/dist"

# ─── 显示 ───
step() { echo -e "\n\033[1;34m▶ $*\033[0m"; }
ok()   { echo -e "\033[1;32m✓ $*\033[0m"; }
warn() { echo -e "\033[1;33m! $*\033[0m"; }
fail() { echo -e "\033[1;31m✗ $*\033[0m" >&2; exit 1; }

# ─── SSH / SCP 包装 ───
if [[ -n "${DEPLOY_PASSWORD:-}" ]]; then
  command -v sshpass >/dev/null 2>&1 \
    || fail "DEPLOY_PASSWORD 设了但没装 sshpass: brew install sshpass"
  REMOTE_SSH=(sshpass -p "$DEPLOY_PASSWORD" ssh -p "$SSH_PORT" $SSH_OPTS)
  REMOTE_SCP=(sshpass -p "$DEPLOY_PASSWORD" scp -P "$SSH_PORT" $SSH_OPTS)
else
  REMOTE_SSH=(ssh -p "$SSH_PORT" $SSH_OPTS)
  REMOTE_SCP=(scp -P "$SSH_PORT" $SSH_OPTS)
fi

remote_run() { "${REMOTE_SSH[@]}" "$DEPLOY_HOST" "$@"; }
remote_cp()  { "${REMOTE_SCP[@]}" "$@" "$DEPLOY_HOST:${@: -1}"; }  # 简化用法

# 解析模式
MODE="${1:-all}"   # all | backend | web | skip-build
BUILD_BACKEND=true
BUILD_WEB=true
PUSH_BACKEND=true
PUSH_WEB=true
case "$MODE" in
  all)        ;;
  backend)    PUSH_WEB=false; BUILD_WEB=false ;;
  web)        PUSH_BACKEND=false; BUILD_BACKEND=false ;;
  skip-build) BUILD_BACKEND=false; BUILD_WEB=false ;;
  *) fail "未知模式: $MODE （支持 all|backend|web|skip-build）" ;;
esac

step "目标: $DEPLOY_HOST  模式: $MODE"

# ─── 1. 本地构建 ───
if $BUILD_BACKEND; then
  step "1a/_  构建 backend (mvnw package -DskipTests)"
  cd "$REPO_ROOT/apps/backend"
  ./mvnw -q -DskipTests package
  [[ -f "$LOCAL_JAR" ]] || fail "未生成 $LOCAL_JAR"
  ok "jar 大小: $(du -h "$LOCAL_JAR" | cut -f1)"
fi

if $BUILD_WEB; then
  step "1b/_  构建 web (npm run build)"
  cd "$REPO_ROOT/apps/web"
  npm run build
  [[ -f "$LOCAL_WEB_DIST/index.html" ]] || fail "未生成 $LOCAL_WEB_DIST/index.html"
  ok "新 bundle: $(grep -oE 'index-[A-Za-z0-9_-]+\.js' "$LOCAL_WEB_DIST/index.html" | head -1)"
fi

# ─── 2. 推送 backend ───
if $PUSH_BACKEND; then
  step "2a/_  上传 jar → $REMOTE_BACKEND_JAR"
  "${REMOTE_SSH[@]}" "$DEPLOY_HOST" "mkdir -p $(dirname "$REMOTE_BACKEND_JAR")"
  "${REMOTE_SCP[@]}" "$LOCAL_JAR" "$DEPLOY_HOST:$REMOTE_BACKEND_JAR"
  ok "jar 已上传"

  step "2b/_  重启 xqt-backend 并等待 health UP"
  "${REMOTE_SSH[@]}" "$DEPLOY_HOST" bash <<EOF
set -e
systemctl restart xqt-backend
for i in \$(seq 1 60); do
  if curl -s "$REMOTE_HEALTH_URL" 2>/dev/null | grep -q UP; then
    echo "  ✓ health UP（用时 \${i}s）"
    exit 0
  fi
  sleep 1
done
echo "  ✗ 60s 内未 healthy" >&2
systemctl status xqt-backend --no-pager | tail -20 >&2
exit 1
EOF
  ok "backend 健康"
fi

# ─── 3. 推送 web ───
if $PUSH_WEB; then
  step "3a/_  上传 index.html → $REMOTE_WEB_ROOT/index.html"
  "${REMOTE_SSH[@]}" "$DEPLOY_HOST" "mkdir -p $REMOTE_WEB_ROOT/assets"
  "${REMOTE_SCP[@]}" "$LOCAL_WEB_DIST/index.html" "$DEPLOY_HOST:$REMOTE_WEB_ROOT/index.html"

  step "3b/_  同步 assets/ → $REMOTE_WEB_ROOT/assets/"
  # scp -r 把所有 hashed bundle 都传过去；旧的 hashed 文件会沉淀但不影响新版本
  # 保留旧 hashed 文件是 Vite 安全策略（防止已加载的 HTML 引用 404）
  "${REMOTE_SCP[@]}" -r "$LOCAL_WEB_DIST/assets/." "$DEPLOY_HOST:$REMOTE_WEB_ROOT/assets/"

  step "3c/_  清理 14 天前的 hashed bundles（避免无限堆积）"
  "${REMOTE_SSH[@]}" "$DEPLOY_HOST" \
    "find $REMOTE_WEB_ROOT/assets/ -type f -mtime +14 -delete 2>/dev/null || true"

  step "3d/_  公网回放验证"
  PUBLIC_HOST="${DEPLOY_HOST#*@}"
  ACTUAL=$(curl -s "http://$PUBLIC_HOST/?_=$(date +%s)" | grep -oE 'index-[A-Za-z0-9_-]+\.js' | head -1)
  EXPECTED=$(grep -oE 'index-[A-Za-z0-9_-]+\.js' "$LOCAL_WEB_DIST/index.html" | head -1)
  if [[ "$ACTUAL" == "$EXPECTED" ]]; then
    ok "公网拿到的就是新 bundle: $ACTUAL"
  else
    warn "公网仍返回 $ACTUAL（期望 $EXPECTED）— 可能 nginx 缓存或 CDN"
  fi
fi

echo
ok "部署完成 — http://${DEPLOY_HOST#*@}/"
echo "  浏览器强刷：⌘+Shift+R（Chrome/Edge）或无痕窗口"
