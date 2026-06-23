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

    // ════════ P0-A1: 业务阶段错误码 ACC 200-253 全映射 (对齐 APIClass.php output 数字码) ════════
    public static final String INVALID_BODY        = "ACC_200"; // 入参基本结构错
    public static final String PACKAGE_OR_DECLARE_REQUIRED = "ACC_201"; // 申报+装箱二选一缺
    public static final String RECEIVER_OR_WAREHOUSE_REQUIRED = "ACC_202"; // 收件/仓库二选一
    public static final String CUSTOMER_NOT_AUTHORIZED = "ACC_203"; // 客户无此产品权限
    public static final String PRODUCT_NOT_FOUND   = "ACC_204"; // 找不到产品
    public static final String CHANNEL_NOT_FOUND   = "ACC_205"; // 找不到渠道
    public static final String CHANNEL_ACCOUNT_INACTIVE = "ACC_206"; // 渠道账号停用
    public static final String COUNTRY_NOT_SUPPORTED = "ACC_207"; // 渠道不支持该国家
    public static final String POSTCODE_INVALID    = "ACC_208"; // 邮编格式错
    public static final String POSTCODE_REMOTE     = "ACC_209"; // 偏远地区 (仅警告)
    public static final String WEIGHT_OUT_OF_RANGE = "ACC_210"; // 重量超渠道范围
    public static final String VOLUME_OUT_OF_RANGE = "ACC_211"; // 体积超
    public static final String DIMENSION_OUT_OF_RANGE = "ACC_212"; // 长宽高超
    public static final String DECLARED_VALUE_INVALID = "ACC_213"; // 申报金额不合理
    public static final String BATTERY_REQUIRED    = "ACC_214"; // 电池货物必填 BatteryCode
    public static final String BATTERY_FORBIDDEN   = "ACC_215"; // 该渠道禁带电池
    public static final String DUPLICATE_ORDER_NO  = "ACC_216"; // 客户单号重复
    public static final String DUPLICATE_TRACKING_NO = "ACC_217"; // 转单号已被占用
    public static final String DECLARE_INCOMPLETE  = "ACC_218"; // 申报明细不全
    public static final String HSCODE_INVALID      = "ACC_219"; // HS 编码格式错 (非 6-10 位)
    public static final String PACKAGE_INCOMPLETE  = "ACC_220"; // 装箱单不全
    public static final String PACKAGE_WEIGHT_MISMATCH = "ACC_221"; // 装箱总重 != 申报总重
    public static final String PACKAGE_NO_DUPLICATE = "ACC_222"; // 装箱单号重复
    public static final String CUSTOMER_BALANCE_INSUFFICIENT = "ACC_223"; // 客户余额+授信不足
    public static final String CUSTOMER_FROZEN     = "ACC_224"; // 客户已冻结
    public static final String SERVICES_CONFLICT   = "ACC_225"; // 附加服务互斥 (e.g. 签收+成人签收)
    public static final String INSURANCE_REQUIRED  = "ACC_226"; // 该单必须投保
    public static final String SHIPPER_REQUIRED    = "ACC_227"; // 发件人必填
    public static final String IMPORTER_REQUIRED   = "ACC_228"; // 进口商必填
    public static final String LABEL_TYPE_INVALID  = "ACC_229"; // 标签类型不支持
    public static final String CURRENCY_INVALID    = "ACC_230"; // 币种码不识别
    public static final String FUEL_RATE_MISSING   = "ACC_231"; // 燃油费率缺
    public static final String RATE_CARD_MISSING   = "ACC_232"; // 找不到生效费率表
    public static final String TRACKING_OCCUPIED   = "ACC_233"; // 子单号已被其它客户占用
    public static final String ORDER_NOT_FOUND     = "ACC_234"; // 订单不存在
    public static final String ORDER_NOT_OWNED     = "ACC_235"; // 订单不属于该客户
    public static final String ORDER_LOCKED        = "ACC_236"; // 订单已审核锁定
    public static final String ORDER_ALREADY_SUBMITTED = "ACC_237";  // 重复提交
    public static final String ORDER_ALREADY_CANCELLED = "ACC_238"; // 已取消
    public static final String CANCEL_NOT_ALLOWED  = "ACC_239"; // 当前状态不可取消
    public static final String CARRIER_ERROR       = "ACC_240"; // carrier gateway 报错
    public static final String CARRIER_TIMEOUT     = "ACC_241"; // carrier 超时
    public static final String LABEL_NOT_READY     = "ACC_242"; // 面单还没生成
    public static final String TRACKING_NOT_READY  = "ACC_243"; // 轨迹未就绪
    public static final String PLUGIN_NOT_CONFIGURED = "ACC_244"; // 渠道插件未配置
    public static final String DECLARE_VALUE_MISMATCH = "ACC_245"; // 申报金额跟明细 sum 不一致
    public static final String QUANTITY_INVALID    = "ACC_246"; // 件数不合理
    public static final String CANCEL_HAS_AUDITED_CHARGE = "ACC_247"; // 含已审费用 不可取消
    public static final String EXCEED_RATE_LIMIT   = "ACC_429"; // 限流 (API 调用频率)
    public static final String INTERNAL_ERROR      = "ACC_500"; // 系统错

    private AccErrorCode() {
    }
}
