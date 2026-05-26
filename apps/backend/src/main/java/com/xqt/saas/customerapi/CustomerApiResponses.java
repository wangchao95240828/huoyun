package com.xqt.saas.customerapi;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class CustomerApiResponses {
    private CustomerApiResponses() {
    }

    public record BalanceLine(
        String currency,
        String accountName,
        BigDecimal balance
    ) {
    }

    public record BalanceList(
        String customerCode,
        List<BalanceLine> data
    ) {
    }

    public record PreOrderResult(
        String orderId,
        String orderNo,
        String status,
        String customerCode
    ) {
    }

    /** 对应 ACC act=Submit 响应：返回新主单号、子单号、状态。 */
    public record SubmitResult(
        String orderId,
        String orderNo,
        String customerRef,
        String shipmentId,
        String shipmentNo,
        String trackingNo,
        String carrierMasterTrackingNo,
        String status
    ) {
    }

    /** 对应 ACC act=Cancel 响应。 */
    public record CancelResult(
        String orderNo,
        String customerRef,
        String previousStatus,
        String status,
        boolean shipmentMarked,
        java.math.BigDecimal refundedAmount,
        int refundedChargeCount
    ) {
    }

    /** 对应 ACC act=PreSubmit：预试算 + 余额检查，但不取号、不扣款。 */
    public record PreSubmitResult(
        String orderNo,
        String channelCode,
        String gatewayKey,
        java.math.BigDecimal estimatedAmount,
        String currency,
        java.math.BigDecimal currentBalance,
        java.math.BigDecimal balanceAfter,
        boolean canSubmit,
        List<String> blockers
    ) {
    }

    /**
     * 对应 ACC act=Status 响应：{ Express: { No: statusCode } }。
     * 这里同时返回新字符串状态，便于新客户端按强类型解析。
     */
    public record StatusEntry(
        String no,
        String status,
        int statusCode
    ) {
    }

    public record StatusList(
        List<StatusEntry> express
    ) {
    }

    /**
     * 对应 ACC act=Query 单条订单结构，字段名沿用旧响应方便契约测试。
     */
    public record OrderDeclareItem(
        String name,
        String cnName,
        String hsCode,
        String origin,
        BigDecimal price,
        BigDecimal quantity,
        String note
    ) {
    }

    public record OrderDetail(
        String no,
        String trackNo,
        List<String> trackNoList,
        String customerRef,
        String status,
        int statusCode,
        String country,
        String productCode,
        String channelCode,
        BigDecimal weight,
        Integer piece,
        BigDecimal volume,
        BigDecimal declaredValue,
        String declaredCurrency,
        String materialsEN,
        String materialsCN,
        String remark,
        String addTime,
        Map<String, Object> receiver,
        Map<String, Object> shipper,
        Map<String, Object> shipTo,
        List<OrderDeclareItem> declare
    ) {
    }

    public record OrderDetailList(
        Map<String, OrderDetail> express
    ) {
    }

    /**
     * 对应 ACC act=Product / act=Channel：返回 { Name, Code, Logistics }。
     */
    public record ChannelInfo(
        String code,
        String name,
        String lane,
        String lastMileMethod,
        boolean active
    ) {
    }

    public record ChannelList(
        List<ChannelInfo> data
    ) {
    }

    /**
     * 对应 ACC act=Track / api/Track.php 的单条轨迹事件，字段保留旧名。
     */
    public record TrackingEvent(
        String time,
        String location,
        String activity,
        String trackNo,
        String source,
        String normalizedStatus
    ) {
    }

    /**
     * 对应 ACC act=Track 的单条订单聚合结果。
     */
    public record TrackingDetail(
        String referenceNo,
        String trackNo,
        String trackStatus,
        String trackMsg,
        String trackTime,
        List<TrackingEvent> track
    ) {
    }

    public record TrackingList(
        Map<String, TrackingDetail> data
    ) {
    }
}
