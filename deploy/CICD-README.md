# xqt-saas CI/CD 策略

## 一句话总结

> 任何 `push` 到允许的分支 → GitHub Actions 自动触发 → **在 GitHub 托管 Ubuntu Runner 上编译** → SSH+rsync 推到服务器 → 跑 migration → 远程 systemctl restart → 健康检查 → 失败自动回滚。

## 为什么不用自托管 Runner

试过了，CentOS 7 EOL 走死路：

| 矛盾 | 详情 |
|---|---|
| Runner v2.319.1 | GitHub 服务器拒绝："deprecated and cannot receive messages" |
| Runner v2.334.0 | 需要 `GLIBCXX_3.4.20/21`，CentOS 7 自带 libstdc++ 只到 3.4.19 |
| CentOS 8 libstdc++ | 反过来需要 glibc 2.18，CentOS 7 只有 2.17 |
| SCL devtoolset-11 | Red Hat 设计上 SCL 不提供 runtime libstdc++.so.6（只有 .a + 头文件，鼓励静态链接） |

**长期：** 升级 OS（AlmaLinux 9 或 Ubuntu 22）。  
**短期：** 用 GitHub 托管 Runner + SSH 部署，绕开服务器 OS 限制。

## 架构图

```
       开发同事            GitHub                                生产服务器 8.148.227.76
       ─────────          ──────                                ────────────────────────
                                                                ┌─────────────────────┐
       git push  ──────► GitHub repo                            │ xqt-backend.service │
                              │                                  │ (Spring Boot jar)   │
                              │ webhook                          └────────▲────────────┘
                              ▼                                           │
                         ┌─────────────────────┐                          │
                         │ GitHub Runner       │                          │
                         │ (ubuntu-latest)     │                          │
                         │                     │                          │
                         │ checkout            │  SSH (ed25519)           │
                         │ mvn package         │  rsync jar     ──────────┤
                         │ npm build           │  rsync dist    ──────────┤
                         │ ssh psql migration  │  ssh systemctl restart ──┘
                         │ ssh health check    │
                         └─────────────────────┘
```

## 两个 Workflow

| 文件 | 触发 | 在哪跑 | 作用 |
|---|---|---|---|
| `.github/workflows/ci.yml` | 任何 push / PR | GitHub 托管 Ubuntu | 编译 + 测试，防挂 |
| `.github/workflows/cd.yml` | push 到 `feat/acc-full-migration-2026-05-26` 或 `main` | GitHub 托管 Ubuntu | 编译 + SSH 部署 + 远程重启 |

## 第一次设置（5 分钟）

只要 2 步：在 GitHub 仓库 Settings 加 2 个 secret。

### Step 1：加 `SSH_DEPLOY_KEY` secret

去 `Settings → Secrets and variables → Actions → New repository secret`：

- **Name**: `SSH_DEPLOY_KEY`
- **Secret**: 粘贴 ed25519 私钥（完整内容，包括 `-----BEGIN/END-----` 两行）

私钥已生成在本地 `/tmp/xqt-deploy-key/id_ed25519`，对应公钥已贴在服务器 `/root/.ssh/authorized_keys`。

```bash
# 本地查看私钥内容（用来粘贴到 GitHub）
cat /tmp/xqt-deploy-key/id_ed25519
```

### Step 2：加 `DB_PASSWORD` secret

- **Name**: `DB_PASSWORD`
- **Secret**: `Xqt_prod_DB_2026_change_me`（生产 PostgreSQL 密码）

### Step 3：触发首次部署

```bash
# 本地随便改个文件 + push（或直接在 GitHub UI 点 Run workflow）
echo "// touched at $(date)" >> apps/backend/README.md
git add -A && git commit -m "test: trigger first CD"
git push origin feat/acc-full-migration-2026-05-26
```

去 GitHub `Actions` 看 workflow 跑起来。

## 日常使用

| 场景 | 怎么做 |
|---|---|
| 我提交了一个 commit 想自动部署 | `git push` 到允许的分支，**就完了** |
| 我想手动触发部署（不 push 代码）| GitHub Actions UI → CD workflow → Run workflow |
| 我想跳过测试（紧急 hotfix） | Run workflow 时勾选 `skip_tests` |
| 我想跳过 migration | Run workflow 时勾选 `skip_migration` |
| 线上挂了想紧急回滚 | SSH 上服务器，跑 `/opt/xqt-saas/deploy/cicd-rollback.sh` |
| 看部署日志 | GitHub Actions UI 看 |

## DB Migration 策略

每次 CD：
1. rsync `db/migrations/` 目录到服务器 `/tmp/cd-migrations/`
2. SSH 上去远程跑 psql
3. 查 `schema_migrations` 追踪表：
   ```sql
   CREATE TABLE schema_migrations (
     filename text PRIMARY KEY,
     applied_at timestamptz NOT NULL DEFAULT now()
   );
   ```
4. 没跑过的逐个 `psql -v ON_ERROR_STOP=1 -f`，跑完写入追踪表
5. 清掉 `/tmp/cd-migrations/`

**约定**：新 migration 文件名格式 `NNN_description.sql`，N 递增。

## 安全设计

| 项 | 怎么做 |
|---|---|
| 私钥不进仓库 | 存 GitHub Secret `SSH_DEPLOY_KEY`，CI 临时写到 `~/.ssh/` |
| DB 密码不进仓库 | 存 GitHub Secret `DB_PASSWORD` |
| Host key 校验 | `ssh-keyscan -H` 写入 known_hosts |
| 失败自动回滚 | 健康检查 60s 不通 → 自动 cp .bak 回去 → 重启 |
| 串行部署 | `concurrency: cd-production` 同时只能 1 个 |
| SSH 用 root | 临时方案；未来可加 `deploy` 用户 + 受限 sudo |

## 配置文件清单

```
.github/workflows/
├── ci.yml                  CI: 编译 + 测试（每次 push）
└── cd.yml                  CD: GitHub-hosted runner + SSH 部署

deploy/
├── cicd-rollback.sh        紧急：手动回滚 jar + web
├── deploy-native.sh        旧的手动部署脚本（仍可用，scp 方式）
├── deploy.sh               旧的 docker 部署脚本（弃用）
└── CICD-README.md          本文件
```

## 关键路径

| 路径 | 说明 |
|---|---|
| 服务器 jar | `/opt/xqt-saas/backend/xqt-backend.jar` |
| 服务器 jar 备份 | `/opt/xqt-saas/backend/xqt-backend.jar.bak`（用于回滚） |
| 服务器 web | `/opt/xqt-saas/web/`（**不是** `web/dist/`，nginx 直读这里） |
| 服务器 web 备份 | `/opt/xqt-saas/web.bak/` |
| 健康检查 | `http://127.0.0.1:18103/actuator/health` |
| systemd 服务 | `xqt-backend.service` |
| 公钥位置 | `/root/.ssh/authorized_keys`（标记 `github-actions-deploy@xqt-saas`） |

## 故障排查

### Workflow 失败 / SSH 不通

- 看 GitHub Actions UI 的 "Setup SSH" 步骤
- 确认 `SSH_DEPLOY_KEY` secret 完整（包括 `-----BEGIN/END-----` 两行）
- SSH 公钥没在服务器：本地 `cat /tmp/xqt-deploy-key/id_ed25519.pub` 然后手动加到 `/root/.ssh/authorized_keys`

### Migration 失败

- 看 GitHub Actions UI 的 "Run DB migrations" 步骤的 psql 输出
- 也可以 SSH 上服务器手动跑：`PGPASSWORD=... psql -h 127.0.0.1 -p 15432 -U xqt -d xqt_saas -f /tmp/cd-migrations/XXX.sql`

### 部署成功但页面没更新

- 浏览器缓存 → 强刷 `⌘+Shift+R`
- nginx 缓存 → SSH 上 `sudo nginx -s reload`
- 公网验证有 5 次重试 + cache_bust，理论上不会假成功

### 健康检查超时

- 大概率新 jar 启动失败
- 自动回滚步骤会把 `.bak` 切回去并重启
- SSH 上看 `journalctl -u xqt-backend -n 100`

### rsync/scp 慢

- GitHub Runner 在美国/欧洲，到中国阿里云带宽不稳定
- 通常 80MB jar ~30-60s 可以接受
- 如果超过 5 分钟，考虑：未来自建 China-based runner 或迁 OS

### 紧急手动回滚

```bash
ssh root@8.148.227.76
/opt/xqt-saas/deploy/cicd-rollback.sh
```

## 未来扩展（按需）

| 阶段 | 加什么 |
|---|---|
| 阶段 1 | 飞书/钉钉/企业微信 webhook 通知 |
| 阶段 2 | 独立 `deploy` 用户 + 受限 sudo（替代 root SSH） |
| 阶段 3 | 多环境（staging + production 两套 secret/host） |
| 阶段 4 | **OS 升级到 AlmaLinux 9** → 切回自托管 runner（建议优先） |
| 阶段 5 | 蓝绿部署（zero-downtime） |

---

**当前文档版本**：v2.0 / 2026-06-10
**部署方式**：GitHub-hosted runner + SSH/rsync 推送
**部署目标**：8.148.227.76 (中国境内 Aliyun, CentOS 7)
