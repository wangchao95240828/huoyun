package com.dws.exception;

public class IotException extends RuntimeException {

    private final String errorCode;
    private final String suggestedAction;

    public IotException(String errorCode, String message, String suggestedAction) {
        super(message);
        this.errorCode = errorCode;
        this.suggestedAction = suggestedAction;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }

    public static IotException authInvalid() {
        return new IotException("IOT_AUTH_INVALID", "签名错误", "stop");
    }

    public static IotException timestampExpired() {
        return new IotException("IOT_TIMESTAMP_EXPIRED", "请求已过期", "stop");
    }

    public static IotException itemNotFound() {
        return new IotException("IOT_ITEM_NOT_FOUND", "箱号不存在", "stop");
    }

    public static IotException shipmentMismatch() {
        return new IotException("IOT_SHIPMENT_MISMATCH", "箱号和运单不匹配", "stop");
    }

    public static IotException dimensionInvalid() {
        return new IotException("IOT_DIMENSION_INVALID", "重量或尺寸不合法", "stop");
    }

    public static IotException labelFailed() {
        return new IotException("IOT_LABEL_FAILED", "标签生成失败", "stop");
    }

    public static IotException duplicateRequest() {
        return new IotException("IOT_DUPLICATE_REQUEST", "重复请求", "continue");
    }
}
