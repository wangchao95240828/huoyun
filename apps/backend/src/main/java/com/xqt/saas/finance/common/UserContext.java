package com.xqt.saas.finance.common;

import com.xqt.saas.auth.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 用户上下文工具类
 * 用于获取当前登录用户的信息
 */
public class UserContext {

    private UserContext() {
    }

    /**
     * 获取当前登录用户的认证信息
     */
    public static AuthPrincipal getPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new IllegalStateException("未登录或认证信息不存在");
        }
        return (AuthPrincipal) authentication.getPrincipal();
    }

    /**
     * 获取当前用户的ID
     */
    public static String getUserId() {
        return getPrincipal().userId();
    }

    /**
     * 获取当前用户的租户ID
     */
    public static String getTenantId() {
        return getPrincipal().tenantId();
    }

    /**
     * 获取当前用户的租户编码
     */
    public static String getTenantCode() {
        return getPrincipal().tenantCode();
    }

    /**
     * 获取当前用户的用户名
     */
    public static String getUsername() {
        return getPrincipal().username();
    }

    /**
     * 获取当前用户的显示名称
     */
    public static String getDisplayName() {
        return getPrincipal().displayName();
    }
}
