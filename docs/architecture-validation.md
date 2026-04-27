# 架构外部验证报告

验证日期：2026-04-27

## 结论

当前架构方向成立，适合作为 MVP 起点：模块化单体、PostgreSQL 多租户、规则版本化、费用行快照、渠道账单导入、异步对账、客户账单和利润闭环，均符合公开 SaaS 与财务系统设计经验。

但如果要进入生产，需要补 4 个关键能力：

1. 数据库层真正启用租户隔离 RLS。
2. 财务账务增加复式、不可变的 ledger/journal 层。
3. 批量导入和对账任务增加幂等键、重试、死信和任务审计。
4. API 权限从“角色字段”升级为统一授权模块，覆盖对象级和字段级授权。

## 外部证据对照

| 架构点 | 验证结果 | 依据 |
| --- | --- | --- |
| 模块化单体起步 | 正确 | Martin Fowler 的 Monolith First 观点强调多数成功微服务从单体演进，初期边界不清时应先做结构化单体。 |
| PostgreSQL pooled tenant model | 正确，但需 RLS | AWS SaaS PostgreSQL 指南认为 pooled model 成本低，但必须用 RLS 集中保证租户隔离。 |
| 所有业务表带 `tenant_id` | 正确 | AWS 对 pool model 的描述就是所有租户数据共库共表，用租户键隔离。 |
| 只靠应用层 `tenant_id` 过滤 | 不够 | PostgreSQL 官方文档说明 RLS 可在数据库层限制 SELECT/INSERT/UPDATE/DELETE；AWS 也强调减少依赖每条 SQL 都写对过滤条件。 |
| 规则版本 + 费用行快照 | 正确 | 财务和物流对账需要可追溯，Oracle Accounting Hub 也以 accounting events 生成可审计的 subledger journal。 |
| 当前 `charges` 作为财务核心表 | MVP 可用，生产不足 | 费用行适合业务计费，但正式账务应再落一层不可变、复式 ledger entries。Modern Treasury 等账本资料强调 immutability、double-entry、auditability。 |
| Redis 承载异步导入/对账 | MVP 可用 | Redis Streams 官方支持 append-only stream、consumer groups、ack/retry 语义，适合账单导入、解析、对账等异步任务。 |
| API 网关/后端统一授权 | 方向正确，但需落实现 | OWASP API Top 10 2023 把对象级授权、认证、字段级授权、函数级授权列为高风险，需要每个访问对象 ID 的接口都检查权限。 |
| OpenTelemetry/日志体系 | 需要补进部署基线 | OpenTelemetry 是厂商无关的 traces、metrics、logs 采集标准，适合后续定位对账任务、保险回调和账单导入链路问题。 |

## 必改项

### 1. PostgreSQL RLS

现在 schema 里所有核心表都有 `tenant_id`，这是对的；但还没有 `ALTER TABLE ... ENABLE ROW LEVEL SECURITY` 和 `CREATE POLICY`。生产前必须补上。

建议：

- 对所有带 `tenant_id` 的业务表启用 RLS。
- API 每个请求设置 `app.current_tenant_id`。
- RLS policy 使用 `tenant_id = current_setting('app.current_tenant_id')::uuid`。
- 对服务端后台任务设置明确的 service role，并避免默认绕过 RLS。

### 2. 不可变账本层

当前 `charges` 记录的是业务费用行，适合报价、账单、对账。但财务正式入账建议增加：

- `ledger_accounts`
- `ledger_transactions`
- `ledger_entries`
- `journal_entries`
- `posting_batches`

原则：

- 已过账分录不可 UPDATE/DELETE，只能用冲销或调整分录。
- 每笔 ledger transaction 至少两条 entry。
- 同币种借贷平衡。
- 对账差异和补收形成新的调整分录，不改历史。

### 3. 批量任务幂等

账单导入、保险回调、邮件发送、对账计算都需要幂等。

建议增加：

- `idempotency_keys`
- `background_jobs`
- `job_attempts`
- 每个导入文件的 hash。
- 每条渠道账单行的自然唯一键，例如 `carrier + invoice_no + tracking_no + fee_type + amount + shipment_date`。

### 4. 权限模型

现在 `users.role_code` 只能做简单角色。需要补：

- `roles`
- `permissions`
- `role_permissions`
- `user_roles`
- 关键业务对象的 scope：租户、客户、部门、销售归属。
- 对账、改价、冲销、绕 SOP 必须有单独权限和审计。

## 可后置项

- ClickHouse 看板库可以后置。MVP 用 PostgreSQL materialized view 或普通聚合足够。
- 微服务拆分可以后置。先按 `finance/rates/reconciliation/insurance` 做目录边界即可。
- OCR/大模型解析账单可以后置。先把 Excel/CSV 模板映射做扎实。

## 推荐下一步

1. `002_rls.sql` 已把租户隔离从设计落到数据库。
2. `003_ledger.sql` 已把费用行和正式账务分开。
3. `004_jobs_idempotency.sql` 已增加 `background_jobs` 与 `idempotency_keys`。
4. `005_authorization.sql` 已增加角色、权限和审批底座。
5. 后续继续补 API 中间件，把角色权限从数据模型接入每个业务接口。

## 参考资料

- PostgreSQL Row Security Policies: https://www.postgresql.org/docs/current/ddl-rowsecurity.html
- AWS Prescriptive Guidance, PostgreSQL RLS for SaaS: https://docs.aws.amazon.com/prescriptive-guidance/latest/saas-multitenant-managed-postgresql/rls.html
- AWS Database Blog, multi-tenant PostgreSQL RLS: https://aws.amazon.com/blogs/database/multi-tenant-data-isolation-with-postgresql-row-level-security/
- Martin Fowler, Monolith First: https://martinfowler.com/bliki/MonolithFirst.html
- Modern Treasury, ledger immutability and double-entry: https://www.moderntreasury.com/journal/how-to-scale-a-ledger-part-v
- Oracle Fusion Accounting Hub audit trail: https://docs.oracle.com/cd/E29597_01/fusionapps.1111/e20374/F484500AN23121.htm
- Redis Streams documentation: https://redis.io/docs/latest/develop/data-types/streams/
- OpenTelemetry documentation: https://opentelemetry.io/docs/
- OWASP API Security Top 10 2023: https://owasp.org/API-Security/editions/2023/en/0x11-t10/
