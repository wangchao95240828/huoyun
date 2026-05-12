package com.xqt.saas.customerapi;

public record CustomerApiPrincipal(
    String credentialId,
    String tenantId,
    String customerId,
    String customerCode,
    String accessKey,
    String secretKey
) {
}
