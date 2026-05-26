# ACC tab Retrofit 模式：让每个 controller "完整复刻 ACC 行为"

生成日期：2026-05-24
适用：把已经做了"形状映射"的 18 个 ACC tab controller 升级到 ACC 业务行为等价。

## 1. 已就位的 5 个框架（基础设施）

| 框架 | 类 | 用途 |
|---|---|---|
| 审核流 | `framework.audit.AuditService` (bean name `accAuditService`) | audit / undoAudit / batchAudit / history + 写不可变 `audit_events` |
| 状态机 | `framework.statemachine.StateMachineRegistry` | 按 `(entity, fromStatus, event)` 查转移合法性，非法转移抛 ApiException |
| 级联校验 | `framework.cascade.CascadeChecker` | 删除前扫描相关表确认无依赖 |
| 字段权限 | `framework.fieldgate.FieldGate` | 审核后 update 只允许预声明字段，其它 silently rejected 并返回 `rejectedFields` 列表 |
| 汇率快照 | `framework.money.MoneySnapshotService` | 落库时 freeze 当时汇率到 `exchange_rate_snapshots`，避免日后金额漂移 |

通用审核端点（无需每个 tab 重复实现）：
- `POST /api/acc/{tab}/{id}/audit-biz`
- `POST /api/acc/{tab}/{id}/undo-biz`
- `POST /api/acc/{tab}/batch-audit`（body：`{ ids: [...] }`）
- `GET /api/acc/{tab}/{id}/audit-history`

入口在 `acc.AccAuditController`，按 tab → 表名映射查；新 tab 接入 audit 流只需在 `TAB_TO_TABLE` 加一行。

## 2. 单个 controller 的 retrofit checklist

按照 `AccChargesController` 的实现模式抄。**每项打钩才算"复刻完"**：

### A. 注入依赖
```java
private final CascadeChecker cascadeChecker;
private final FieldGate fieldGate;
private final MoneySnapshotService moneySnapshotService;  // 仅金额型 tab 需要

public AccXXXController(JdbcTemplate jdbc, JsonSupport json,
                        CascadeChecker cascadeChecker, FieldGate fieldGate,
                        MoneySnapshotService moneySnapshotService) { ... }
```

### B. list / raw 端点：在 SELECT 增加 `audit_status / audited_at / audit_name`
```sql
SELECT ..., audit_status, audited_at, audit_name FROM xxx WHERE ...
```
在 `project()` 中加：
```java
out.put("auditStatus", row.get("audit_status"));
out.put("auditedAt", json.value(row.get("audited_at")));
out.put("auditName", row.get("audit_name"));
```

### C. POST `create`：金额类必须落汇率快照
```java
String id = jdbc.queryForObject("INSERT ... RETURNING id::text", ...);
moneySnapshotService.snapshot(TABLE, id, amount, currency);
return Map.of("id", id);
```

### D. PUT `update`：先过字段闸
```java
String currentAudit = jdbc.queryForObject(
    "SELECT audit_status FROM xxx WHERE id = ?::uuid", String.class, id);
FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
    throw ApiException.badRequest("xxx 已审核，字段不可修改: " + ... + "；请先反审");
}
Map<String, Object> allowed = gate.allowed();
// 后续 UPDATE 用 allowed.get(...) 而非 body.get(...)
```

### E. DELETE：先级联校验 + 审核后锁定
```java
String auditStatus = jdbc.queryForObject(
    "SELECT audit_status FROM xxx WHERE id = ?::uuid", String.class, id);
if ("AUDITED".equals(auditStatus)) {
    throw ApiException.badRequest("xxx 已审核，不能删除，请先反审");
}
cascadeChecker.checkBeforeDelete(TABLE, id);
jdbc.update("DELETE FROM xxx WHERE id = ?::uuid", id);
```

### F. 状态机驱动的端点（订单 submit/cancel、账单 issue/pay 等）
凡是触发业务状态变化的端点：
```java
stateMachineRegistry.check("orders", currentStatus, "submit");
String nextStatus = stateMachineRegistry.nextStatus("orders", currentStatus, "submit");
jdbc.update("UPDATE orders SET status = ? WHERE ...", nextStatus, ...);
```

### G. 注册级联规则（如果是新模块）
在 `CascadeChecker.init()` 加一段：
```java
register("xxx", List.of(
    CascadeRule.of("relation_table", "fk_column", "xxx 有 {n} 个关联记录，不能删除"),
    new CascadeRule("audit_check",
        "SELECT count(*) FROM relation WHERE xxx_id = ?::uuid AND audit_status = 'AUDITED'",
        "xxx 有 {n} 条已审核关联记录")
));
```

### H. 注册状态机（如果有自己的 status enum）
在 `StateMachineRegistry.init()` 加：
```java
machines.put("xxx", EntityStateMachine.builder()
    .transition("DRAFT", "submit", "SUBMITTED")
    .transition("SUBMITTED", "cancel", "CANCELLED")
    .build());
```

### I. 注册字段闸规则
在 `FieldGate.init()` 加：
```java
registerMutable("xxx", "AUDITED", Set.of("status", "remark", "audit_name", "audited_at"));
```

### J. 注册到通用审核端点
在 `AccAuditController.TAB_TO_TABLE` 加：
```java
"xxx", "xxx_db_table"
```

## 3. 已 retrofit 完的样板

**全部 13 个对接 tab 已 retrofit 完毕**，每个都接全 5 框架，端到端测试通过：

**财务三剑客**：
- `acc/AccChargesController.java` — 全字段闸 + 级联 + 汇率快照 + audit 字段展示
- `acc/AccCostsController.java` — 同上，AP 侧
- `acc/AccBillsController.java` — 字段闸 + 级联 + audit 字段展示

**收付款**：
- `acc/AccPaymentsController.java` — 字段闸 + 级联 + AP 汇率快照
- `acc/AccReceivedsController.java` — 字段闸 + 级联 + AR 汇率快照

**业务核心**：
- `acc/AccOrdersController.java` — 字段闸 + 级联（关联 charges audit 检查） + audit 字段
- `acc/AccShipmentsController.java` — 字段闸 + 级联（关联 charges + label_files） + audit 字段

**主数据**：
- `acc/AccCustomersController.java` — 字段闸（code 锁、name 允许）+ 级联（4 类阻塞规则）
- `acc/AccChannelsController.java` — 字段闸 + 级联（shipments/rate_cards）
- `acc/AccSuppliersController.java` — 字段闸 + 级联（partner_payments）

**字典**：
- `acc/AccCurrenciesController.java` — BIGINT id 适配 + 字段闸 + 级联
- `acc/AccFeeTypesController.java` — 字段闸 + 级联
- `acc/AccBranchesController.java` + `AccDepartmentsController.java` — 共用 organizations 表，按 org_type 区分
- `acc/AccRemotesController.java` + `AccFuelsController.java` — 简单字典

基础设施：
- `acc/AccTenantTxFilter.java` — 把每个 /api/acc/** 请求包在 PG 事务里 + set_config，让 INSERT 用的 `current_setting('app.current_tenant_id')` 跨 JDBC 调用看得到
- `acc/AccAuditController.java` — 通用 audit/undo/batch/history 端点
- `framework/audit/AuditService.java`、`framework/statemachine/StateMachineRegistry.java`、`framework/cascade/CascadeChecker.java`、`framework/fieldgate/FieldGate.java`、`framework/money/MoneySnapshotService.java`

## 4. 后续 11 tab 的 retrofit 排期建议

P0 已全部完成（payments / receiveds / orders / shipments）。剩余：

| 优先级 | tab | 依赖 |
|---|---|---|
| P1 | customers / channels / partners (suppliers) | 主数据 retrofit，主要工作是字段闸 + 级联 |
| P1 | finance_currency (currencies) / charge_items (fee-types) / organizations (branches/departments) | 字典类，retrofit 最简 |
| P2 | remote_zones / fuel_surcharge_rates | 简单字典 |

预估：P1 6 个，每个 20 分钟；P2 5 个（含 branches/departments 共用 organizations 的 2 个；fee-types/currencies/remotes/fuels/suppliers），每个 15 分钟。**剩余全部约 2.5 小时**。

## 5. 测试覆盖标准

每个 retrofit 完的 controller 至少 4 个测试：
1. `audited 状态下 update 拒绝禁止字段并返回 rejectedFields`
2. `audited 状态下 delete 抛 "请先反审"`
3. `delete 有级联时报错带依赖原因`
4. （金额类）`create 写入金额时落汇率快照`

参照 `framework.statemachine.StateMachineRegistryTests` 和 `framework.fieldgate.FieldGateTests`。

## 6. 不在本轮的工作

- 多语言错误消息（目前硬编码中文）
- 审核流的多级审批（旧 ACC 只有"主管审核"一级；如需多级须在 `audit_events` 加 stage 字段并扩展）
- 审核流权限（旧 ACC 只有"运营/财务"角色，没有列权限。要做的话挂 `@PreAuthorize`）
- `audit_events` 的回滚：当前框架不支持 undo 回到任意历史状态，只支持 audit ↔ undo_audit
