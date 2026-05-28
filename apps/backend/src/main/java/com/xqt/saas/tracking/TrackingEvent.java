package com.xqt.saas.tracking;

import java.time.OffsetDateTime;

/**
 * 多源轨迹聚合后的统一事件 DTO。
 *
 * 对应 ACC 旧系统的 Express_Process / Transit_Process / Stowage_Process /
 * Online_TrackNo / Express_TrackNo 五张表的事件，按 event_time 排序成一条时间线。
 *
 * visibility = PUBLIC 时客户可见；INTERNAL 时仅内部可见（含 operator 等敏感字段）。
 */
public record TrackingEvent(
    String source,           // 来源表标识：tracking_events / stowage_steps / dispatches / transits
    String sourceId,         // 来源行 id（便于反查原始数据）
    OffsetDateTime eventTime,
    String statusCode,       // 标准化状态码
    String location,         // 位置（如有）
    String message,          // 公开消息
    String operator,         // 操作人（仅 INTERNAL 可见）
    String internalRemark,   // 内部备注（仅 INTERNAL 可见）
    Visibility visibility
) {
    public enum Visibility { PUBLIC, INTERNAL }

    /** 转成对外公开视图（剥离 operator / internalRemark）。 */
    public TrackingEvent toPublic() {
        if (visibility == Visibility.PUBLIC) return this;
        return new TrackingEvent(source, sourceId, eventTime, statusCode, location,
            message, null, null, Visibility.PUBLIC);
    }
}
