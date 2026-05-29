package com.xqt.saas.common;

import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
        return response(ex.status(), ex.errorCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::fieldErrorMessage)
            .collect(Collectors.joining("; "));
        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return response(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, ex.getMessage());
    }

    /** DB 约束违反（必填字段为空 / 唯一冲突 / 外键缺失）→ 400 用户输入错误，而非 500。 */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
        org.springframework.dao.DataIntegrityViolationException ex) {
        LOGGER.warn("DB integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
            extractIntegrityMessage(ex.getMostSpecificCause().getMessage()));
    }

    /** 把 PG 报错精简为对人友好的提示。 */
    private String extractIntegrityMessage(String raw) {
        if (raw == null) return "data integrity violation";
        if (raw.contains("violates not-null constraint")) {
            int s = raw.indexOf("column \""), e = raw.indexOf("\"", s + 8);
            String col = s >= 0 && e > s ? raw.substring(s + 8, e) : "unknown";
            return "missing required field: " + col;
        }
        if (raw.contains("violates unique constraint")) {
            return "duplicate value: " + raw.replaceAll("\\s+", " ").substring(0, Math.min(200, raw.length()));
        }
        if (raw.contains("violates foreign key constraint")) {
            return "referenced entity not found";
        }
        return raw.length() > 200 ? raw.substring(0, 200) : raw;
    }

    /** Spring 3.x DispatcherServlet 默认抛 NoResourceFoundException 当路径无 controller 匹配。 */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception ex) {
        return response(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        LOGGER.error("Unhandled backend exception", ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "internal server error");
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, ErrorCode errorCode, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(errorCode, message));
    }

    private String fieldErrorMessage(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}
