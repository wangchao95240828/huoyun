package com.xqt.saas.acc;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * P0-C10 修复: 5 大承运商专有状态码 → tracking_status 归一映射 + 问题词典自动建 Ask.
 *
 * 对齐 ACC PHP 各 config/logistics/*.php (UPS/FedEx/DHL/EMS/ChinaPost) 50+ 状态码字典.
 * 之前 AccTrackingIngestController.normalize 只 5 行 if-else 关键字匹配, 多数 raw code
 * 不能精确归一, 异常事件也不自动建 acc_asks.
 *
 * 用法:
 *   String norm = CarrierStatusMap.normalize("UPS", "I", "In Transit");
 *   String askType = CarrierStatusMap.detectAskTrigger("Held in customs");
 *   if (askType != null) → 自动 INSERT acc_asks (auto-detected by tracking)
 */
public final class CarrierStatusMap {
    private CarrierStatusMap() {}

    // ─────────── 5 家承运商 raw code → tracking_status enum 映射 ───────────
    // 来源: ACC PHP config/logistics/UPS.php (286-380) / FedEx.php / DHL.php / EMS.php
    private static final Map<String, Map<String, String>> CARRIERS = new HashMap<>();
    static {
        // UPS: 单字母 code 主导 (P/I/O/D/X/M)
        Map<String, String> ups = new HashMap<>();
        ups.put("P", "IN_TRANSIT");           // Pickup Scan
        ups.put("M", "CREATED");              // Manifest pickup
        ups.put("I", "IN_TRANSIT");           // In Transit
        ups.put("O", "OUT_FOR_DELIVERY");     // Out for delivery
        ups.put("D", "DELIVERED");
        ups.put("X", "EXCEPTION");
        ups.put("RS", "RETURNED");            // Returned to sender
        ups.put("MV", "IN_TRANSIT");          // Movement
        ups.put("DO", "OUT_FOR_DELIVERY");    // Delivered to Other
        ups.put("NA", "EXCEPTION");           // No Address
        CARRIERS.put("UPS", ups);

        // FedEx: 2 字母 code 主导
        Map<String, String> fedex = new HashMap<>();
        fedex.put("OC", "CREATED");           // Order Created
        fedex.put("PU", "IN_TRANSIT");        // Picked Up
        fedex.put("AR", "IN_TRANSIT");        // Arrived at facility
        fedex.put("AF", "IN_TRANSIT");        // At FedEx destination
        fedex.put("AC", "IN_TRANSIT");        // At Canada Post facility
        fedex.put("IT", "IN_TRANSIT");        // In transit
        fedex.put("DP", "IN_TRANSIT");        // Departed
        fedex.put("EO", "IN_TRANSIT");        // Enroute Origin
        fedex.put("ED", "IN_TRANSIT");        // Enroute Destination
        fedex.put("OD", "OUT_FOR_DELIVERY");  // Out for delivery
        fedex.put("DL", "DELIVERED");         // Delivered
        fedex.put("HL", "EXCEPTION");         // Held at location
        fedex.put("DE", "EXCEPTION");         // Delivery Exception
        fedex.put("DY", "EXCEPTION");         // Delay
        fedex.put("CA", "EXCEPTION");         // Canceled
        fedex.put("RR", "RETURNED");          // Returning to shipper
        fedex.put("RS", "RETURNED");          // Returned to shipper
        fedex.put("SE", "EXCEPTION");         // Shipment Exception
        fedex.put("CC", "IN_TRANSIT");        // Cleared customs
        fedex.put("CD", "EXCEPTION");         // Customs Delay
        CARRIERS.put("FEDEX", fedex);

        // DHL: 数字 code 主导
        Map<String, String> dhl = new HashMap<>();
        dhl.put("PU", "IN_TRANSIT");          // Picked up
        dhl.put("AF", "IN_TRANSIT");          // Arrived facility
        dhl.put("DF", "IN_TRANSIT");          // Departed facility
        dhl.put("PL", "IN_TRANSIT");          // Processed at location
        dhl.put("WC", "IN_TRANSIT");          // With delivery courier
        dhl.put("OK", "DELIVERED");           // OK
        dhl.put("AR", "IN_TRANSIT");          // Arrived
        dhl.put("DD", "DELIVERED");           // Delivered
        dhl.put("HX", "EXCEPTION");           // Held customs
        dhl.put("HI", "EXCEPTION");           // Held import
        dhl.put("BA", "EXCEPTION");           // Bad address
        dhl.put("CM", "EXCEPTION");           // Damaged
        dhl.put("RT", "RETURNED");            // Returned
        CARRIERS.put("DHL", dhl);

        // EMS 中国邮政速递: 中文短语
        Map<String, String> ems = new HashMap<>();
        ems.put("已收寄", "IN_TRANSIT");
        ems.put("处理中", "IN_TRANSIT");
        ems.put("离开处理中心", "IN_TRANSIT");
        ems.put("到达处理中心", "IN_TRANSIT");
        ems.put("派送中", "OUT_FOR_DELIVERY");
        ems.put("已投递", "DELIVERED");
        ems.put("已签收", "DELIVERED");
        ems.put("成功签收", "DELIVERED");
        ems.put("妥投", "DELIVERED");
        ems.put("退回", "RETURNED");
        ems.put("丢失", "EXCEPTION");
        ems.put("破损", "EXCEPTION");
        ems.put("海关扣留", "EXCEPTION");
        ems.put("地址不详", "EXCEPTION");
        CARRIERS.put("EMS", ems);

        // CHINA_POST / 国际小包
        Map<String, String> cp = new HashMap<>();
        cp.put("已揽收", "IN_TRANSIT");
        cp.put("封发", "IN_TRANSIT");
        cp.put("国际邮件互换局", "IN_TRANSIT");
        cp.put("到达目的国", "IN_TRANSIT");
        cp.put("交境外邮政", "IN_TRANSIT");
        cp.put("派送", "OUT_FOR_DELIVERY");
        cp.put("妥投", "DELIVERED");
        cp.put("退回寄件人", "RETURNED");
        cp.put("无法投递", "EXCEPTION");
        cp.put("海关已清关", "IN_TRANSIT");
        cp.put("海关查验", "EXCEPTION");
        CARRIERS.put("CHINA_POST", cp);
    }

    /** raw_code 精确匹配 → 归一. 若 carrier 字典无, fallback 到关键字匹配. */
    public static String normalize(String carrier, String rawCode, String rawText) {
        if (carrier != null) {
            Map<String, String> dict = CARRIERS.get(carrier.toUpperCase());
            if (dict != null && rawCode != null) {
                String hit = dict.get(rawCode.toUpperCase());
                if (hit != null) return hit;
                // EMS/CHINA_POST 用中文短语 key, 试包含匹配
                if (rawText != null) {
                    for (var e : dict.entrySet()) {
                        if (rawText.contains(e.getKey())) return e.getValue();
                    }
                }
            }
        }
        return fallbackByKeyword(rawText);
    }

    private static String fallbackByKeyword(String rawText) {
        if (rawText == null) return "IN_TRANSIT";
        String s = rawText.toLowerCase();
        if (s.contains("delivered") || s.contains("signed") || s.contains("已签收") || s.contains("妥投"))
            return "DELIVERED";
        if (s.contains("out for delivery") || s.contains("派送中"))
            return "OUT_FOR_DELIVERY";
        if (s.contains("exception") || s.contains("delay") || s.contains("held")
            || s.contains("unable") || s.contains("异常") || s.contains("延误") || s.contains("扣留"))
            return "EXCEPTION";
        if (s.contains("returned") || s.contains("return to") || s.contains("退回"))
            return "RETURNED";
        return "IN_TRANSIT";
    }

    // ─────────── 问题词典 (异常关键字 → 自动建 acc_asks) ───────────
    // 对齐 ACC UPS.php:78-148 Question/QuestionStatus/QuestionContinue 三数组合并
    // 命中即返回 ask_type (NULL = 不需建 ask)
    private static final List<Map.Entry<String, String>> ASK_TRIGGERS = List.of(
        Map.entry("held in customs", "CUSTOMS"),
        Map.entry("customs delay", "CUSTOMS"),
        Map.entry("海关扣留", "CUSTOMS"),
        Map.entry("海关查验", "CUSTOMS"),
        Map.entry("clearance delay", "CUSTOMS"),

        Map.entry("damaged", "DAMAGE"),
        Map.entry("damage", "DAMAGE"),
        Map.entry("破损", "DAMAGE"),
        Map.entry("crushed", "DAMAGE"),

        Map.entry("lost", "LOST"),
        Map.entry("missing", "LOST"),
        Map.entry("丢失", "LOST"),
        Map.entry("无法找到", "LOST"),

        Map.entry("address incorrect", "ADDRESS"),
        Map.entry("address invalid", "ADDRESS"),
        Map.entry("bad address", "ADDRESS"),
        Map.entry("地址不详", "ADDRESS"),
        Map.entry("地址有误", "ADDRESS"),

        Map.entry("delay", "DELAY"),
        Map.entry("delayed", "DELAY"),
        Map.entry("flight delay", "DELAY"),
        Map.entry("飞机晚点", "DELAY"),
        Map.entry("延误", "DELAY"),

        Map.entry("returned to sender", "RETURN"),
        Map.entry("return to shipper", "RETURN"),
        Map.entry("退回寄件人", "RETURN"),

        Map.entry("unable to deliver", "ABNORMAL"),
        Map.entry("delivery exception", "ABNORMAL"),
        Map.entry("recipient not home", "ABNORMAL"),
        Map.entry("收件人不在", "ABNORMAL"),

        Map.entry("hold for pickup", "ABNORMAL"),
        Map.entry("held at location", "ABNORMAL"),

        Map.entry("under declare", "CUSTOMS"),   // 低报
        Map.entry("低报", "CUSTOMS"),
        Map.entry("intercepted", "ABNORMAL"),     // 拦截
        Map.entry("拦截", "ABNORMAL"),
        Map.entry("misrouted", "ABNORMAL"),       // 错分
        Map.entry("错分", "ABNORMAL")
    );

    /** 在 rawText 中检测异常关键字, 返回 ask_type 或 null. */
    public static String detectAskTrigger(String rawText) {
        if (rawText == null) return null;
        String s = rawText.toLowerCase();
        for (var e : ASK_TRIGGERS) {
            if (s.contains(e.getKey().toLowerCase())) return e.getValue();
        }
        return null;
    }
}
