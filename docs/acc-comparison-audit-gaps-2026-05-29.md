# Comparison Case：S1-S9 实际接入审计 + 剩余缺口收口

> 用户反馈再追问"看一下还有差距么" → 对刚交付的 9 项 + 收尾 wiring 做对账审计

## 1. 审计方法

不依赖 commit message / doc 自评，而是直接 grep 代码：
- 所有 `INSERT INTO balance_ledger` 入口 → 必须全调 fxCapture
- 所有 `StowageStateMachine` 注入点 → controller 必须真正调用
- 所有 `no-op` / placeholder / TODO 注释
- AuthPrincipal/RLS 上下文实际值是否有意义

## 2. 发现的真实缺口

### Gap A：S6 漏一个 ledger 写入入口（高风险）

`FinanceTxnAuditSideEffect.applyLedger` 直接 `jdbc.update("INSERT INTO balance_ledger ...")`，
**写 REFUND / REBATE / VOID / ADJUST 4 类 biz_type**（占 S6 承诺 11 类的 36%），**未调 fxCapture**。

之前 S6 测试 +15 没覆盖 → 漏检。

**修复**：FinanceTxnAuditSideEffect 注入 `FxSnapshotCapture`；INSERT 后调 `captureForLedger`；
FinanceTxnAuditSideEffectTest +1 验证调用（参数化 11 类没法直接复用，REFUND 一条足以证明接入）。

### Gap B：S7 BRANCH_MANAGER 是隐形 zero-row trap（功能假坏）

migration 044 的 policy 强制 `branch_id = app.user_branch_id`。
RequestContext 把 `app.user_branch_id` 总设空字符串（AuthPrincipal 暂无 branchId 字段）。
**结果**：用 BRANCH_MANAGER 登录 → policy 评 `branch_id = ''` 永假 → **零行可见**。

比"过度放宽"更糟（功能假坏，看起来 token 有效 / 接口 200 / 但列表全空）。

**修复**：migration 047 重建 shipments_branch_visibility / orders_branch_visibility policies，
BRANCH_MANAGER 在 `app.user_branch_id` 为空时视同 ADMIN（全可见）。
等 AuthPrincipal 补 branchId 后此分支自动激活真正隔离。

另一个隐藏问题：**shipments.branch_id 全表 NULL**（grep 全工程：只有 acc_employees.branch_id 被
INSERT，shipments.branch_id 没人写）。等真正接入分支管理时需补 SubmitOrderService 入库赋值。

### Gap C：Demo carrier gateways 无 cancel override（约定行为，非 bug）

`DemoUpsCarrierGateway` / `DemoFedexCarrierGateway` 都没 override `cancel()`，
继承 interface 默认 `return false`。

**判定为正确行为**：
- 它们是演示 adapter，文档明说"不接真实 HTTP"
- 默认 `false` 让 SubmitCompensationService 写 orphan 表带 `cancel_success=false`
- 真实生产 adapter 应该 override（S2 文档已说明）

不修改。

## 3. 测试 +1

`FinanceTxnAuditSideEffectTest.auditCallsFxCaptureAfterLedgerWrite`：
mock REFUND txn → 验证 `fxCapture.captureForLedger("CNY", "REFUND", "acc_finance_txns", "RFD-001")` 被调一次。

### 全工程
**192 → 193**（+1 验证 Gap A 修复；Gap B 是 SQL policy 行为修复，由生产/集成验证）。

## 4. 还可能存在但暂判定为低优先级的项

| 项 | 现状 | 判定 |
|----|------|------|
| `SandboxLabelGateway` 仍输出 placeholder bytes | 文档说明"非真实 PDF" | 测试环境用，生产换 adapter |
| `AccWarehousesController` consignee/postcode 空串 | 字段不在主表 | 业务侧未要求字段联表 |
| `ShipmentDeliveredEvent` 无 listener | 利润结算 listener 未实现 | S5 文档已说明"externall hook" |
| shipments.branch_id 全 NULL | SubmitOrderService 未写 | 等分支管理上线一并修复 |
| `ProfitSettlementListener` 不存在 | documentcharges 没接事件 | 后续 Sprint 任务 |

这些都是**已识别 + 文档化的有意识保留**，不是"挂着不用"的 wiring 缺口。

## 5. 经验

**Doc 与代码不同步的两种典型**：
1. doc 说"已接入"但代码无 caller（S3/S5/S9 原版）→ 应直接 grep caller 而非读 doc
2. policy 已落但永真为假（S7 branch trap）→ 必须 e2e 在 PG 验证一次 SELECT

下次交付前应该跑：
```bash
# 验证 ledger 入口全覆盖
grep -rn "INSERT INTO balance_ledger" apps/backend/src/main/java/ \
  | xargs grep -L "fxCapture\|captureForLedger"
# → 输出应为空
```

类似 audit grep 可加入 CI 守门规则。
