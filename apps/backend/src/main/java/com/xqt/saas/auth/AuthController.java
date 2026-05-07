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
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, httpRequest.getRemoteAddr(), httpRequest.getHeader("user-agent"));
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

    private AuthPrincipal principal(Authentication authentication) {
        return (AuthPrincipal) authentication.getPrincipal();
    }
}
