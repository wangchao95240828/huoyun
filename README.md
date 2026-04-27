# 新启天货代物流 SaaS

面向货代物流业务的多租户 SaaS 脚手架，当前重点落地财务模块：费率引擎、应收账单、成本导入、自动对账、保险、利润与提成。

## 目录

- `apps/api`: 财务域 API 服务脚手架。
- `apps/web`: 运营后台前端脚手架。
- `packages/shared`: 前后端共享枚举与类型。
- `db/migrations`: PostgreSQL 数据库迁移。
- `db/seeds`: 基础字典与样例数据。
- `infra`: 本地开发基础设施。
- `docs`: 系统架构与项目分工文档。

## 本地启动

### 环境要求

- Node.js 22+
- npm 11+
- Docker Desktop / Docker Compose

### 1. 启动数据库和缓存

```bash
cd xqt-saas
cp .env.example .env
docker compose -f infra/docker-compose.yml up -d
```

`docker compose` 会启动：

- PostgreSQL: `localhost:15432`
- Redis: `localhost:6379`

PostgreSQL 会自动执行 `db/migrations` 和 `db/seeds` 下的 SQL。使用 `15432` 是为了避免和本机已有 PostgreSQL 的 `5432` 冲突。

### 2. 安装依赖

```bash
npm install
```

如果本机 `~/.npm` 缓存权限异常，可以改用临时缓存：

```bash
npm install --cache /tmp/xqt-npm-cache
```

### 3. 编译验证

```bash
npm run build
```

### 4. 启动开发服务

```bash
npm run dev
```

默认端口：

- API: `http://localhost:8080`
- Web: `http://localhost:5173`

### 5. API 快速验证

```bash
curl http://localhost:8080/health
curl -H 'x-tenant-code: xqt' http://localhost:8080/api/finance/overview
```

如 `8080` 被占用，可以临时指定：

```bash
API_PORT=18080 npm run dev -w apps/api
```

## 常用命令

```bash
# 查看容器状态
docker compose -f infra/docker-compose.yml ps

# 停止容器但保留数据
docker compose -f infra/docker-compose.yml down

# 删除本地开发数据库卷并重新初始化
docker compose -f infra/docker-compose.yml down -v
docker compose -f infra/docker-compose.yml up -d
```

## 设计原则

- 多租户从 Day 1 设计，所有业务表带 `tenant_id`。
- PostgreSQL RLS 从数据库层强制租户隔离，API 请求内设置 `app.current_tenant_id`。
- 财务闭环以“一票货”为利润核算最小单元，保留箱级、子单级和账单原始行追溯。
- 费率规则版本化，费用行保存规则快照，避免月底对账时无法复盘。
- `charges` 负责业务计费，`ledger_*` 负责正式不可变复式过账。
- 对账从“自动比对 + 异常人工复核”出发，不追求黑盒自动入账。
