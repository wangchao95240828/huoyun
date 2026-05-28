# Comparison Case：配载状态机自动联动（任务 S5）

> 任务：docs/acc-logic-gap-claude-task-supplement-2026-05-29.md §3 任务 S5 + 任务书 §3.9

## 1. 问题

ACC 的 `Stowage_Process` 在装箱单 / 转运 / 派送的状态变更时会**自动**：
- 写一条 milestone（系统内部轨迹节点）
- 推进关联 shipment 的状态
- 妥投时触发利润结算

新系统 `AccStowagesController / AccDispatchesController` 都是裸 CRUD，状态变更不联动其他模块 → 数据失同步，前端轨迹缺节点，财务模块拿不到 DELIVERED 信号无法做 AR/AP close。

## 2. 实现

### `com.xqt.saas.stowage.StowageStateMachine`
集中编排 4 类状态推进的副作用，每个方法 `@Transactional` + DB 异常吞掉（业务连续性优先）：

| 方法 | 触发 | shipment 关联 | tracking_events | shipments.status |
|------|------|---------------|-----------------|------------------|
| `onStowageConfirmed` | stowages.status=CONFIRMED | `cartons.stowage_id` | STOWAGE_CONFIRMED / IN_TRANSIT | DRAFT/ORDERED/IN_WAREHOUSE/MEASURED/BOOKED → IN_TRANSIT |
| `onTransitInTransit` | transits.status=IN_TRANSIT | （暂无 link 表） | no-op | no-op |
| `onDispatchOutForDelivery` | dispatch.status=PICKED | customer×pick_date±3 | DISPATCH_OUT / OUT_FOR_DELIVERY | 不改 |
| `onDispatchDelivered` | dispatch.status=DONE | customer×pick_date±3 | DELIVERED / DELIVERED | IN_TRANSIT/OUT_FOR_DELIVERY/BOOKED → DELIVERED |

妥投同时 publish `ShipmentDeliveredEvent`，documentcharges 监听做利润结算。

### `ShipmentDeliveredEvent`
record(tenantId, shipmentId, shipmentNo, deliveredAt)。

### 接入 controller
- `AccStowagesController.update` 在 UPDATE 后判断 `allowed.status == "CONFIRMED"` 调 `onStowageConfirmed`
- `AccDispatchesController.update` 在 UPDATE 后判断 `allowed.status == "PICKED"` 或 `"DONE"` 调对应方法
- 失败吞 `DataAccessException`，不阻断 controller 主路径

### 关键设计

**`advanceShipmentStatus` 防回退**：仅当当前 status 在 fromStatuses 白名单内才推进，避免把已 DELIVERED 的 shipment 回退到 IN_TRANSIT。

**dispatch ↔ shipment 关联**：dispatch 表无直接 shipment_id 字段，按 `customer_id × pick_date ±3 天` 模糊匹配（同 TrackingAggregator 策略）。生产环境若需精确，应补 `acc_dispatch_items` 关联表。

**transit no-op**：当前 acc_transits 无 link 表（PRD 阶段产物，未补 items），暂不能 fanout 到 shipment 级。占位方法签名预留，方便未来补 `acc_transit_items` 后无需改 controller。

## 3. 测试（StowageStateMachineTest 6 项全过）

1. `stowageConfirmedFanoutsTrackingEventsAndAdvancesShipments`：2 个 shipment fanout，写 2 条 tracking_events + 2 条 shipments UPDATE
2. `dispatchDeliveredWritesDeliveredEventAndPublishesAppEvent`：写 DELIVERED 事件 + UPDATE shipments + publish ShipmentDeliveredEvent
3. `dispatchOutForDeliveryWritesEventButDoesNotChangeShipmentStatus`：PICKED 阶段写 OUT_FOR_DELIVERY 事件，不动 shipments
4. `transitInTransitIsNoOp`：transit 调用返回 0，零 DB 操作
5. `databaseFailureDoesNotPropagateException`：DB 异常吞掉不抛
6. `shipmentDeliveredEventCarriesTenantAndShipment`：多 shipment fanout 时事件 payload 正确

### 全工程
**160 测试全过**（+6 vs S4 结束时 154）。

## 4. 利润结算 listener 接入指南

```java
@Component
class ProfitSettlementListener {
    @EventListener
    @Async
    void onShipmentDelivered(ShipmentDeliveredEvent ev) {
        // 1. 查 charges where shipment_id=ev.shipmentId
        // 2. 计算 profit = SUM(AR) - SUM(AP)
        // 3. 写 acc_shipment_profits + balance_ledger
    }
}
```

documentcharges 模块直接 `@EventListener` 监听即可，无需改 StowageStateMachine。

## 5. ACC 对照

| ACC | 新系统 |
|-----|--------|
| `Stowage->confirm()` 内联 INSERT Express_Process | `StowageStateMachine.onStowageConfirmed` |
| `Stowage->confirm()` 改 Express.Status='Shipped' | `advanceShipmentStatus(IN_TRANSIT)` |
| `Dispatch->done()` 改 Express.Status='Done' | `advanceShipmentStatus(DELIVERED)` |
| ACC 利润结算 trigger | Spring `ShipmentDeliveredEvent` |
| Transit 自动轨迹（ACC 也只在有具体 packing list 时生效） | no-op + 文档化前置依赖 |
