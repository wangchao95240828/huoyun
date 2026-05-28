# Comparison Case：Submit 失败业务补偿（任务 S2）

> 任务：docs/acc-logic-gap-claude-task-supplement-2026-05-29.md §3 任务 S2 + 任务书 §3.1#4

## 1. 问题

ACC 原系统 Online 插件取号成功 + 本地 DB 写入失败时无补偿——provider 那边的子单号
仍存在，DB rollback 后变成"幽灵单号"。

新系统统一抽象 `CarrierGateway.cancel(tenantId, masterTrackingNo)` 默认 no-op，
真实 provider 实现真正的 cancel HTTP 调用；DB 后续 insert 失败时
`SubmitCompensationService` best-effort 调 cancel + 写 `acc_orphan_tracking_nos`
留痕。

## 2. 实现

### migration 041
`acc_orphan_tracking_nos` 表含 tenant_id / provider_code / master_tracking_no /
原 request+response evidence / cancel 尝试结果 + 时间 + RLS 策略。

### CarrierGateway 接口
新增 `default boolean cancel(tenantId, masterTrackingNo) { return false; }`。
Sandbox 实现：维护 `ACTIVE` 单号集合，submit 时加入，cancel 时移除返回是否成功。

### SubmitCompensationService
- `@Transactional(REQUIRES_NEW)` 隔离外层正在回滚的事务，确保 orphan 表写入提交
- 1. best-effort 调 `gateway.cancel()`
- 2. 用 `app.service_role='true'` 绕 RLS（因为外层事务回滚后 tenant 上下文可能丢失）
- 3. INSERT orphan 表，记录 cancel 结果（含异常 message）
- 4. orphan insert 本身失败时不再抛出（best-effort）

### CustomerApiService.submitOrder
取号成功 → `insertCarton` 用 try/catch 包裹：抛错时调
`compensationService.compensateOrphanTracking(...)` 再 rethrow 让外层事务回滚。

## 3. 测试

### SubmitCompensationServiceTest（5 项全过）
1. cancel 成功 → orphan 表写 cancel_success=true
2. cancel 失败 → orphan 表写 cancel_success=false（含 null customer_ref / null request）
3. cancel 抛异常 → orphan 表仍能写入 + cancel_response 含 exception message
4. orphan insert 失败时不抛错（best-effort）
5. Sandbox cancel 反向移除 ACTIVE 单号（同一 master 第二次 cancel 返回 false）

### 全工程
146 测试全过（+5 新增）。

## 4. 关键设计

- **REQUIRES_NEW 事务隔离**：外层事务正在回滚的瞬间，新事务能继续写 orphan 表
- **service_role 绕 RLS**：tenant 上下文可能在外层事务回滚后丢失，service_role 让 orphan insert 不被 RLS 拦截
- **best-effort 三层降级**：
  1. cancel HTTP 失败 → orphan 仍写入 + cancel_response 记 exception
  2. orphan insert 失败 → 整体不抛错（运维通过日志+审计后续清理）
  3. 任何环节失败都不影响外层抛出原始错误给客户

## 5. 真实生产 adapter 接入指南

接 UPS/FedEx 真实 adapter 时只需：
1. 实现 `CarrierGateway.cancel(tenantId, masterTrackingNo)` 调真实 cancel HTTP
2. 失败时让方法**返回 false** 而非抛异常（否则会进 catch 路径，记 exception 字段更明显）
3. 启动时打开 `app.carrier.strict-gateway=true` 让数据驱动路由生效

补偿流程会自动接入，无需改 SubmitCompensationService 或 CustomerApiService。
