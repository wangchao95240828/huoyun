package com.xqt.saas.customerapi;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CustomerApiRequests {
    private CustomerApiRequests() {
    }

    /**
     * 对应 ACC api/APIClass.php 的 PreOrder/Modify 请求体。
     * 字段命名沿用旧系统，便于对照样本。映射到新系统的 orders 表时进入 metadata。
     */
    public record PreOrder(
        String no,
        String token,
        String product,
        String country,
        BigDecimal weight,
        Integer piece,
        BigDecimal volume,
        String currency,
        Map<String, Object> receiver,
        Map<String, Object> shipper,
        Map<String, Object> shipTo,
        List<Map<String, Object>> declare,
        List<Map<String, Object>> packageList,
        Map<String, Object> extra
    ) {
        public PreOrder {
            extra = extra == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(extra));
        }
    }

    /**
     * 对应 ACC act=Modify 的输入：仅 DRAFT 阶段允许，未提供的字段保持原值。
     */
    public record ModifyOrder(
        String product,
        String country,
        BigDecimal weight,
        Integer piece,
        BigDecimal volume,
        String currency,
        Map<String, Object> receiver,
        Map<String, Object> shipper,
        Map<String, Object> shipTo,
        List<Map<String, Object>> declare,
        String remark
    ) {
    }

    /**
     * 对应 ACC act=Status / act=Query 的输入。旧 PHP 接受 No 为字符串或数组，
     * 这里统一接受字符串数组，并保留 no 字段做单条兼容。
     */
    public record OrderRefList(
        String no,
        List<String> nos
    ) {
        public List<String> resolved() {
            if (nos != null && !nos.isEmpty()) {
                return nos;
            }
            if (no != null && !no.isBlank()) {
                return List.of(no);
            }
            return List.of();
        }
    }
}
