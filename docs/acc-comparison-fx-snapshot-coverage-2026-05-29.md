# Comparison Case：fx_rate_snapshots 覆盖 11 类资金动作（任务 S6）

> 任务：docs/acc-logic-gap-claude-task-supplement-2026-05-29.md §3 任务 S6 + 任务书 §3.4#6

## 1. 问题

migration 030 已建 `fx_rate_snapshots` 表，但写入是手工调用 `DocumentChargeRepository.insertFxSnapshot`，
**11 类 balance_ledger biz_type**（PREPAY / PREPAY_RELEASE / RECEIPT / PAYMENT / REFUND /
ADJUST / REBATE / FINE / REPARATION / VOID / FX_DIFF）的实际资金动作里，多数路径漏写，
事后无法对账重现"那条 USD 流水当时的汇率是多少"。

## 2. 实现

### migration 043
- `tenants.base_currency char(3) not null default 'CNY'`：租户基准币种
- `fx_rate_snapshots` 加 `biz_type / source_type / source_ref` 三列：精确反查到触发 snapshot 的 ledger 动作
- `idx_fx_snapshots_biz` 部分索引：仅索引 biz_type 非空行

### `com.xqt.saas.finance.FxSnapshotCapture`
集中拦截器组件，签名 `captureForLedger(tenantId, currency, bizType, sourceType, sourceRef)`：
1. 查 `tenants.base_currency`，找不到 fallback CNY
2. `currency == base` → no-op 返回 null
3. 查最新 `exchange_rates` 行（from=ledger.currency, to=base）
4. 命中 → 写 snapshot rate + source=`AUTO_LEDGER`
5. 未命中 → 写 snapshot rate=`1` + source=`MISSING_RATE`（让审计能看到漏配）
6. DB 异常静默吞掉（fx 失败不阻断 ledger 主路径）

### 接入 ledger 唯一入口
- `DocumentChargeRepository.recordBalanceLedger`：INSERT balance_ledger 后调 `fxCapture.captureForLedger(...)`
- `CustomerApiRepository.recordBalanceLedger`：同步处理（客户 API submit 时 PREPAY 也走这里）

两条 ledger 写路径都加了同一行 `fxCapture.captureForLedger(...)`，11 类 biz_type 自动覆盖，零业务侧改动。

## 3. 测试（FxSnapshotCaptureTest 15 项全过）

- `sameCurrencyNoSnapshot`：tenant.base = ledger.currency 时不写 snapshot
- `foreignCurrencyWritesSnapshotWithCurrentRate`：USD ledger + CNY base → snapshot rate=7.2500, source=AUTO_LEDGER
- `missingRateFallsBackToOneWithMissingRateSource`：exchange_rates 表无配置 → rate=1, source=MISSING_RATE
- `dbFailureReturnsNullDoesNotThrow`：base_currency 查询失败 → 返回 null 不抛
- `all11BizTypesTriggerSnapshotForForeignCurrency` 参数化 ×11：PREPAY/PREPAY_RELEASE/RECEIPT/
  PAYMENT/REFUND/ADJUST/REBATE/FINE/REPARATION/VOID/FX_DIFF 各发一条 USD ledger，
  验证 snapshot biz_type 字段精确记录

### 全工程
**175 测试全过**（+15 vs S5 结束时 160）。

## 4. 设计取舍

### 为什么"事后捕获"而非"事前转换"
fx 快照只是 **审计证据**，不参与金额计算：
- ledger 永远按原币 currency 记录 amount
- 报表/对账需要时按 snapshot.rate 二次换算
- 避免"PREPAY 写入时已换算" + "结算时再换算"造成的双倍误差

### 为什么 MISSING_RATE 写 rate=1
- 不写 snapshot → 事后无法察觉漏配
- 写 0 → 容易让后续报表 SUM 误算
- 写 1 + source=MISSING_RATE → 运维可以 SQL `WHERE source='MISSING_RATE'` 一次性看到所有漏配，
  补汇率行后用 service_role 批量 UPDATE snapshot 即可

### 为什么 DB 异常吞掉
fx_rate_snapshots 是 **可重建的派生数据**，主 ledger 才是源真相。如果 fx 失败抛错，
会让用户的 PREPAY/Submit 失败 → 用户体验差。运维通过 LEFT JOIN 找缺 snapshot 的 ledger 重建即可。

## 5. 接入新资金动作的步骤

任何模块新增资金动作时只需：
1. 走 `recordBalanceLedger` 入口（不要直接 INSERT balance_ledger）
2. 传正确的 `bizType` 和 `currency`
3. fx 快照**自动**捕获，无需额外代码

ACC 老系统每个 Customer_Balance_History 动作都要手工调 `getFxRate()` 函数；新系统抽象到 DI 组件，
开发者只需关心"我要写一笔流水"，"汇率快照"是基础设施层透明完成的。
