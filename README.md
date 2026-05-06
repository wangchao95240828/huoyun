# 新启天货代物流 SaaS

面向货代物流业务的多租户 SaaS 脚手架，当前重点落地财务模块：费率引擎、应收账单、成本导入、自动对账、保险、利润与提成。

## 目录

- `apps/backend`: Java/Spring Boot 主后端，后续平台 API 以这里为准。
- `apps/api`: 早期 Fastify API 原型，后续逐步迁移到 Spring Boot。
- `apps/web`: 运营后台前端脚手架。
- `packages/shared`: 前后端共享枚举与类型。
- `db/migrations`: PostgreSQL 数据库迁移。
- `db/seeds`: 基础字典与样例数据。
- `infra`: 本地开发基础设施。
- `docs`: 系统架构与项目分工文档。

关键文档：

- `docs/system-architecture.md`: 主系统总体架构设计。
- `docs/assets/system-architecture.pdf`: 主系统总体架构图 PDF。
- `docs/spring-boot-implementation-plan.md`: Java/Spring Boot 后端落地方案。
- `docs/main-system-database-design.md`: 主系统数据库表结构设计。
- `docs/auth-architecture.md`: 登录与权限架构设计。
- `docs/xqt-finance-api-requests.md`: 新智慧财务模块请求和 body 对照。

## 本地启动

### 环境要求

- Node.js 22+
- npm 11+
- Java 17+
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
cd apps/backend && ./mvnw test
```

### 4. 启动开发服务

```bash
npm run dev
```

默认端口：

- Fastify 原型 API: `http://localhost:8080`
- Web: `http://localhost:5173`

Spring Boot 后端：

```bash
cd apps/backend
./mvnw spring-boot:run
```

如果本机 `8080` 已被其他 Java 服务占用，或你使用本机 PostgreSQL `5432`：

```bash
cd apps/backend
env -u DEBUG API_PORT=18103 SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/xqt_saas ./mvnw spring-boot:run
```

### 5. API 快速验证

```bash
curl http://localhost:8080/api/health
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
