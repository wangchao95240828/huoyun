package com.xqt.saas.finance.common;

import java.util.UUID;

public final class TenantUtils {

    private TenantUtils() {
    }

    public static UUID currentUuid() {
        return UUID.fromString(UserContext.getTenantId());
    }
}
