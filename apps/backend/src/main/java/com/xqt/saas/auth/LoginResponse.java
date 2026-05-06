package com.xqt.saas.auth;

public record LoginResponse(
    boolean ok,
    String token,
    long expiresIn,
    AuthPrincipal user
) {
}
