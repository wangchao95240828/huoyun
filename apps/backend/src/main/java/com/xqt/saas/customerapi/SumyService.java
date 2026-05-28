package com.xqt.saas.customerapi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.customerapi.CustomerApiResponses.SubmitResult;
import org.springframework.stereotype.Service;

/**
 * 复刻 ACC acc/api/sumy.php 的第三方推单（act=push）。
 *
 * 与旧系统差异（迁移说明）：
 *  - 鉴权：旧版 ApiToken（Customer_API.APIKey）放在 body；新版统一走 customer-api 签名鉴权
 *    （X-API-User/Time/Sign），客户身份由 CustomerApiPrincipal 确定。body 里的
 *    ApiToken/ApiUserName 保留但不再作为鉴权依据。
 *  - 不再另写一套订单创建逻辑：逐单映射成标准 PreOrder，复用 preOrder + submitOrder，
 *    自动落 orders/shipments/cartons/declarations/charges 并走 RateEngine 报价。
 *  - 逐单成败隔离：单个订单失败（重复单号/渠道停用/余额不足等）不影响其它订单。
 *
 * 请求体（PascalCase，兼容旧客户端）：
 *   { ApiUserName, ApiToken, OrderList: [ {
 *       PlatformOrderID, Channel, Weight(克), CustomsName, CustomsNameCN, CustomsValue,
 *       Receiver: { Name, Province, City, Address, PostCode, CountryCode, Tel, Mobile },
 *       ProductList: [ { CustomsName, CustomsCnName, HSCode, DeclareValue, Quantity } ]
 *   } ] }
 *
 * 响应：{ Success, Message, Result: [ { PlatformOrderID, Success, TrackingNumber, ErrorMessage } ] }
 */
@Service
public class SumyService {
    private final CustomerApiService customerApiService;

    public SumyService(CustomerApiService customerApiService) {
        this.customerApiService = customerApiService;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> push(CustomerApiPrincipal principal, Map<String, Object> body) {
        if (body == null) throw ApiException.badRequest("数据格式不合法！");
        Object orderListRaw = body.get("OrderList");
        if (!(orderListRaw instanceof List<?> orderList) || orderList.isEmpty()) {
            throw ApiException.badRequest("【OrderList】不能为空！");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Object item : orderList) {
            if (!(item instanceof Map<?, ?> m)) continue;
            Map<String, Object> order = (Map<String, Object>) m;
            String orderId = str(order.get("PlatformOrderID"));
            String error = null;
            String trackingNo = "";

            try {
                validate(order);
                customerApiService.preOrder(principal, toPreOrder(order));
                SubmitResult sub = customerApiService.submitOrder(principal, orderId);
                trackingNo = sub.trackingNo() == null ? "" : sub.trackingNo();
            } catch (ApiException ex) {
                error = ex.getMessage();
            } catch (RuntimeException ex) {
                error = "下单失败：" + ex.getMessage();
            }

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("PlatformOrderID", orderId);
            r.put("Success", error == null);
            r.put("TrackingNumber", trackingNo);
            r.put("ErrorMessage", error == null ? "" : error);
            results.add(r);
        }

        boolean allOk = results.stream().allMatch(r -> Boolean.TRUE.equals(r.get("Success")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("Success", allOk);
        out.put("Message", "");
        out.put("Result", results);
        return out;
    }

    /** 复刻 sumy.php 的逐单校验。 */
    @SuppressWarnings("unchecked")
    private void validate(Map<String, Object> order) {
        String orderId = str(order.get("PlatformOrderID"));
        if (orderId.length() < 6 || orderId.length() > 30 || !orderId.matches("[0-9a-zA-Z\\-]+")) {
            throw ApiException.badRequest("订单号长度必须是6-30，并且只能为【数字,字母,-】组合！");
        }
        BigDecimal weight = num(order.get("Weight"));
        if (weight == null || weight.signum() <= 0) {
            throw ApiException.badRequest("重量【" + order.get("Weight") + "】必须为大于零的数字");
        }
        Object recRaw = order.get("Receiver");
        if (!(recRaw instanceof Map<?, ?> rm)) {
            throw ApiException.badRequest("收件人信息不能为空！");
        }
        Map<String, Object> rec = (Map<String, Object>) rm;
        if (str(rec.get("Name")).length() > 60) {
            throw ApiException.badRequest("收货人长度不能超过60个字符！");
        }
        if (str(rec.get("Province")).length() > 50) {
            throw ApiException.badRequest("省/洲长度不能超过50个字符！");
        }
        if (str(rec.get("City")).length() > 50) {
            throw ApiException.badRequest("城市长度不能超过50个字符！");
        }
        if (str(rec.get("PostCode")).length() > 20) {
            throw ApiException.badRequest("邮编长度不能超过20个字符！");
        }
        if (str(order.get("Channel")).isBlank()) {
            throw ApiException.badRequest("找不到物流渠道！");
        }
        if (str(rec.get("CountryCode")).isBlank()) {
            throw ApiException.badRequest("收件国家不能为空！");
        }
    }

    /** sumy 订单 → 标准 PreOrder。重量克转千克，申报明细映射。 */
    @SuppressWarnings("unchecked")
    private CustomerApiRequests.PreOrder toPreOrder(Map<String, Object> order) {
        Map<String, Object> rec = (Map<String, Object>) order.get("Receiver");
        BigDecimal weightKg = num(order.get("Weight"))
            .divide(new BigDecimal("1000"), 3, RoundingMode.HALF_UP);

        Map<String, Object> receiver = new LinkedHashMap<>();
        receiver.put("name", rec.get("Name"));
        receiver.put("province", rec.get("Province"));
        receiver.put("city", rec.get("City"));
        receiver.put("address", rec.get("Address"));
        receiver.put("postcode", rec.get("PostCode"));
        receiver.put("countryCode", rec.get("CountryCode"));
        String phone = str(rec.get("Tel"));
        if (phone.isBlank()) phone = str(rec.get("Mobile"));
        receiver.put("phone", phone);

        List<Map<String, Object>> declare = new ArrayList<>();
        Object plRaw = order.get("ProductList");
        if (plRaw instanceof List<?> pl) {
            for (Object p : pl) {
                if (!(p instanceof Map<?, ?> pm)) continue;
                Map<String, Object> sv = (Map<String, Object>) pm;
                Map<String, Object> d = new LinkedHashMap<>();
                String name = str(sv.get("CustomsName"));
                d.put("name", name.isBlank() ? order.get("CustomsName") : name);
                String cn = str(sv.get("CustomsCnName"));
                d.put("material", cn.isBlank() ? order.get("CustomsNameCN") : cn);
                d.put("hsCode", sv.get("HSCode"));
                BigDecimal dv = num(sv.get("DeclareValue"));
                d.put("price", dv != null && dv.signum() > 0 ? dv : order.get("CustomsValue"));
                d.put("quantity", sv.get("Quantity"));
                declare.add(d);
            }
        }

        return new CustomerApiRequests.PreOrder(
            str(order.get("PlatformOrderID")),
            null,
            str(order.get("Channel")),
            str(rec.get("CountryCode")),
            weightKg,
            1,
            null,
            "CNY",
            receiver,
            null,
            null,
            declare,
            null,
            Map.of("source", "sumy")
        );
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    private static BigDecimal num(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(v.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
