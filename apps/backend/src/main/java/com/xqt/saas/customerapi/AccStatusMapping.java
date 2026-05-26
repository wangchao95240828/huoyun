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
}
