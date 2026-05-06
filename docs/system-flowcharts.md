# 系统流程图

版本：2026-05-06

## 1. 总体访问流程

```mermaid
flowchart TB
  user["内部员工<br/>财务 / 操作 / 管理员"] --> browser["浏览器"]
  browser --> web["Vue 若依风格后台<br/>localhost:5173"]

  web -->|"认证/权限/系统管理<br/>/api/auth /api/admin /api/health"| spring["Spring Boot 主后端<br/>localhost:18103"]
  web -->|"业务原型接口<br/>/api/finance /api/acc /api/sys /health"| fastify["Fastify 原型 API<br/>localhost:18080"]

  spring --> pg["PostgreSQL 主库<br/>localhost:5432 / tenant_id + RLS"]
  fastify --> pg

  fastify -. "ACC adapter<br/>当前未接通真实 MySQL" .-> acc["ACC 旧系统"]
  fastify -. "XQT adapter<br/>只读请求/页面样本" .-> xqt["新智慧 TMS/AOS"]

  pg --> auth["租户 / 用户 / 角色 / 权限 / 会话"]
  pg --> core["订单 / 运单 / 仓库 / 财务 / 账本"]
  pg --> evidence["external_* / comparison_cases<br/>复刻证据"]
```

## 2. 前端路由与代理流程

```mermaid
flowchart LR
  web["apps/web<br/>Vite dev server :5173"] --> authPath{请求路径}

  authPath -->|"/api/auth/*"| springAuth["Spring Boot :18103<br/>登录/登出/当前用户"]
  authPath -->|"/api/admin/*"| springAdmin["Spring Boot :18103<br/>用户/角色/权限"]
  authPath -->|"/api/health"| springHealth["Spring Boot :18103<br/>健康检查"]
  authPath -->|"/api/*"| fastifyApi["Fastify :18080<br/>ACC/XQT/财务/系统原型"]
  authPath -->|"/health"| fastifyHealth["Fastify :18080<br/>上游适配器状态"]

  springAuth --> pg[(PostgreSQL)]
  springAdmin --> pg
  fastifyApi --> pg
```

## 3. 登录与会话流程

```mermaid
sequenceDiagram
  autonumber
  participant U as 用户
  participant W as Web
  participant A as Spring Boot Auth
  participant DB as PostgreSQL

  U->>W: 输入租户、用户名、密码
  W->>A: POST /api/auth/login
  A->>DB: set_config('app.service_role','true')
  A->>DB: 查询 tenants/users/roles/permissions
  A->>A: 校验 PBKDF2 密码
  A->>A: 签发 HMAC Token
  A->>DB: 写 user_sessions
  A->>DB: 写 auth_login_events
  A-->>W: token + user + permissions
  W->>W: localStorage 保存 token

  W->>A: GET /api/auth/me + Authorization
  A->>A: 校验 token 签名和 exp
  A->>DB: 校验 session_hash 未过期未注销
  A-->>W: 当前用户

  W->>A: POST /api/auth/logout
  A->>DB: user_sessions.revoked_at = now()
  A-->>W: ok=true
```

## 4. 权限校验流程

```mermaid
flowchart TB
  request["带 Bearer Token 的请求"] --> filter["BearerAuthFilter"]
  filter --> verify["TokenService.verify<br/>签名 + 过期时间"]
  verify --> session["AuthService.validateSession<br/>查 user_sessions"]
  session --> authorities["roles + permissions<br/>转 Spring Security authorities"]
  authorities --> preauth{"@PreAuthorize<br/>权限判断"}

  preauth -->|通过| controller["Controller 执行业务"]
  preauth -->|失败| forbidden["403 Forbidden"]

  controller --> tenant["事务内设置<br/>app.current_tenant_id"]
  tenant --> db["PostgreSQL RLS<br/>租户隔离"]
```

## 5. ACC 复刻流程

```mermaid
flowchart TB
  code["阅读 ACC 源码<br/>APIClass.php / Track.php / Scale.php"] --> reverse["反推字段/状态/规则"]
  dbAcc["接通 ACC 只读 MySQL"] --> sample["抽取真实样本"]
  reverse --> mapping["建立字段映射<br/>external_field_mappings"]
  sample --> snapshot["保存样本<br/>external_payload_snapshots"]
  mapping --> compare["建立 comparison_cases"]
  snapshot --> compare
  compare --> pass{"人工对照通过?"}
  pass -->|否| adjust["调整字段/状态/规则"]
  adjust --> compare
  pass -->|是| implement["迁入 Spring Boot 业务模块"]
  implement --> mainDb["写入主业务模型<br/>orders/shipments/charges/bills"]
  mainDb --> retire["逐步下线 /api/acc 原型接口"]
```

## 6. 新智慧复刻流程

```mermaid
flowchart TB
  page["打开新智慧页面<br/>只读操作"] --> capture["抓取列表接口和表单字段"]
  capture --> body["整理 POST body<br/>timeLimit/scenes + 筛选字段"]
  body --> docs["写入文档<br/>xqt-finance-api-requests.md"]
  docs --> pageClone["在新系统复刻页面"]
  pageClone --> apiClone["Spring Boot 实现主系统 API"]
  apiClone --> db["落入主库财务模型"]
  db --> compare["页面金额/状态/筛选结果对照"]
  compare --> ok{"对照通过?"}
  ok -->|否| fix["修正字段映射和口径"]
  fix --> compare
  ok -->|是| replace["新系统承载业务操作"]
```

## 7. 财务闭环流程

```mermaid
flowchart TB
  order["订单/运单/仓库事件"] --> rate["费率规则匹配<br/>rate_rules / rate_versions"]
  rate --> charge["生成费用行<br/>charges + 规则快照"]
  charge --> audit["费用审核/调整"]
  audit --> bill["生成客户/供应商账单"]
  bill --> payment["收款/付款"]
  payment --> allocation["核销匹配<br/>settlements / allocations"]
  allocation --> diff{"是否有差异?"}
  diff -->|有| review["差异队列<br/>人工复核"]
  diff -->|无| post["账本过账"]
  review --> post
  post --> ledger["ledger_transactions<br/>ledger_entries"]
  ledger --> report["利润 / 应收 / 应付 / 账本报表"]
```

## 8. 数据写入与租户隔离流程

```mermaid
sequenceDiagram
  autonumber
  participant C as Controller
  participant S as Service
  participant R as Repository/JdbcTemplate
  participant DB as PostgreSQL RLS

  C->>S: 传入 AuthPrincipal.tenantId
  S->>R: 开启事务
  R->>DB: select set_config('app.current_tenant_id', tenantId, true)
  S->>R: 执行业务 SQL
  R->>DB: SQL 自动受 RLS 策略约束
  DB-->>R: 当前租户数据
  R-->>S: 返回结果
  S-->>C: JSON DTO
```

## 9. 本地启动流程

```mermaid
flowchart LR
  pg["PostgreSQL :5432"] --> spring["Spring Boot :18103"]
  pg --> fastify["Fastify :18080"]
  redis["Redis :6379"] -. "后续任务/缓存" .-> spring
  spring --> web["Vite Web :5173"]
  fastify --> web

  web --> browser["http://localhost:5173"]
```

当前后台会话：

| 会话 | 服务 | 地址 |
| --- | --- | --- |
| `xqt-web-5173` | Web 前端 | `http://localhost:5173` |
| `xqt-backend-18103` | Spring Boot 后端 | `http://localhost:18103` |

