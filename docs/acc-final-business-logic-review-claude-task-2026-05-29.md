# ACC 最终业务逻辑对比与 Claude 收尾任务

> 生成日期：2026-05-29  
> 目标读者：Claude / 后续实现 Agent  
> 当前对照基础：`docs/acc-logic-gap-claude-task-2026-05-28.md`、`docs/acc-logic-gap-claude-task-supplement-2026-05-29.md`  
> 当前重点：不要再做大范围重构，只修收尾缺口、红测和交付风险。

---

## 0. 给 Claude 的第一句话

当前 ACC 迁移已经不是“缺主业务逻辑”的阶段。订单、报价、Submit、取号、面单、费用、账单、资金流水、审核副作用、轨迹聚合、配载联动、数据权限、`shipment_order_links` 都已经有实现，并且补充任务书 S1-S9 的大部分内容已经接入主路径。

**你现在的任务不是继续发散补新模块，而是把当前工作区修到可交付状态：**

1. 修掉当前全量测试失败。
2. 处理未提交的 `047_branch_visibility_fallback.sql` 是否符合业务权限口径。
3. 确认最新业务逻辑路径已经全部有测试和 comparison 文档。
4. 最后跑完整后端测试，必要时跑前端 typecheck，再给出最终交付结论。

---

## 1. 当前最新状态

### 1.1 已完成并接入主路径的业务逻辑

| 业务域 | 当前状态 | 关键证据 |
|---|---|---|
| 客户 API 下单 / Submit | 已接 `RateEngine`、预扣余额、AR/AP 费用行、渠道取号、失败补偿、`shipment_order_links` | `CustomerApiService`、migration 041/045 |
| 报价 / 计费 | 客户价、组价、渠道限制、偏远、多段、关键词附加费、最低计费、PER_CBM 等已补 | migration 042、`RateEngineTests` |
| 关键词附加费 | 已从“独立方法”接入 Submit 主路径，进入预扣 + AR 行 | `docs/acc-comparison-followup-wirings-2026-05-29.md` |
| 费用 / 账单 / 利润 | documentcharges 与 acc profits 口径对齐，真实利润含 adjustments/reparation | `DocumentChargeRepository`、`AccProfitsController` |
| 资金流水 | `balance_ledger` 覆盖多类金额动作，fx 快照框架已补 | migration 037/043 |
| 审核副作用 | `FinanceTxnAuditSideEffect` 支持审核入账、反审冲正 | `FinanceTxnAuditSideEffect` |
| 渠道取号 | 数据驱动 provider 路由、Sandbox adapter、evidence 落库、cancel 补偿 | migration 039/040/041 |
| 面单 | provider evidence 落库，ZPL/PNG/JPG 转 PDF 已接入 `LabelService` | `LabelFormatConverter`、`LabelService` |
| 轨迹 | 多源轨迹聚合，内部/客户视角分层 | `TrackingAggregator` |
| 配载 / 转运 / 派送 | `StowageStateMachine` 已接 stowage、dispatch、transit；transit 通过 `acc_transit_items` fanout | migration 046 |
| 数据权限 | 已加 user 级 RLS：SALESMAN / BRANCH_MANAGER / ADMIN / FINANCE | migration 044 |
| 强关联 | `shipment_order_links` 已落地，支持 SPLIT/MERGE/REPLACE 扩展 | migration 045 |

### 1.2 最新收尾 commit

最新关键 commit：

```text
98da01a fix: S3/S5/S9 收尾接入（补充任务完整收口）
1721516 feat(labels): ZPL → PDF / 图片 → PDF 转换（任务 S9 + 9 项补充收尾）
825420f feat(orders): shipment_order_links 强关联表（任务 S8，方案 A）
```

`98da01a` 已处理之前挂起的三件事：

- S3：`rateEngine.applyKeywordSurcharges` 已进入 Submit 主路径。
- S5：`onTransitInTransit` 不再 no-op，新增 `acc_transit_items`。
- S9：`LabelFormatConverter.convertToPdfIfNeeded` 已在 `LabelService.generate` 调用。

---

## 2. 当前阻塞：测试红

### 2.1 现象

当前 `./mvnw -q test` 失败：

```text
Tests run: 193, Failures: 1, Errors: 0

FinanceTxnAuditSideEffectTest.auditCallsFxCaptureAfterLedgerWrite
Wanted but not invoked:
fxSnapshotCapture.captureForLedger(...)
Actually, there were zero interactions with this mock.
```

### 2.2 涉及文件

当前未提交修改：

```text
M apps/backend/src/main/java/com/xqt/saas/acc/FinanceTxnAuditSideEffect.java
M apps/backend/src/test/java/com/xqt/saas/acc/FinanceTxnAuditSideEffectTest.java
?? db/migrations/047_branch_visibility_fallback.sql
```

### 2.3 根因判断

`FinanceTxnAuditSideEffect.applyLedger()` 只有在找到资金账户后才会写 ledger 和调用 fx：

```java
String accountId = findOwnerAccount(side, ownerId, currency);
if (accountId == null) return;
...
fxCapture.captureForLedger(...)
```

失败测试里 mock 很可能没有匹配 `findOwnerAccount()` 的真实参数数量。真实调用是：

```java
jdbc.queryForObject(sql, String.class, side, ownerId, currency, currency, currency)
```

但测试写的是：

```java
when(jdbc.queryForObject(contains("FROM financial_accounts"), eq(String.class),
    any(), any(), any())).thenReturn("acct-1");
```

只 mock 了 3 个参数，导致 `findOwnerAccount()` 没命中，返回 null，后续 `fxCapture.captureForLedger` 自然不会执行。

### 2.4 修复要求

修测试即可，不要改业务代码绕过。

建议改成：

```java
when(jdbc.queryForObject(contains("FROM financial_accounts"), eq(String.class),
    any(), any(), any(), any(), any())).thenReturn("acct-1");
```

然后重新跑：

```bash
cd apps/backend
./mvnw -q test
```

验收：193 项全绿。

---

## 3. 当前风险：047 branch fallback 需要业务决策

### 3.1 047 做了什么

未提交 migration：

```text
db/migrations/047_branch_visibility_fallback.sql
```

它修改了 migration 044 的 `BRANCH_MANAGER` 权限策略：

- 044：`BRANCH_MANAGER` 必须 `branch_id = app.user_branch_id`。
- 047：如果 `app.user_branch_id` 为空，则 `BRANCH_MANAGER` 可看全 tenant。

文档注释说：

```text
AuthPrincipal 暂无 branchId，BRANCH_MANAGER 会一行都查不到。
修复：BRANCH_MANAGER + branch context 为空时 → 视同 tenant 全可见。
```

### 3.2 业务含义

这不是纯技术修复，而是权限口径选择：

| 方案 | 行为 | 风险 |
|---|---|---|
| 保留 044 严格策略 | 无 branchId 的 BRANCH_MANAGER 看不到数据 | 功能可能“假坏” |
| 接受 047 fallback | 无 branchId 的 BRANCH_MANAGER 看全 tenant | 分公司隔离不严格 |
| 更好方案 | 补 `AuthPrincipal.branchId`，没有 branchId 则拒绝登录或降级为普通角色 | 最接近 ACC 权限 |

### 3.3 Claude 要做的事

不要直接默认提交 047。先做一个明确决策：

1. 如果用户/产品接受“没 branchId 时分公司经理看全租户”，可以保留 047，但要在 comparison 文档里写明这是临时 fallback。
2. 如果要严格复刻 ACC 分公司隔离，应该撤掉 047，改为补 `AuthPrincipal.branchId` + 登录/用户表 branch 字段。
3. 如果当前只是演示环境，为了不阻断演示，可以保留 047，但必须在最终报告标为“演示 fallback，生产前关闭”。

推荐：**不要把 047 当成最终权限实现**。最好后续新增：

```text
users.branch_id
AuthPrincipal.branchId
RequestContext.setTenant(...) set local app.user_branch_id = actual branch id
```

---

## 4. 最终业务逻辑对比结论

### 4.1 已经可以认为复刻的主业务路径

以下 ACC 核心逻辑已经在新系统有对应实现，并且大部分有测试或 comparison case：

1. 客户下单：`PreOrder / Submit / Modify / Query / Status`
2. 第三方推单：`sumy.php`
3. 电子秤：`Scale.php`
4. 报价：客户价、组价、渠道限制、偏远、多段、关键词附加费、最低计费、体积计费
5. 渠道取号：数据驱动 provider + evidence + cancel
6. 面单：PDF/ZPL/图片统一输出 PDF
7. 费用：AR/AP 拆行、费用确认、作废
8. 账单：客户账单、供应商账单、收款、付款
9. 资金流水：balance ledger + before/after balance
10. 审核：audit/undo + side effect + 反审冲正
11. 利润：AR - AP + adjustments - reparation
12. 轨迹：多源聚合 + 客户/内部分层
13. 配载：stowage/transit/dispatch 状态机联动
14. 数据权限：tenant + salesman/branch/user role RLS
15. 强关联：shipment_order_links

### 4.2 仍不是“代码未复刻”的剩余事项

这些不是当前 Claude 要继续发散实现的业务逻辑缺口，而是外部/配置/运营边界：

| 项 | 状态 |
|---|---|
| 真实 UPS/FedEx/label provider | 需要真实凭证；当前 Sandbox adapter 可替换 |
| 旧 ACC 真实样本回放 | 需要旧库/真实报文 |
| 37 个主数据 tab 是否审核 | 需要运营策略 |
| 仓库 IoT push | 用户已决定暂缓 |
| docker-compose migration 挂载 | 运维 runbook 范围 |
| `strict-quote` / `strict-gateway` prod 默认 | 运维配置范围 |

---

## 5. Claude 的具体收尾任务

### 任务 C1：修红测

修 `FinanceTxnAuditSideEffectTest.auditCallsFxCaptureAfterLedgerWrite` 的 mock 参数。

验收：

```bash
cd apps/backend
./mvnw -q test
```

必须 193/193 全绿。

### 任务 C2：决定 047 是否保留

如果保留：

- 新增 comparison 文档：

```text
docs/acc-comparison-branch-visibility-fallback-2026-05-29.md
```

内容必须写清：

- 为什么 AuthPrincipal 当前没有 branchId。
- 为什么 BRANCH_MANAGER 空 branchId 时选择放行。
- 生产前如何替换为严格 branch isolation。

如果不保留：

- 删除 `047_branch_visibility_fallback.sql`。
- 改为实现 `AuthPrincipal.branchId` 真实注入，或者留下文档任务。

### 任务 C3：补最终补报

新增：

```text
docs/acc-migration-final-report-supplement-2026-05-29.md
```

必须包含：

- S1-S9 已完成清单。
- 最新 migration 041-046/047 清单。
- 最新测试数。
- 当前剩余外部依赖。
- 生产部署前 runbook：所有 migrations、strict 开关、真实 provider 凭证。

### 任务 C4：清理工作区

最后保证：

```bash
git status --short
```

只剩用户明确不提交的旧文档/交付包，不能有未提交 Java 测试失败相关文件。

---

## 6. 不要做什么

| 不要做 | 原因 |
|---|---|
| 不要再新增大模块 | ACC 主路径已经闭环，现在是收尾 |
| 不要绕过失败测试 | 当前失败是 mock 参数问题，不是业务代码问题 |
| 不要把 047 悄悄当最终权限方案 | 这是业务权限口径，必须明示 |
| 不要改已交付历史报告原文 | 新增 supplement 即可 |
| 不要接真实 UPS/FedEx | 没有凭证，继续用 Sandbox adapter |
| 不要重新设计 IoT 仓库接口 | 用户已决定暂缓 |

---

## 7. 最终验收命令

后端：

```bash
cd apps/backend
./mvnw -q test
```

前端如有改动：

```bash
cd apps/web
npm run typecheck
```

代码搜索：

```bash
rg "TODO|FIXME|no-op|未实现|占位" apps/backend/src/main/java
```

允许保留的命中：

- `SandboxCarrierGateway` / `NoopCarrierGateway` / `SandboxLabelGateway`：真实凭证未接，属于外部依赖。
- `estimatePrepayAmount`：dev/demo fallback，生产通过 `RATES_STRICT_QUOTE=true` 禁用。

不应再出现：

- transit no-op
- Label converter 未接入
- keyword surcharge 只实现未调用

---

## 8. 一句话交付目标

把当前状态从：

> “ACC 业务逻辑基本复刻，但工作区有红测和权限 fallback 风险”

收口到：

> “ACC 主业务逻辑已路径闭环、测试全绿、权限 fallback 已明示，剩余全部是外部凭证 / 运维配置 / 运营决策。”

