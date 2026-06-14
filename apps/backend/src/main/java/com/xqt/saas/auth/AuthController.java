package com.xqt.saas.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.xqt.saas.common.ApiResponse;
import com.xqt.saas.common.CommandResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request, httpRequest.getRemoteAddr(), httpRequest.getHeader("user-agent"));
        return ApiResponse.ok(response);
    }

    @GetMapping("/me")
    public ApiResponse<AuthMeResponse> me(Authentication authentication) {
        return ApiResponse.ok(new AuthMeResponse(principal(authentication)));
    }

    @PostMapping("/logout")
    public ApiResponse<CommandResponse> logout(Authentication authentication) {
        authService.logout(principal(authentication));
        return ApiResponse.ok(CommandResponse.ok());
    }

    /**
     * 忘记密码 — 发送重置链接（ACC User.php 邮箱重置规则）。
     * body: { tenantCode, email }
     * 返回 generic ok 不泄露邮箱存在与否。
     */
    @PostMapping("/forgot-password")
    public ApiResponse<java.util.Map<String, Object>> forgotPassword(
        @RequestBody java.util.Map<String, Object> body
    ) {
        String tenantCode = body.get("tenantCode") == null ? "xqt" : body.get("tenantCode").toString();
        String email = body.get("email") == null ? null : body.get("email").toString();
        if (email == null || email.isBlank()) {
            return ApiResponse.ok(java.util.Map.of("ok", true,
                "message", "如果该邮箱已注册，重置链接将发送至邮箱"));
        }
        authService.requestPasswordReset(tenantCode, email);
        return ApiResponse.ok(java.util.Map.of("ok", true,
            "message", "如果该邮箱已注册，重置链接将发送至邮箱"));
    }

    /**
     * 重置密码 — body: { token, newPassword, confirmPassword }
     */
    @PostMapping("/reset-password")
    public ApiResponse<java.util.Map<String, Object>> resetPassword(
        @RequestBody java.util.Map<String, Object> body
    ) {
        String token = body.get("token") == null ? null : body.get("token").toString();
        String newPw = body.get("newPassword") == null ? null : body.get("newPassword").toString();
        String conf  = body.get("confirmPassword") == null ? null : body.get("confirmPassword").toString();
        if (token == null || token.isBlank()) {
            throw com.xqt.saas.common.ApiException.badRequest("重置 token 必填");
        }
        if (newPw == null || newPw.length() < 5) {
            throw com.xqt.saas.common.ApiException.badRequest("密码长度要大于 5");
        }
        if (conf != null && !newPw.equals(conf)) {
            throw com.xqt.saas.common.ApiException.badRequest("两次输入的密码不一致");
        }
        authService.resetPassword(token, newPw);
        return ApiResponse.ok(java.util.Map.of("ok", true, "message", "密码重置成功，请重新登录"));
    }

    private AuthPrincipal principal(Authentication authentication) {
        return (AuthPrincipal) authentication.getPrincipal();
    }
}
