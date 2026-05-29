# deploy/ — 生产部署产物

## 目录

```
deploy/
├── README.md                  本文件
├── docker-compose.prod.yml    生产 compose（含 47 migrations + backend + web + redis）
├── .env.example               环境变量模板
└── deploy.sh                  一键部署脚本
```

应用 Dockerfile 位于：
- `apps/backend/Dockerfile`：Spring Boot multi-stage（maven build → JRE 17）
- `apps/web/Dockerfile`：Vue + Vite → nginx serve
- `apps/web/nginx.conf`：SPA fallback + 反代 backend

## 与 infra/docker-compose.yml 的区别

| 文件 | 用途 | migrations | 应用 |
|------|------|-----------|------|
| `infra/docker-compose.yml` | 本地 dev（仅 PG + redis） | 1-25 | 用 `mvn spring-boot:run` |
| `deploy/docker-compose.prod.yml` | 生产/演示部署 | 1-47 + seeds | 镜像 |

任务书 §7 锁定 `infra/docker-compose.yml` 为运维 runbook 范围，所以 deploy
独立路径不动 dev compose。

## 部署流程

### 准备

服务器需要：
- Docker + Docker Compose v2
- 端口 80 开放（web）

本地需要：
- Docker（含 buildx）
- ssh + scp + rsync + tar

### 配置

```bash
cp deploy/.env.example deploy/.env
vim deploy/.env   # 改 POSTGRES_PASSWORD 等

# 配置部署目标
cat > deploy/.deploy.env <<EOF
DEPLOY_HOST=root@8.148.227.76
DEPLOY_PATH=/opt/xqt-saas
SSH_PORT=22
EOF
```

### 部署

```bash
./deploy/deploy.sh
```

脚本 5 步：
1. 本地构建 backend 镜像（multi-stage：maven → JRE）
2. 本地构建 web 镜像（vite → nginx）
3. `docker save | gzip` 打包
4. scp 镜像 + compose + db/ 到服务器
5. 远端 `docker load + docker compose up -d`，等待 backend healthy

### 验收

```bash
# 远端
ssh root@8.148.227.76
cd /opt/xqt-saas
docker compose ps              # 4 个 service 全 healthy
docker compose logs backend    # 查启动日志
curl http://localhost:8080/actuator/health   # 应返回 {"status":"UP"}

# 外部
curl http://8.148.227.76/health
open http://8.148.227.76/
```

## 生产 strict 开关（已默认开启）

- `RATES_STRICT_QUOTE=true`：报价失败不退化为 dev 估算
- `APP_CARRIER_STRICT_GATEWAY=true`：carrier 路由严格走数据库配置

在 `.env` 里覆盖：

```bash
RATES_STRICT_QUOTE=false   # 仅 dev/demo 临时降级
```

## 升级 / 重新部署

源码改动后再次跑：

```bash
./deploy/deploy.sh
```

脚本会重建镜像、上传、替换容器。**数据库不会被清空**（postgres volume 持久化）。

## 回滚

服务器上保留前一版镜像：

```bash
ssh root@8.148.227.76 'cd /opt/xqt-saas && docker images | grep xqt-'
# 选定 tag
ssh root@8.148.227.76 'cd /opt/xqt-saas && \
  docker compose down && \
  docker tag xqt-backend:previous-tag xqt-backend:prod && \
  docker compose up -d'
```

（脚本目前每次覆盖 `:prod` tag。生产建议改为日期 tag，本任务范围不强求。）

## 数据库迁移

新 migration 加到 `db/migrations/`，编号递增。重新部署：

```bash
./deploy/deploy.sh
```

**注意**：postgres `/docker-entrypoint-initdb.d/` 只在**首次启动**有效（空数据库时）。
首次以后新 migration 需手动应用：

```bash
ssh root@8.148.227.76 'cd /opt/xqt-saas && \
  docker compose exec -T postgres psql -U xqt -d xqt_saas < db/migrations/048_xxx.sql'
```

## 安全

- 部署前修改 `.env` 中 `POSTGRES_PASSWORD`
- 服务器密码（root@8.148.227.76）部署成功后**立即轮换**
- 生产环境用密钥登录而非密码，禁 root SSH
- `.env` 不要提交 git；`.deploy.env` 也不要

## 故障排查

| 现象 | 排查 |
|------|------|
| `backend not healthy` | `docker compose logs backend` 看堆栈 |
| `connection refused to postgres` | postgres 健康检查未通过；看 `docker compose logs postgres` |
| `404 from /api/*` | nginx 反代未生效；看 `docker compose exec web cat /etc/nginx/conf.d/default.conf` |
| 数据库 schema 不匹配 | postgres volume 已有旧数据，migrations 没跑；按"数据库迁移"段手动应用 |
