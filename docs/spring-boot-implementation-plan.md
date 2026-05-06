# Spring Boot 后端落地方案

版本：2026-05-06

## 1. 技术路线

主平台后端切换为 Java/Spring Boot：

| 项 | 选择 |
| --- | --- |
| Java | Java 17 |
| 框架 | Spring Boot 3.5.x |
| Web | Spring MVC |
| 安全 | Spring Security |
| 数据库访问 | Spring JDBC |
| 数据库 | PostgreSQL |
| 缓存/队列 | Redis |
| 健康检查 | Spring Boot Actuator |
| 前端 | Vue 3 + Vite，继续保留 |

选择 Java 17/Spring Boot 3.5.x 的原因：

1. 当前本机已安装 Java 17，可以直接开发和验证。
2. Spring Boot 3.5.x 是当前稳定 3.x 线，适合作为业务平台一期。
3. 业务数据库已经大量使用 PostgreSQL、RLS、复杂 SQL 和财务聚合，Spring JDBC 比 JPA 更适合先期落地。
4. 后续如果要拆服务，Spring Boot 生态更容易扩展到 Spring Batch、Spring Cloud、消息队列和可观测性。

## 2. 代码位置

Java 后端工程：

```text
apps/backend
  pom.xml
  mvnw
  src/main/java/com/xqt/saas
  src/main/resources/application.yml
```

当前 TypeScript 后端：

```text
apps/api
```

定位为早期原型和迁移对照。后续新功能优先写入 `apps/backend`。

## 3. 当前已落地接口

| API | 说明 |
| --- | --- |
| `GET /api/health` | 应用和数据库健康检查 |
| `POST /api/auth/login` | 登录 |
| `GET /api/auth/me` | 当前用户 |
| `POST /api/auth/logout` | 退出登录 |
| `GET /api/admin/users` | 用户列表 |
| `GET /api/admin/roles` | 角色列表 |
| `GET /api/admin/permissions` | 权限列表 |

这些接口直接使用现有主库表：

- `users`
- `roles`
- `permissions`
- `user_roles`
- `role_permissions`
- `user_sessions`
- `auth_login_events`

## 4. 后端包结构目标

```text
com.xqt.saas
  auth
  admin
  common
  health
  module
    masterdata
    order
    shipment
    warehouse
    finance
    reconciliation
    ledger
    external
  adapter
    acc
    xqt
  job
  infrastructure
    db
    redis
    audit
    tenant
```

每个业务模块建议按以下结构组织：

```text
module/finance
  FinanceController
  FinanceService
  FinanceRepository
  dto
  model
```

## 5. 租户和权限策略

Spring Boot 后端必须继承当前数据库隔离方式：

1. 登录时使用受控 service role 查询用户、角色、权限。
2. 业务请求从 token 中读取 `tenantId`。
3. 每个业务事务内设置 `app.current_tenant_id`。
4. SQL 仍然受 PostgreSQL RLS 保护。
5. 权限判断使用 Spring Security authority，对应 `permissions.code`。

权限示例：

```java
@PreAuthorize("hasAuthority('admin.user.read')")
```

## 6. 迁移顺序

### P0：基础能力

- 登录、登出、当前用户。
- 用户、角色、权限查询。
- 统一错误响应。
- 租户上下文工具。
- 审计日志工具。

### P1：新智慧财务复刻

- 财务流水。
- 客户流水。
- 客户账单。
- 供应商流水。
- 供应商账单。
- 账户和账户流水。
- 费用审批。
- 每个 POST 请求 body 与 `docs/xqt-finance-api-requests.md` 对齐。

### P2：订单、运单、仓库

- 订单列表和详情。
- 运单审计。
- 箱明细。
- 仓库收货、拣货、装车。
- 扫描事件。

### P3：ACC 逻辑迁移

- ACC adapter Java 化。
- 旧计费逻辑逐条迁移。
- 状态机迁移。
- 典型业务用例对照。

### P4：替代旧 API 原型

- 前端 API base 切到 Spring Boot。
- 下线 Fastify 中已迁移路由。
- 保留必要的对照工具。

## 7. 本地命令

启动基础设施：

```bash
docker compose -f infra/docker-compose.yml up -d
```

构建 Java 后端：

```bash
cd apps/backend
./mvnw test
```

启动 Java 后端：

```bash
cd apps/backend
./mvnw spring-boot:run
```

如果连接本机 PostgreSQL `5432`：

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/xqt_saas ./mvnw spring-boot:run
```

如果本机 `8080` 已被占用：

```bash
env -u DEBUG API_PORT=18103 SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/xqt_saas ./mvnw spring-boot:run
```
