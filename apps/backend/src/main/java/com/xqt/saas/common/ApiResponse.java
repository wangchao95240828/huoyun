package com.xqt.saas.common;

public record ApiResponse<T>(boolean ok, T data, String error, String errorCode) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String error) {
        return new ApiResponse<>(false, null, error, errorCode.name());
    }
}
