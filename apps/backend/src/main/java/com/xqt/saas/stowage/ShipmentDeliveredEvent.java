package com.xqt.saas.stowage;

import java.time.OffsetDateTime;

/**
 * 任务 S5：妥投事件。
 *
 * 由 StowageStateMachine.onDispatchStatusChanged(DONE) 发布；
 * documentcharges 模块监听后做利润结算 / AR-AP close。
 */
public record ShipmentDeliveredEvent(
    String tenantId,
    String shipmentId,
    String shipmentNo,
    OffsetDateTime deliveredAt
) {
}
