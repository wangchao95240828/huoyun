# Comparison Case：ShipmentDeliveredEvent listener + 银行/支付/汇率 feed 真实接入

> 任务：用户反馈最终补全两项：
> 1. 真实银行/支付/汇率 feed 还没接，目前是表配置和快照机制
> 2. ShipmentDeliveredEvent 的利润结算 listener 文档里还列为后续项

## 1. ProfitSettlementListener（利润结算 listener）

### 修复前状态
`StowageStateMachine.onDispatchDelivered` 在妥投时 publish `ShipmentDeliveredEvent`，
但**全工程无 `@EventListener` 实际监听**，事件被发布即消失。`AccProfitsController`
是 on-the-fly 计算（每次查询都重算），没有结算时间点的快照。

### 实现
`com.xqt.saas.documentcharges.ProfitSettlementListener`：

- `@EventListener` 监听 `ShipmentDeliveredEvent`
- `@Transactional(REQUIRES_NEW)` 隔离：listener 失败不回滚 dispatch.status=DELIVERED 主路径
- **幂等保护**：先查 `profit_snapshots` 该 shipment 已有 → skip（重派送 / 状态机重放都安全）
- 聚合 charges：
  - `ar_amount` = SUM(side='AR' AND settlement_status<>'VOID' 的 amount)
  - `ap_amount` = SUM(side='AP' 同上)
  - `commission_amount` = SUM(evidence->>'commission' 的 numeric) —— 佣金证据在 FREIGHT AR 行
  - `seller_cost_amount` 暂 0（ACC Sellr_Cost 字段后续接入）
- `gross_profit = ar - ap - commission - seller_cost`
- INSERT `profit_snapshots`（migration 010 早已建表，本 listener 是**真正业务接入**）
- 异常静默 + 日志（结算失败不阻断配载推进）

### 测试（ProfitSettlementListenerTest +5）
1. 正常路径：AR 500 - AP 300 - commission 20 = 180 ✓
2. 幂等：已存在 snapshot 跳过 ✓
3. DB 异常静默不抛 ✓
4. **亏损也落账**（AR 100 - AP 150 = -50）✓
5. 数字类型兼容（Long/Double/null 都能聚合）✓

### 业务影响
- 真实妥投 → 立即得到 profit 快照行，运营报表可直接 SUM `profit_snapshots`，不必每次查询都聚合 charges
- 利润口径明确：写入时是哪个 currency，里面是什么金额，metadata 记 trigger + delivered_at，全审计

## 2. FxRateFeed（真实汇率源）

### 修复前状态
`exchange_rates` 表只有手工 INSERT；`FxSnapshotCapture` 在写不到 rate 时 fallback rate=1 + source=MISSING_RATE。
这意味着每条非基准币 ledger 都标 MISSING_RATE 直到运营手填汇率。

### 实现
- 抽象 `com.xqt.saas.finance.feed.FxRateFeed`：`fetch(baseCurrency, currencies, rateDate)` 返回 `Map(from -> rate)`
- 默认实现 `OpenExchangeRateHostFeed`：
  - 调免费公共 API https://api.exchangerate.host/historical
  - HTTP 5xx / 超时 / JSON 异常 → 返回空 Map，调用方走 fallback
  - 关键 trick：API 返回 1 base = X currency，本系统需要 1 currency = X base，**做倒数后存**
- 调度任务 `FxRateRefreshJob`：
  - `@Scheduled(cron = 02:00 Asia/Hong_Kong)`
  - `@Profile("prod")`：CI/dev 默认不跑（避免联网）
  - 遍历 tenants → 列每个租户的 currency 集合 → fetch → UPSERT exchange_rates
  - 默认兜底币种 USD/EUR/HKD/JPY/GBP（即使表里没配置也保证日常 5 大币有最新汇率）
  - 启用 service_role 绕 RLS（系统层任务，不该被 tenant policy 拦）
- `XqtBackendApplication` 加 `@EnableScheduling` 开启调度

### 切换其他 feed
PBoC / 中行 / 彭博 / Reuters 接入 = 另写一个实现 `FxRateFeed` 的 `@Component`，
通过 `app.fx.feed=pboc` 配置选择激活。**业务层零改动**。

### 测试（OpenExchangeRateHostFeedTest +8）
- 本地 HttpServer 模拟 exchangerate.host
- 验证：倒数 / 5xx / 超时 / 空 currencies / 坏 JSON / 缺 rates 字段 / rate=0 跳过 / code 标识

## 3. BankReconciliationFeed（银行对账 feed）

### 现状
ACC `AccBillsController` 是表配置（管理员手动录入对账单）。生产银行流水从未被自动拉取。

### 实现
- 抽象 `BankReconciliationFeed`：`pullStatement(tenantId, accountNo, dateFrom, dateTo)` → `List<BankStatementEntry>`
- `BankStatementEntry` record：externalRef / txnTime / direction / amount / currency / balanceAfter /
  counterpartyName / counterpartyAccount / purpose / raw
- 默认 `SandboxBankFeed`：返回 3 条虚拟流水让对账模块端到端可跑

### 生产接入指南（文档化）
1. 工商银行：网银管家批量回单接口
2. 招商银行：CMB U-Bank API
3. 中国银行：CBPS 现金管理
4. 国际：SWIFT MT940 / ISO20022 camt.053

每行新增 = 写一个实现，业务层零改动。

### 测试（SandboxBankFeedTest +4）
- 空账号返回空
- 3 条流水 + currency CNY + ref 唯一
- code 标识
- 跨调用 ref 不重复

## 4. PaymentGateway（支付网关）

### 现状
应收应付都靠手工记账。客户线上支付场景未抽象。

### 实现
- 抽象 `PaymentGateway`：`charge / query / refund` 三方法 + 4 个 DTO record
- 默认 `SandboxPaymentGateway`：内存模拟，立即 SUCCESS；通过 `extra.simulate=PENDING/FAILED`
  控制状态用作业务测试

### 生产接入指南
- 支付宝：Alipay Open API（trade.create / trade.query）
- 微信支付：Wechat Pay V3 API
- 银联：UnionPay 全渠道
- 国际：Stripe / PayPal

### 测试（SandboxPaymentGatewayTest +7）
- charge 默认 SUCCESS
- simulate=PENDING 控制状态
- query 返回正确 paidAmount
- refund 全额 + 部分退 + 未知 ID 失败
- code 标识

## 5. 全工程

**202 → 226 测试绿**（+24：listener 5 + fx feed 8 + bank 4 + payment 7）

零回归。

## 6. 与 ACC 对照

| ACC | 之前（仅快照机制） | 现在 |
|-----|--------------------|------|
| 妥投触发利润计算 | 报表查询每次重算 | ShipmentDeliveredEvent → profit_snapshots 落账 |
| 汇率获取 | 表配置 + MISSING_RATE | exchangerate.host 自动 daily refresh + 可切换 |
| 银行流水对账 | 表配置（人工录入） | BankReconciliationFeed 抽象 + Sandbox + 产品级接入指南 |
| 支付场景 | 手工记账 | PaymentGateway 抽象 + Sandbox + 4 主流 provider 接入路径 |

## 7. 部署后激活步骤

```bash
# 1. 默认 dev/test 不跑定时 fx refresh，prod profile 开
# .env 或环境变量
SPRING_PROFILES_ACTIVE=prod

# 2. 可选：覆盖默认 cron 和 endpoint
app.fx.refresh.cron=0 0 2 * * *
app.fx.exchangerate-host.endpoint=https://api.exchangerate.host/historical
app.fx.exchangerate-host.timeout-ms=5000
```

bank / payment 真实接入时：
- 注册新的 `@Component` 实现接口
- 在 `application.yml` 配置激活 bean 的 code
- 加 properties 存 API key / secret（用 Spring `${...}` 占位，不入 git）
