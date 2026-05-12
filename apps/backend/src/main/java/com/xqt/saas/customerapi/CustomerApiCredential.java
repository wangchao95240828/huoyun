package com.xqt.saas.customerapi;

public record CustomerApiCredential(
    String credentialId,
    String tenantId,
    String customerId,
    String customerCode,
    String accessKey,
    String secretKey,
    String status
) {
    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
