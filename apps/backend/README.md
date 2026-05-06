# XQT Spring Boot Backend

Java/Spring Boot 版后端骨架，目标是逐步替代当前 `apps/api` 的 Fastify 实现。

## 技术栈

- Java 17
- Spring Boot 3.5.x
- Spring Web MVC
- Spring Security
- Spring JDBC
- PostgreSQL
- Redis
- Actuator

## 本地启动

```bash
cd apps/backend
./mvnw spring-boot:run
```

默认连接：

- API: `http://localhost:8080`
- PostgreSQL: `jdbc:postgresql://localhost:15432/xqt_saas`
- Redis: `localhost:6379`

如果你使用本机 PostgreSQL 的 `5432`：

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/xqt_saas ./mvnw spring-boot:run
```

如果本机 `8080` 已经被占用：

```bash
env -u DEBUG API_PORT=18103 SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/xqt_saas ./mvnw spring-boot:run
```

连接服务器数据库启动：

```bash
../../scripts/start-backend-server-db.sh
```

## 已实现接口

| API | 说明 |
| --- | --- |
| `GET /api/health` | 应用和数据库健康检查 |
| `POST /api/auth/login` | 登录 |
| `GET /api/auth/me` | 当前用户 |
| `POST /api/auth/logout` | 退出登录 |
| `/api/admin/users` | 用户 CRUD、角色绑定、操作人记录 |
| `/api/admin/roles` | 角色 CRUD、权限绑定、操作人记录 |
| `GET /api/admin/permissions` | 权限列表 |
| `GET /api/admin/audit-logs` | 操作审计日志 |
| `GET /api/business-flows` | 两套业务流程 |
| `/api/seller/orders` | 客户卖货订单 CRUD |
| `/api/document/orders` | 客户自己制单订单 CRUD |

默认本地账号仍使用主库种子：

```json
{
  "tenantCode": "xqt",
  "username": "admin",
  "password": "Admin@123456"
}
```
