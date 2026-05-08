# 阿里巴巴 Java 开发规约落地版

生成日期：2026-05-07  
适用范围：`apps/backend` Spring Boot 主后端，以及后续所有 Java 业务模块。  
参考来源：Alibaba P3C 官方仓库 `https://github.com/alibaba/p3c`，当前公开说明的最新版为《Java开发手册（黄山版）》；P3C 同时提供 PMD、IntelliJ IDEA、Eclipse 插件能力。

## 1. 项目强制原则

1. Controller 不返回裸 `Map`，必须返回明确 DTO 或 `ApiResponse<T>`。
2. Controller 不直接写复杂 SQL；查询、事务、业务状态流必须进入 Service。
3. 对外接口 request / response 必须有 DTO，不允许用 `Map<String, Object>` 作为公开契约。
4. `Map<String, Object>` 只允许出现在数据访问转换层、JSON metadata、外部字段映射层。
5. 字符串状态、权限码、来源系统、客户方向、服务模式必须沉淀为常量或枚举；不得在业务代码里散落魔法值。
6. 写接口必须记录操作人和审计日志，不能接受前端传入的 `createdBy`、`updatedBy`、`deletedBy`。
7. 所有列表接口必须有分页上限，默认 `pageSize=20`，最大 `pageSize=100`。
8. 业务异常必须使用明确错误码和统一异常处理，不允许 Controller 直接散落 `ResponseStatusException`。
9. 所有新增 Java 文件必须通过 `./mvnw verify`，包含测试、Checkstyle、P3C/PMD、SpotBugs。
10. 涉及前端契约变化时，必须同步 `npm run lint -w apps/web` 和 `npm run build -w apps/web`。

## 2. 分层规范

### 2.1 Controller

职责：

1. 接收 HTTP 请求。
2. 参数校验。
3. 获取登录主体。
4. 调用 Service。
5. 返回 `ApiResponse<T>`。

禁止：

1. 写复杂 SQL。
2. 组装大段业务逻辑。
3. 返回裸 `Map`。
4. 直接拼接审计内容。

示例：

```java
@GetMapping("/users")
public ApiResponse<ListResponse<UserView>> users(Authentication authentication) {
    AuthPrincipal auth = context.principal(authentication);
    return ApiResponse.ok(adminService.listUsers(auth));
}
```

### 2.2 Service

职责：

1. 设置租户上下文。
2. 处理业务状态。
3. 控制事务。
4. 调用 Repository 或 JDBC。
5. 写审计日志。
6. 把数据库行转换为 View DTO。

### 2.3 Repository / JDBC

当前项目使用 Spring JDBC，不使用 ORM。规则：

1. SQL 参数必须使用占位符，不允许字符串拼接外部输入。
2. 查询结果可以短暂使用 `Map<String, Object>`，但必须在 Service 层转换成 DTO。
3. 所有业务表必须带租户条件或依赖 RLS。
4. `deleted_at IS NULL` 的软删除过滤必须明确。
5. 金额、账单、核销、库存、导入类写操作必须支持幂等。

## 3. API 响应规范

### 3.1 统一响应

所有新接口统一返回：

```json
{
  "ok": true,
  "data": {},
  "error": null,
  "errorCode": null
}
```

对应 Java：

```java
public record ApiResponse<T>(boolean ok, T data, String error, String errorCode) {
}
```

### 3.2 列表响应

```json
{
  "ok": true,
  "data": {
    "items": []
  },
  "error": null,
  "errorCode": null
}
```

Java 类型：

```java
ApiResponse<ListResponse<UserView>>
```

### 3.3 分页响应

```json
{
  "ok": true,
  "data": {
    "items": [],
    "page": 1,
    "pageSize": 20
  },
  "error": null,
  "errorCode": null
}
```

Java 类型：

```java
ApiResponse<PageResponse<OrderView>>
```

### 3.4 单对象响应

```json
{
  "ok": true,
  "data": {
    "item": {}
  },
  "error": null,
  "errorCode": null
}
```

Java 类型：

```java
ApiResponse<ItemResponse<OrderView>>
```

### 3.5 命令响应

```json
{
  "ok": true,
  "data": {
    "success": true
  },
  "error": null,
  "errorCode": null
}
```

Java 类型：

```java
ApiResponse<CommandResponse>
```

## 4. 命名规范

| 类型 | 规则 | 示例 |
| --- | --- | --- |
| Controller | 资源名 + `Controller` | `SellerOrderController` |
| Service | 业务名 + `Service` | `BusinessFlowService` |
| Request DTO | 动作 + `Request` | `UserSaveRequest` |
| Response DTO | 语义 + `Response` | `HealthResponse` |
| View DTO | 页面/接口视图 + `View` | `OrderView` |
| 常量 | 全大写下划线 | `DEFAULT_PAGE_SIZE` |
| 权限码 | 小写点分层 | `admin.user.read` |
| API 路径 | 小写 kebab-case | `/api/business-flows` |

## 5. 常量和枚举

必须抽取常量的场景：

1. 状态：`ACTIVE`、`DISABLED`、`DRAFT`、`CANCELLED`。
2. 来源：`LOCAL`、`ACC`、`XQT`。
3. 客户方向：`SELLER_CUSTOMER`、`DOCUMENT_CUSTOMER`。
4. 服务模式：`SELLER_FULFILLMENT`、`DOCUMENT_SHIPPING`。
5. 分页默认值和最大值。
6. 审计动作：`CREATE`、`UPDATE`、`DELETE`。

短期允许常量放在当前 Service；长期应收敛到领域枚举或常量类。

## 6. 异常与校验

1. 请求 body 必填时必须校验。
2. 新增接口使用 `@Valid` + request DTO。
3. 业务不存在返回 404。
4. 参数不合法返回 400。
5. 未授权返回 401。
6. 无权限返回 403。
7. 不允许用 `catch (Exception ignored)` 吞掉业务异常。
8. 不允许返回 `ok=true` 但实际业务失败。

## 7. 审计与安全

写操作必须满足：

1. 从 token 获取 `tenantId` 和 `userId`。
2. SQL 写入 `created_by`、`updated_by`、`deleted_by`。
3. 写入 `audit_logs`。
4. 审计记录包括 entity、action、before、after。
5. 删除优先软删除。
6. 禁止前端伪造操作人。

## 8. 数据库规范

1. 所有业务表必须有 `tenant_id`。
2. 所有主键使用内部 UUID，不使用 ACC/XQT 外部 ID 做主键。
3. 外部 ID 进入 `external_object_refs`。
4. 外部字段进入 `external_field_mappings`。
5. 金额字段必须明确币种。
6. 财务流水、账单、核销、账本分层，不混写。
7. 查询条件必须使用索引友好字段。
8. 大查询必须分页。

## 9. 文档规范

新增或修改 API 时必须同步：

1. `docs/api-request-body-catalog.md`
2. `docs/api-development-guide.md`
3. 对照 ACC/XQT 的接口必须同步 `docs/acc-xqt-full-match-roadmap.md`
4. 有页面/字段对照的模块必须新增 comparison case

文档中必须写清：

1. HTTP method。
2. path。
3. request body。
4. response type。
5. 权限码。
6. 操作人记录。
7. 旧系统对照来源。

## 10. 当前项目已完成的规约修正

| 范围 | 修正内容 |
| --- | --- |
| Admin API | 拆分为 `AdminController`、`AdminService`、`AdminRepository`，公开返回值改为 `ApiResponse<ListResponse/ItemResponse/PageResponse/CommandResponse>` |
| Auth API | `/api/auth/login`、`/api/auth/me`、`/api/auth/logout` 统一返回 `ApiResponse<T>` |
| Health API | `/api/health` 改为 `HealthController` + `HealthService` + `HealthResponse` |
| Business Flow API | Controller 不再直接写 SQL，改由 `BusinessFlowService` 负责查询 |
| Seller / Document Order API | 公开响应改为 `OrderView`、`OrderLineView`、`PageResponse`、`ItemResponse`、`CommandResponse` |
| 统一异常 | 新增 `ApiException`、`ErrorCode`、`GlobalExceptionHandler`，错误响应统一包含 `errorCode` |
| 质量门禁 | Maven `verify` 接入 Checkstyle、P3C/PMD、SpotBugs，新增 API 契约测试 |
| 前端适配 | 登录态读取兼容 `ApiResponse<LoginResponse>` |

## 11. 后续强制检查清单

每次提交前检查：

```bash
cd apps/backend && ./mvnw verify
npm run lint -w apps/web
npm run build -w apps/web
rg "public Map<String, Object>" apps/backend/src/main/java/com/xqt/saas
```

如果 `public Map<String, Object>` 出现在 Controller 或 Service 的公开方法上，必须重构后再提交。

## 12. 长期持续治理项

1. 订单模块和流程模块当前使用 Spring JDBC，后续可按模块体量继续拆 Repository。
2. 状态常量后续应集中到领域枚举类。
3. 测试应继续补齐权限、租户隔离、卖货流程、制单流程、审计字段的集成测试。
4. CI 中必须保留 `./mvnw verify`、前端 lint/build，避免规范退化。
