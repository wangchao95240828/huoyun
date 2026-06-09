# xqt-saas CI/CD 策略

## 一句话总结

> 任何 `push` 到允许的分支 → GitHub Actions 自动触发 → 在服务器上自托管的 Runner 拉取代码 → 编译 → 跑 migration → 部署 → 健康检查 → 失败自动回滚。

## 架构图

```
       开发同事            GitHub                     生产服务器 8.148.227.76
       ─────────          ──────                     ────────────────────────
                                                     ┌─────────────────────┐
       git push  ──────► GitHub repo                 │ xqt-runner.service  │
                              │                       │ (自托管 Runner)     │
                              │ webhook              │                     │
                              └─────────────────────►│ pull code           │
                                                     │ mvn package         │
                                                     │ npm build           │
                                                     │ psql -f migration   │
                                                     │ cp jar → restart    │
                                                     │ cp dist → nginx     │
                                                     │ health check ✓      │
                                                     └─────────────────────┘
```

## 两个 Workflow

| 文件 | 触发 | 在哪跑 | 作用 |
|---|---|---|---|
| `.github/workflows/ci.yml` | 任何 push / PR | GitHub 托管 Ubuntu | 编译 + 测试，防挂 |
| `.github/workflows/cd.yml` | push 到 `feat/acc-full-migration-2026-05-26` 或 `main` | 自托管 Runner（服务器上） | 真实部署 |

## 第一次设置（10 分钟）

### Step 1：拿 GitHub Runner Token

去仓库 `Settings → Actions → Runners → New self-hosted runner`，选 Linux/x64。
GitHub 会显示一段：

```bash
./config.sh --url https://github.com/wangchao95240828/huoyun --token AABBCC...
```

**复制 `--token` 后面那串值**（这个 token 1 小时有效，过期就再生成一次）。

### Step 2：在服务器上跑安装脚本

```bash
# SSH 上服务器
ssh root@8.148.227.76

# 把 install-runner.sh 拉过去
cd /opt
git clone https://github.com/wangchao95240828/huoyun.git xqt-saas-tmp
cd xqt-saas-tmp
chmod +x deploy/install-runner.sh

# 跑安装（传 token）
./deploy/install-runner.sh AABBCC...

# 删临时仓库（runner 自己有工作区）
cd /opt && rm -rf xqt-saas-tmp
```

脚本会自动：
- 装 Java 17 + Node 20 + Maven
- 创建 `xqt-runner` 用户 + 受限 sudo 权限
- 下载 GitHub Runner v2.319.1
- 注册名为 `xqt-prod`、标签 `self-hosted,xqt-prod,linux`
- 装成 systemd 服务，开机自启
- 启动 + 验证

### Step 3：验证 Runner 上线

GitHub 仓库 `Settings → Actions → Runners`，应该看到：

```
✅ xqt-prod   Idle   labels: self-hosted, xqt-prod, linux
```

### Step 4：触发首次部署

```bash
# 本地随便改个文件 + push（或直接在 GitHub UI 点 workflow_dispatch）
echo "// touched at $(date)" >> apps/backend/README.md
git add -A && git commit -m "test: trigger first CD"
git push origin feat/acc-full-migration-2026-05-26
```

然后去 GitHub `Actions` 看到 workflow 运行起来。

## 日常使用

| 场景 | 怎么做 |
|---|---|
| 我提交了一个 commit 想自动部署 | `git push` 到允许的分支，**就完了** |
| 我想手动触发部署（不 push 代码）| GitHub Actions UI → CD workflow → Run workflow |
| 我想跳过测试（紧急 hotfix） | Run workflow 时勾选 `skip_tests` |
| 我想跳过 migration | Run workflow 时勾选 `skip_migration` |
| 线上挂了想紧急回滚 | SSH 上服务器，跑 `/opt/xqt-saas/deploy/cicd-rollback.sh` |
| 看部署日志 | GitHub Actions UI 看；或服务器上 `journalctl -u xqt-runner -f` |

## DB Migration 策略

每次 CD 跑 migration 前会查 `schema_migrations` 追踪表：

```sql
CREATE TABLE schema_migrations (
  filename text PRIMARY KEY,
  applied_at timestamptz NOT NULL DEFAULT now()
);
```

- 没跑过的：从 `db/migrations/` 里按文件名顺序跑
- 跑过的：跳过
- 跑失败：`ON_ERROR_STOP=1` 中断，CD workflow 失败

**约定**：新 migration 文件名格式 `NNN_description.sql`，N 递增。

## 安全设计

| 项 | 怎么做 |
|---|---|
| Runner 不能 root | 用 `xqt-runner` 用户，受限 sudo 白名单 |
| Runner 只能 restart 自己 | sudoers 只允许 `systemctl restart xqt-backend` |
| Runner 不能动其他服务 | 不能 restart nginx / postgres 等 |
| 失败自动回滚 | 健康检查 60s 内挂掉，自动 cp .bak 回去 |
| 串行部署 | `concurrency: cd-production` 同时只能 1 个 |
| Token 保护 | 注册 Token 1 小时失效；GitHub Secrets 不必加 |

## 配置文件清单

```
.github/workflows/
├── ci.yml                  CI: 编译 + 测试（每次 push）
└── cd.yml                  CD: 部署（push 到生产分支）

deploy/
├── install-runner.sh       一次性：装 GitHub Runner 到服务器
├── cicd-rollback.sh        紧急：手动回滚
├── deploy-native.sh        旧的手动部署脚本（仍可用）
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
| systemd 服务 | `xqt-backend.service` + `xqt-runner.service` |
| Runner 工作区 | `/opt/xqt-runner/_work/huoyun/huoyun/` |

## 监控建议（可选）

| 类型 | 工具 | 用途 |
|---|---|---|
| 部署通知 | 飞书/钉钉/企业微信 webhook | CD 成功/失败推到群 |
| 错误监控 | Sentry / Aliyun ARMS | 后端异常实时告警 |
| 性能监控 | Prometheus + Grafana | API 延迟 + JVM 内存 |
| 日志聚合 | Loki + Grafana | 集中查 backend / runner / nginx 日志 |

## 故障排查

### Runner 显示 offline

```bash
ssh root@8.148.227.76
systemctl status xqt-runner
journalctl -u xqt-runner -n 50
```

常见原因：
- Token 过期 → 重新注册 `./config.sh remove && ./config.sh --token NEW`
- 网络问题 → check `curl https://github.com`
- 磁盘满 → check `df -h`

### 部署成功但页面没更新

- 浏览器缓存 → 强刷 `⌘+Shift+R`
- nginx 缓存 → `sudo nginx -s reload`
- CDN 缓存 → 等 5 分钟或刷新

### 部署失败但服务还活着

- 检查 GitHub Actions UI 上的失败 step
- 多数情况是 migration 失败 → 看 psql 报错信息
- 健康检查超时 → SSH 上看 `journalctl -u xqt-backend -n 100`

### 回滚后还有问题

```bash
# 看当前服务版本
md5sum /opt/xqt-saas/backend/xqt-backend.jar

# 用很老的部署脚本（手动）从本地推
./deploy/deploy-native.sh skip-build  # 用本地现成的 target/*.jar 重新传
```

## 未来扩展（按需）

| 阶段 | 加什么 |
|---|---|
| 阶段 1 | Slack/飞书 webhook 通知 |
| 阶段 2 | 多环境（staging + production 两套 runner）|
| 阶段 3 | 蓝绿部署（zero-downtime）|
| 阶段 4 | Helm + K8s（如果搬上云）|

---

**当前文档版本**：v1.0 / 2026-06-06
**Runner 版本**：GitHub Actions Runner v2.319.1
**部署目标**：8.148.227.76 (中国境内 Aliyun)
