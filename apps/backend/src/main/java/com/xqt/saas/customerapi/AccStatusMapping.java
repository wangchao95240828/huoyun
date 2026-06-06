package com.xqt.saas.customerapi;

/**
 * 新系统 orders.status / shipments.status 到 ACC 旧 Express.Status 数字码的映射。
 *
 * ACC api/APIClass.php::doStatus 直接返回 Express.Status 整数值，旧客户端按数字解析。
 * 这里给出一个稳定的字符串 → 数字映射，缺失值返回 -1（旧系统约定的"找不到"）。
 */
public final class AccStatusMapping {
    public static final int NOT_FOUND = -1;

    private AccStatusMapping() {
    }

    public static int toAccCode(String status) {
        if (status == null) {
            return NOT_FOUND;
        }
        return switch (status) {
            case "DRAFT" -> 0;
            case "SUBMITTED", "ORDERED" -> 1;
            case "ACCEPTED", "IN_WAREHOUSE" -> 2;
            case "MEASURED" -> 3;
            case "FULFILLING", "BOOKED" -> 4;
            case "IN_TRANSIT" -> 5;
            case "DELIVERED" -> 7;
            case "COMPLETED", "CLOSED" -> 7;
            case "CANCELLED" -> 8;
            case "EXCEPTION" -> -2;
            default -> NOT_FOUND;
        };
    }

    /**
     * ACC api/Track.php 的 10 档物流进度（Delivery 字段）：
     *   0=待收取 / 1=已签入 / 2=转仓中 / 3=分发中 / 4=已发货
     *   5=转运中 / 6=送货中 / 7=已签收 / 8=已退件 / 9=已赔偿
     * 内部 tracking_status enum → ACC 数字码。-1=未知。
     */
    private static final String[] DELIVERY_NAMES = {
        "待收取", "已签入", "转仓中", "分发中", "已发货",
        "转运中", "送货中", "已签收", "已退件", "已赔偿"
    };

    public static int toDeliveryCode(String trackingStatus) {
        if (trackingStatus == null) return 0;
        return switch (trackingStatus) {
            case "CREATED" -> 1;
            case "IN_TRANSIT" -> 5;
            case "OUT_FOR_DELIVERY" -> 6;
            case "DELIVERED" -> 7;
            case "RETURNED" -> 8;
            case "CLAIMING" -> 9;
            case "EXCEPTION", "VOID" -> NOT_FOUND;
            default -> 0;
        };
    }

    public static String deliveryName(int code) {
        if (code < 0 || code >= DELIVERY_NAMES.length) return "未知";
        return DELIVERY_NAMES[code];
    }
}
