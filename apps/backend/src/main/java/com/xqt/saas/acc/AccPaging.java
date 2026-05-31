package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 给 /api/acc/* 系列接口共用：把 (data, total) 打包成前端 fetchAccData 期望的 shape。
 * 同时提供 page/pageSize 的归一化，对应 fetchAccData 默认 page=1、pageSize=50。
 */
public final class AccPaging {
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 10_000;

    private AccPaging() {
    }

    public static int page(Integer page) {
        if (page == null || page < 1) return DEFAULT_PAGE;
        return page;
    }

    public static int pageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) return DEFAULT_PAGE_SIZE;
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    public static int offset(Integer page, Integer pageSize) {
        return (page(page) - 1) * pageSize(pageSize);
    }

    public static Map<String, Object> result(List<? extends Map<String, ?>> data, long total) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("total", total);
        return body;
    }

    /**
     * 与 result() 相同，但附带按当前过滤条件计算出的列聚合（合计）。
     * agg 推荐 shape: {"amount": "190145.33", "fee": "0.00"}（前端 footer 行渲染）。
     */
    public static Map<String, Object> result(List<? extends Map<String, ?>> data, long total,
                                             Map<String, Object> agg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("total", total);
        if (agg != null && !agg.isEmpty()) {
            body.put("aggregations", agg);
        }
        return body;
    }
}
