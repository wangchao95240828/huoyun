package com.xqt.saas.finance.common;

import com.xqt.saas.auth.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class UserContext {

    private UserContext() {
    }

    public static AuthPrincipal getPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new IllegalStateException("未登录或认证信息不存在");
        }
        return (AuthPrincipal) authentication.getPrincipal();
    }

    public static String getUserId() {
        return getPrincipal().userId();
    }

    public static String getTenantId() {
        return getPrincipal().tenantId();
    }

    public static String getTenantCode() {
        return getPrincipal().tenantCode();
    }

    public static String getUsername() {
        return getPrincipal().username();
    }

    public static String getDisplayName() {
        return getPrincipal().displayName();
    }
}