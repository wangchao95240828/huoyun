package com.xqt.saas.order.common;

public class UserContext {

    private static final ThreadLocal<String> tenantId = new ThreadLocal<>();

    public static String getTenantId() {
        String id = tenantId.get();
        return id != null ? id : "default-tenant-id";
    }

    public static void setTenantId(String id) {
        tenantId.set(id);
    }

    public static void clear() {
        tenantId.remove();
    }
}
