package com.xqt.saas.order.common;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class R {

    private Integer code;
    private String msg;
    private Object data;
    private Long total;

    public R() {
    }

    public static R success() {
        R r = new R();
        r.setCode(200);
        r.setMsg("success");
        return r;
    }

    public static R success(String msg) {
        R r = new R();
        r.setCode(200);
        r.setMsg(msg);
        return r;
    }

    public static R success(Object data) {
        R r = new R();
        r.setCode(200);
        r.setMsg("success");
        r.setData(data);
        return r;
    }

    public static R success(String msg, Object data) {
        R r = new R();
        r.setCode(200);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }

    public static R success(String msg, Object data, Long total) {
        R r = new R();
        r.setCode(200);
        r.setMsg(msg);
        r.setData(data);
        r.setTotal(total);
        return r;
    }

    public static R error(String msg) {
        R r = new R();
        r.setCode(500);
        r.setMsg(msg);
        return r;
    }

    public static R error(Integer code, String msg) {
        R r = new R();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }
}
