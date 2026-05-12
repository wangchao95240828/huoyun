package com.xqt.saas.customerapi;

/**
 * ACC API 兼容错误码。对应 acc/api/APIClass.php 中 output($N, $error) 的数字码。
 * 新接口在 ApiResponse.errorCode 里以字符串形式保留，便于旧客户端继续按码识别。
 */
public final class AccErrorCode {
    public static final String MISSING_USER = "ACC_101";
    public static final String MISSING_ACT = "ACC_102";
    public static final String TIME_DRIFT = "ACC_103";
    public static final String MISSING_VERSION = "ACC_104";
    public static final String INVALID_SIGN = "ACC_105";
    public static final String UNKNOWN_USER = "ACC_106";
    public static final String CUSTOMER_NOT_FOUND = "ACC_107";
    public static final String SIGN_MISMATCH = "ACC_108";
    public static final String MISSING_PARAM = "ACC_112";
    public static final String EMPTY_PARAM = "ACC_113";
    public static final String FEATURE_DISABLED = "ACC_100";

    private AccErrorCode() {
    }
}
