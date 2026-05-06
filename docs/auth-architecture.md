# 登录与权限架构设计

版本：2026-05-06

## 设计口径

认证与权限必须服务主系统，而不是复用 ACC 或新智慧的账号。主系统账号体系以 `users`、`roles`、`permissions` 为核心，外部系统账号只作为迁移/对照信息，不参与主系统授权判断。

## 架构分层

```text
Web Login
  -> POST /api/auth/login
  -> users + roles + permissions
  -> user_sessions + auth_login_events
  -> signed token
  -> API Authorization: Bearer <token>
  -> requireAuth
  -> permission checks / tenant context
```

## 数据表

| 表 | 作用 |
| --- | --- |
| `users` | 内部员工账号，包含 `username/password_hash/status/last_login_at` |
| `roles` | 角色定义 |
| `permissions` | 权限定义，采用 `resource/action/code` |
| `user_roles` | 用户角色授权，支持租户、分公司、对象范围 |
| `role_permissions` | 角色权限绑定 |
| `user_sessions` | 登录会话，支持撤销和过期 |
| `auth_login_events` | 登录成功/失败审计 |
| `customer_accounts` | 客户门户账号，后续使用 |
| `api_credentials` | API 凭证，后续用于客户 API 和系统集成 |

## 密码策略

当前实现：

- 算法：PBKDF2
- 摘要：SHA-256
- 迭代：210000
- 存储格式：`pbkdf2$sha256$iterations$salt$hash`

后续可以升级到 Argon2id；因为存储格式带算法头，可以兼容多算法。

## Token 策略

当前实现：

- HMAC SHA-256 签名 token
- 默认有效期 8 小时
- token payload 包含：
  - `userId`
  - `tenantId`
  - `tenantCode`
  - `username`
  - `displayName`
  - `roles`
  - `permissions`
  - `exp`
  - `jti`
- `jti` 哈希后写入 `user_sessions`
- `logout` 通过撤销 `user_sessions.revoked_at` 让会话失效

后续如果需要标准 JWT 兼容，可替换签发实现，不影响表结构。

## API

| API | Method | 说明 |
| --- | --- | --- |
| `/api/auth/login` | POST | 登录，返回 token 和用户信息 |
| `/api/auth/me` | GET | 获取当前用户、角色、权限 |
| `/api/auth/logout` | POST | 注销当前会话 |

登录 body：

```json
{
  "tenantCode": "xqt",
  "username": "admin",
  "password": "<LOCAL_DEV_PASSWORD>"
}
```

响应：

```json
{
  "ok": true,
  "token": "...",
  "expiresIn": 28800,
  "user": {
    "id": "...",
    "tenantCode": "xqt",
    "username": "admin",
    "displayName": "系统管理员",
    "roles": ["ADMIN"],
    "permissions": ["..."]
  }
}
```

## 权限模型

权限码采用点分结构：

```text
domain.resource.action
```

示例：

| 权限 | 说明 |
| --- | --- |
| `admin.user.read` | 查看用户 |
| `admin.user.write` | 维护用户 |
| `admin.role.read` | 查看角色 |
| `admin.role.write` | 维护角色 |
| `finance.receivable.read` | 查看应收 |
| `finance.receivable.write` | 维护应收 |
| `finance.payable.read` | 查看应付 |
| `finance.payable.write` | 维护应付 |
| `finance.ledger.post` | 正式过账 |
| `finance.adjust.approve` | 审批财务调整 |
| `operation.order.write` | 维护订单 |
| `warehouse.scan.write` | 仓库扫描 |

`ADMIN` 角色绑定全部权限。

## 租户隔离

数据库仍以 PostgreSQL RLS 为底线：

- 业务表带 `tenant_id`
- API 根据登录 token 获取 `tenantId`
- 读写业务数据时应设置 `app.current_tenant_id`
- 认证过程使用受控 service role 查询用户和权限

## 前端行为

1. 未登录时只显示登录页。
2. 登录成功后 token 保存到 `localStorage`。
3. 请求系统管理接口时自动加 `Authorization` header。
4. 401 时清除 token，回到登录页。
5. 侧边栏显示当前用户、租户、角色，并提供退出按钮。

## 安全边界

当前已完成：

- 密码非明文保存
- token 过期
- 会话撤销
- 登录失败审计
- 5 次失败临时锁定
- `/api/sys/*` 非登录接口要求 token

后续建议：

- 强制首次登录修改默认密码
- 密码复杂度和历史密码
- 管理员重置密码审计
- 角色变更审计
- 客户门户独立登录入口
- API key 签名、时间戳和 nonce 防重放
- 前端菜单按权限过滤

## 与旧系统关系

ACC 的 `Customer_API`、新智慧页面登录账号，都不进入主系统授权判断。它们只作为外部系统样本或迁移来源，映射到：

- `external_systems`
- `external_object_refs`
- `api_credentials`
- `customer_accounts`

主系统上线后，权限判断只看主库。
