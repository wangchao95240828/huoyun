package com.xqt.saas.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import com.xqt.saas.auth.AuthController;
import com.xqt.saas.auth.AuthPrincipal;
import com.xqt.saas.auth.AuthService;
import com.xqt.saas.auth.LoginRequest;
import com.xqt.saas.auth.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApiContractTests {

    @Test
    void okResponseUsesUnifiedEnvelope() {
        ApiResponse<String> response = ApiResponse.ok("ready");

        assertThat(response.ok()).isTrue();
        assertThat(response.data()).isEqualTo("ready");
        assertThat(response.error()).isNull();
        assertThat(response.errorCode()).isNull();
    }

    @Test
    void errorResponseUsesUnifiedEnvelope() {
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.UNAUTHORIZED, "missing token");

        assertThat(response.ok()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.error()).isEqualTo("missing token");
        assertThat(response.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED.name());
    }

    @Test
    void globalExceptionHandlerKeepsErrorCodeAndStatusAligned() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response = handler.handleApiException(ApiException.notFound("order not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().ok()).isFalse();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorCode.NOT_FOUND.name());
        assertThat(response.getBody().error()).isEqualTo("order not found");
    }

    @Test
    void loginControllerWrapsLoginResponse() {
        AuthService authService = mock(AuthService.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        AuthController controller = new AuthController(authService);
        LoginRequest request = new LoginRequest("xqt", "admin", "secret");
        AuthPrincipal principal = new AuthPrincipal(
            "user-1",
            "tenant-1",
            "xqt",
            "admin",
            "Admin",
            List.of("ADMIN"),
            List.of("admin.user.read"),
            100L,
            "session-1"
        );
        LoginResponse loginResponse = new LoginResponse(true, "token", 3600L, principal);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getHeader("user-agent")).thenReturn("JUnit");
        when(authService.login(request, "127.0.0.1", "JUnit")).thenReturn(loginResponse);

        ApiResponse<LoginResponse> response = controller.login(request, servletRequest);

        assertThat(response.ok()).isTrue();
        assertThat(response.data()).isSameAs(loginResponse);
        verify(authService).login(request, "127.0.0.1", "JUnit");
    }

    @Test
    void authPrincipalCopiesMutableCollections() {
        List<String> roles = new ArrayList<>(List.of("ADMIN"));
        List<String> permissions = new ArrayList<>(List.of("admin.user.read"));

        AuthPrincipal principal = new AuthPrincipal(
            "user-1",
            "tenant-1",
            "xqt",
            "admin",
            "Admin",
            roles,
            permissions,
            100L,
            "session-1"
        );
        roles.add("USER");
        permissions.add("admin.user.write");

        assertThat(principal.roles()).containsExactly("ADMIN");
        assertThat(principal.permissions()).containsExactly("admin.user.read");
        assertThatThrownBy(() -> principal.roles().add("OTHER")).isInstanceOf(UnsupportedOperationException.class);
    }
}
