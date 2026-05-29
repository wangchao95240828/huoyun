package com.xqt.saas.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;

/**
 * 端到端 E2E 业务测试发现的 2 个 bug 回归测试：
 *  1) 404 路径加 Bearer 后被 Exception.class 兜底成 500（应该 404）
 *  2) DB NOT-NULL 违反被 Exception.class 兜底成 500（应该 400 + 友好提示）
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void noHandlerFoundReturns404() {
        var ex = new NoHandlerFoundException("GET", "/nope", new HttpHeaders());
        var resp = handler.handleNotFound(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().errorCode()).isEqualTo(ErrorCode.NOT_FOUND.name());
    }

    @Test
    void noResourceFoundReturns404() {
        var ex = new NoResourceFoundException(HttpMethod.GET, "/static/nope");
        var resp = handler.handleNotFound(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void methodNotSupportedReturns405() {
        var ex = new HttpRequestMethodNotSupportedException("POST");
        var resp = handler.handleMethodNotAllowed(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void missingParamReturns400() {
        var ex = new MissingServletRequestParameterException("page", "Integer");
        var resp = handler.handleMissingParam(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED.name());
    }

    @Test
    void notNullConstraintViolationReturns400WithColumnName() {
        var pgCause = new RuntimeException(
            "ERROR: null value in column \"code\" of relation \"customers\" violates not-null constraint");
        var ex = new DataIntegrityViolationException("INSERT failed", pgCause);
        var resp = handler.handleDataIntegrityViolation(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().error()).contains("missing required field: code");
        assertThat(resp.getBody().errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED.name());
    }

    @Test
    void uniqueConstraintViolationReturns400() {
        var pgCause = new RuntimeException(
            "ERROR: duplicate key value violates unique constraint \"customers_tenant_id_code_key\"");
        var ex = new DataIntegrityViolationException("INSERT failed", pgCause);
        var resp = handler.handleDataIntegrityViolation(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().error()).contains("duplicate");
    }

    @Test
    void foreignKeyViolationReturns400() {
        var pgCause = new RuntimeException(
            "ERROR: insert or update on table \"x\" violates foreign key constraint \"fk_y\"");
        var ex = new DataIntegrityViolationException("INSERT failed", pgCause);
        var resp = handler.handleDataIntegrityViolation(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().error()).contains("referenced entity not found");
    }
}
