package com.xqt.saas.finance.common;

import java.util.HashMap;
import java.util.Objects;

/**
 * 统一响应结果封装类
 * 用于API接口返回统一格式的数据
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class R extends HashMap<String, Object> {
    private static final long serialVersionUID = 1L;

    public static final String CODE_TAG = "code";
    public static final String MSG_TAG = "msg";
    public static final String DATA_TAG = "data";
    public static final String TOTAL_TAG = "total";

    public static final int SUCCESS = 200;
    public static final int WARN = 301;
    public static final int ERROR = 500;

    public R() {
    }

    public R(int code, String msg) {
        super.put(CODE_TAG, code);
        super.put(MSG_TAG, msg);
    }

    public R(int code, String msg, Object data) {
        super.put(CODE_TAG, code);
        super.put(MSG_TAG, msg);
        if (data != null) {
            super.put(DATA_TAG, data);
        }
    }

    /**
     * 返回成功结果（无数据）
     */
    public static R success() {
        return R.success("操作成功");
    }

    /**
     * 返回成功结果（带数据）
     */
    public static R success(Object data) {
        return R.success("操作成功", data);
    }

    /**
     * 返回成功结果（带消息）
     */
    public static R success(String msg) {
        return R.success(msg, null);
    }

    /**
     * 返回成功结果（带消息和数据）
     */
    public static R success(String msg, Object data) {
        return new R(SUCCESS, msg, data);
    }

    /**
     * 返回成功结果（带消息、数据和总数）
     */
    public static R success(String msg, Object data, long total) {
        R result = new R(SUCCESS, msg, data);
        result.put(TOTAL_TAG, total);
        return result;
    }

    /**
     * 返回警告结果
     */
    public static R warn(String msg) {
        return R.warn(msg, null);
    }

    /**
     * 返回警告结果（带数据）
     */
    public static R warn(String msg, Object data) {
        return new R(WARN, msg, data);
    }

    /**
     * 返回错误结果（默认消息）
     */
    public static R error() {
        return R.error("操作失败");
    }

    /**
     * 返回错误结果（带消息）
     */
    public static R error(String msg) {
        return R.error(msg, null);
    }

    /**
     * 返回错误结果（带消息和数据）
     */
    public static R error(String msg, Object data) {
        return new R(ERROR, msg, data);
    }

    /**
     * 返回错误结果（带错误码和消息）
     */
    public static R error(int code, String msg) {
        return new R(code, msg, null);
    }

    /**
     * 判断请求是否成功
     */
    public boolean isSuccess() {
        return Objects.equals(SUCCESS, this.get(CODE_TAG));
    }

    /**
     * 判断请求是否警告
     */
    public boolean isWarn() {
        return Objects.equals(WARN, this.get(CODE_TAG));
    }

    /**
     * 判断请求是否错误
     */
    public boolean isError() {
        return Objects.equals(ERROR, this.get(CODE_TAG));
    }

    @Override
    public R put(String key, Object value) {
        super.put(key, value);
        return this;
    }
}
