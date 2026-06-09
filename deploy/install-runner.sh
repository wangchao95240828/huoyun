#!/usr/bin/env bash
# deploy/install-runner.sh — 在服务器上一次性安装 GitHub Actions 自托管 Runner
#
# 用法：
#   1. 去 GitHub 仓库 → Settings → Actions → Runners → New self-hosted runner
#      复制弹出来的 ./config.sh --token XXX 那一行的 token 备用
#   2. SSH 上服务器：ssh root@8.148.227.76
#   3. wget -O install-runner.sh https://raw.githubusercontent.com/.../install-runner.sh
#      chmod +x install-runner.sh
#      ./install-runner.sh <GITHUB_TOKEN_FROM_STEP_1>
#
# 这个脚本会：
#   - 装 GitHub Runner 到 /opt/xqt-runner/
#   - 配置 runner 名 xqt-prod，labels: self-hosted,xqt-prod
#   - 注册成 systemd 服务（开机自启 + 失败自重启）
#   - 给 runner 用户 sudo 权限（用于 systemctl restart xqt-backend）

set -euo pipefail

TOKEN="${1:?用法: $0 <GITHUB_REGISTRATION_TOKEN>}"
REPO_URL="${REPO_URL:-https://github.com/wangchao95240828/huoyun}"
RUNNER_DIR="${RUNNER_DIR:-/opt/xqt-runner}"
RUNNER_VERSION="${RUNNER_VERSION:-2.319.1}"
RUNNER_USER="${RUNNER_USER:-xqt-runner}"

step() { echo -e "\n\033[1;34m▶ $*\033[0m"; }
ok()   { echo -e "\033[1;32m✓ $*\033[0m"; }

step "1/7 创建 runner 用户 $RUNNER_USER"
if ! id "$RUNNER_USER" >/dev/null 2>&1; then
  useradd -m -s /bin/bash "$RUNNER_USER"
  ok "user created"
else
  ok "user already exists"
fi

step "2/7 给 runner 用户 sudo 权限（仅 systemctl + psql + cp 受限命令）"
cat > /etc/sudoers.d/xqt-runner <<EOF
# xqt-runner 部署受控命令白名单
$RUNNER_USER ALL=(root) NOPASSWD: /bin/systemctl restart xqt-backend
$RUNNER_USER ALL=(root) NOPASSWD: /bin/systemctl status xqt-backend
$RUNNER_USER ALL=(root) NOPASSWD: /bin/cp /opt/xqt-saas/backend/xqt-backend.jar*
$RUNNER_USER ALL=(root) NOPASSWD: /bin/cp /opt/xqt-saas/web/*
$RUNNER_USER ALL=(root) NOPASSWD: /bin/cp -r /opt/xqt-saas/web/*
$RUNNER_USER ALL=(root) NOPASSWD: /bin/rm -rf /opt/xqt-saas/web.bak
$RUNNER_USER ALL=(root) NOPASSWD: /usr/bin/find /opt/xqt-saas/web/assets/*
EOF
chmod 0440 /etc/sudoers.d/xqt-runner
ok "sudoers configured"

step "3/7 下载 GitHub Runner v$RUNNER_VERSION"
mkdir -p "$RUNNER_DIR"
chown "$RUNNER_USER:$RUNNER_USER" "$RUNNER_DIR"
cd "$RUNNER_DIR"
if [ ! -f run.sh ]; then
  # x86_64 / linux
  TARBALL="actions-runner-linux-x64-${RUNNER_VERSION}.tar.gz"
  sudo -u "$RUNNER_USER" curl -fL \
    "https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/${TARBALL}" \
    -o /tmp/$TARBALL
  sudo -u "$RUNNER_USER" tar xzf /tmp/$TARBALL -C "$RUNNER_DIR"
  rm -f /tmp/$TARBALL
  ok "runner extracted to $RUNNER_DIR"
else
  ok "runner already downloaded"
fi

step "4/7 安装依赖（Java 17 + Node 20 + Maven）"
if ! command -v java >/dev/null 2>&1 || ! java -version 2>&1 | grep -q "17\."; then
  # CentOS / RHEL
  yum install -y java-17-openjdk-devel || \
  # Ubuntu / Debian
  apt-get update && apt-get install -y openjdk-17-jdk
fi
if ! command -v node >/dev/null 2>&1 || [ "$(node -v | cut -c2-3)" -lt 20 ]; then
  curl -fsSL https://rpm.nodesource.com/setup_20.x | bash - 2>/dev/null || \
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  yum install -y nodejs || apt-get install -y nodejs
fi
ok "java=$(java -version 2>&1 | head -1) | node=$(node -v)"

step "5/7 注册 runner 到 GitHub"
cd "$RUNNER_DIR"
sudo -u "$RUNNER_USER" ./config.sh \
  --url "$REPO_URL" \
  --token "$TOKEN" \
  --name xqt-prod \
  --labels self-hosted,xqt-prod,linux \
  --work _work \
  --replace \
  --unattended
ok "runner registered"

step "6/7 装 systemd 服务"
cat > /etc/systemd/system/xqt-runner.service <<EOF
[Unit]
Description=GitHub Actions Runner (xqt-prod)
After=network-online.target

[Service]
ExecStart=$RUNNER_DIR/run.sh
User=$RUNNER_USER
WorkingDirectory=$RUNNER_DIR
Restart=always
RestartSec=10
KillMode=process

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable xqt-runner.service
systemctl start xqt-runner.service
ok "service started"

step "7/7 验证"
sleep 3
systemctl status xqt-runner.service --no-pager | head -10
echo
echo "✓ Runner 安装完成。"
echo "  ✓ 现在去 GitHub 仓库 Settings → Actions → Runners 应该能看到 'xqt-prod' online"
echo "  ✓ 任何对 feat/acc-full-migration-2026-05-26 或 main 的 push 会自动触发 CD workflow"
echo "  ✓ 查看运行日志: journalctl -u xqt-runner -f"
