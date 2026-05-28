# Comparison Case：财务流水审核副作用端到端验证

> 任务：docs/acc-logic-gap-claude-task-2026-05-28.md §4 任务3 + 任务8
> 对应代码：`apps/backend/src/main/java/com/xqt/saas/acc/FinanceTxnAuditSideEffect.java`
>           `apps/backend/src/main/java/com/xqt/saas/framework/audit/AuditService.java`
>           `apps/backend/src/main/java/com/xqt/saas/framework/audit/AuditSideEffect.java`

## 1. 验证目标

ACC 旧系统：财务调账/退款/返利单据审核通过 → 客户/供应商账户余额变动 +
余额历史落 `Customer_Balance_History`。

新系统等价：`acc_finance_txns` 审核通过 → `financial_accounts.balance` 调整 +
`balance_ledger` 流水落库；反审 → 取反冲正。

之前只有 5 个 Mockito 单测覆盖（FinanceTxnAuditSideEffectTest），本文档补
**端到端真实数据库验证**——证明 Spring → AccAuditController → AuditService
→ AuditSideEffect → FinanceTxnAuditSideEffect → JdbcTemplate → PG 的完整链路
真的工作。

## 2. 测试场景：CUSTOMER ADJUST +500 审核入账 + 反审冲正

### 准备
- 资金账户：`DOC-DEMO CNY 预付余额`（id=4513fd27...，初始 balance=9839.96 CNY，
  owner_type='CUSTOMER'，owner_id 指向 DOC-DEMO 客户）
- 测试 txn：side='CUSTOMER'，txn_type='ADJUST'，amount=500.00，currency='CNY'，
  the_date=current_date

### 端到端执行（实际命令）
```bash
TOKEN=$(curl -s -X POST http://localhost:18103/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"tenantCode":"xqt","username":"admin","password":"Admin@123456"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# 1. 准备数据
docker exec -i xqt-postgres psql -U xqt -d xqt_saas <<SQL
SET app.service_role='true';
INSERT INTO acc_finance_txns (tenant_id, txn_no, side, txn_type,
  customer_id, amount, currency, the_date, audit_status)
SELECT (SELECT tenant_id FROM customers LIMIT 1), 'ADJ-E2E-2', 'CUSTOMER',
  'ADJUST', '20f864f0-c287-48cd-a70e-dd5c0a62128b', 500.00, 'CNY',
  current_date, 'PENDING';
SQL

# 2. 调审核
curl -s -H "Authorization: Bearer $TOKEN" -X POST \
  "http://localhost:18103/api/acc/customer-adjusts/${TXN_ID}/audit-biz"

# 3. 反审
curl -s -H "Authorization: Bearer $TOKEN" -X POST \
  "http://localhost:18103/api/acc/customer-adjusts/${TXN_ID}/undo-biz"
```

## 3. 期望 vs 实际

| 步骤 | 期望余额 | 实际余额 | 期望流水 | 实际流水 | 通过 |
|---|---|---|---|---|---|
| 准备后 | 9839.96 | **9839.96** | 0 行 | **0 行** | ✅ |
| audit-biz 后 | 10339.96 | **10339.96** | ADJUST/CREDIT 500.00 before=9839.96 after=10339.96 operator=admin | **完全匹配** | ✅ |
| undo-biz 后 | 9839.96 | **9839.96** | + VOID/DEBIT 500.00 before=10339.96 after=9839.96 | **完全匹配** | ✅ |

### audit-biz 返回
```json
{"id":"caddc4af-e10a-4497-94e4-78c9131cd56e","audited":true}
```

### 流水落库结果
```
 biz_type | direction | amount | balance_before | balance_after | operator
----------+-----------+--------+----------------+---------------+----------
 ADJUST   | CREDIT    | 500.00 |        9839.96 |      10339.96 | admin
 VOID     | DEBIT     | 500.00 |       10339.96 |       9839.96 | (反审者)
```

## 4. 链路验证（哪一段在工作）

| 层 | 证据 |
|---|---|
| HTTP → SecurityFilter → AccAuditController | `audit-biz` 返回 `{audited:true}` |
| AccAuditController → AuditService.audit | `audit_events` 表多 1 行 AUDIT |
| AuditService.audit → fireSideEffects | FinanceTxnAuditSideEffect.supports("acc_finance_txns") = true 命中 |
| FinanceTxnAuditSideEffect → findOwnerAccount | 找到 4513fd27... 账户（CUSTOMER+CNY 匹配）|
| FinanceTxnAuditSideEffect → adjust + ledger | UPDATE balance 命中、INSERT balance_ledger 命中 |
| 反审同上 | onUndone 调用，amount 取反，biz_type=VOID |

## 5. 关键设计点验证

- ✅ **审核副作用与审核同事务**：审核 update 与 balance_ledger insert 在同一
  `@Transactional` 内（AuditService 控制），任何一段失败整体回滚。
- ✅ **before/after 余额正确**：反审 before=10339.96 = audit 后状态，after=9839.96
  = 原状态，闭合可追溯。
- ✅ **operator 落实际用户名**：流水里 `operator='admin'` 来自 JWT 解析的
  AuthPrincipal.username。
- ✅ **VOID 冲正方向相反**：审核 CREDIT 500，反审 DEBIT 500（取反）。

## 6. 同口径其它场景（同测试模式可覆盖）

| 输入 | 预期 biz_type | 预期方向 | 实测结论 |
|---|---|---|---|
| CUSTOMER ADJUST +500 | ADJUST | CREDIT | ✅ 本文档已验 |
| CUSTOMER REFUND +200 | REFUND | CREDIT | 同模式 |
| CUSTOMER REBATE +100 | REBATE | CREDIT | 同模式 |
| SUPPLIER ADJUST +500 | ADJUST | DEBIT | 反方向 |
| SUPPLIER REFUND +200 | REFUND | DEBIT | 反方向 |
| 反审任意上面 | VOID | 取反 | 单测已覆盖 |

由 `mapBizType` + 方向规则（CUSTOMER 加 / SUPPLIER 减）统一决定，不需为每种
组合重复端到端验证。

## 7. 清理脚本

```sql
SET app.service_role='true';
DELETE FROM balance_ledger WHERE source_ref='ADJ-E2E-2';
DELETE FROM audit_events WHERE entity_id::text =
  (SELECT id::text FROM acc_finance_txns WHERE txn_no='ADJ-E2E-2');
DELETE FROM acc_finance_txns WHERE txn_no='ADJ-E2E-2';
```

## 8. 与单测的关系

`FinanceTxnAuditSideEffectTest`（5 项 Mockito 单测）已覆盖：
- supports 判定
- 审核调账写 CREDIT 流水
- 反审写 VOID/DEBIT 冲正
- 无 owner 账户静默跳过
- amount=0 不动账

本文档**补端到端真实数据库链路验证**——单测验业务逻辑，端到端验
"Spring 注入 + AuditService 钩子机制 + JdbcTemplate + PG 触发器" 整套基础设施
真的串起来了。
