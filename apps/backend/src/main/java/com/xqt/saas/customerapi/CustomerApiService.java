package com.xqt.saas.customerapi;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.customerapi.CustomerApiResponses.BalanceLine;
import com.xqt.saas.customerapi.CustomerApiResponses.BalanceList;
import com.xqt.saas.customerapi.CustomerApiResponses.PreOrderResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerApiService {
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final String ORDER_NO_PREFIX = "DOC";
    private static final int ORDER_NO_MIN_LEN = 6;
    private static final int ORDER_NO_MAX_LEN = 30;

    private final CustomerApiRepository repository;
    private final JsonSupport json;
    private final JdbcTemplate jdbc;

    public CustomerApiService(CustomerApiRepository repository, JsonSupport json, JdbcTemplate jdbc) {
        this.repository = repository;
        this.json = json;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public BalanceList queryBalance(CustomerApiPrincipal principal) {
        setTenant(principal);
        List<Map<String, Object>> rows = repository.findBalances(principal.tenantId(), principal.customerId());
        List<BalanceLine> lines = rows.stream()
            .map(row -> new BalanceLine(
                (String) row.get("currency"),
                (String) row.get("account_name"),
                toBigDecimal(row.get("balance"))
            ))
            .toList();
        return new BalanceList(principal.customerCode(), lines);
    }

    @Transactional(rollbackFor = Exception.class)
    public PreOrderResult preOrder(CustomerApiPrincipal principal, CustomerApiRequests.PreOrder body) {
        if (body == null) {
            throw ApiException.badRequest("body is required");
        }
        validatePreOrder(body);
        setTenant(principal);

        String orderNo = resolveOrderNo(body.no());
        Map<String, Object> accCompat = new LinkedHashMap<>();
        accCompat.put("token", nullToEmpty(body.token()));
        accCompat.put("product", nullToEmpty(body.product()));
        accCompat.put("country", nullToEmpty(body.country()));
        accCompat.put("weight", body.weight() == null ? "" : body.weight().toPlainString());
        Integer piece = body.piece();
        accCompat.put("piece", piece == null ? Integer.valueOf(0) : piece);
        accCompat.put("volume", body.volume() == null ? "" : body.volume().toPlainString());
        accCompat.put("currency", nullToEmpty(body.currency()));
        accCompat.put("receiver", body.receiver() == null ? Map.of() : body.receiver());
        accCompat.put("shipper", body.shipper() == null ? Map.of() : body.shipper());
        accCompat.put("shipTo", body.shipTo() == null ? Map.of() : body.shipTo());
        accCompat.put("declare", body.declare() == null ? List.of() : body.declare());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("acc_compat", accCompat);
        metadata.put("submission_status", "PRE_ORDER");
        metadata.put("source_api", "customer-api");

        String orderId = repository.insertDraftOrder(
            principal.tenantId(),
            principal.customerId(),
            orderNo,
            body.no(),
            json.toJson(metadata)
        );
        return new PreOrderResult(orderId, orderNo, "DRAFT", principal.customerCode());
    }

    private void validatePreOrder(CustomerApiRequests.PreOrder body) {
        requireNonBlank("No", body.no());
        requireNonBlank("Product", body.product());
        requireNonBlank("Country", body.country());
        if (body.weight() == null || body.weight().signum() < 0) {
            throw ApiException.badRequest("Weight is required and must be non-negative");
        }
        if (body.piece() == null || body.piece() < 1) {
            throw ApiException.badRequest("Piece must be a positive integer");
        }
        if (body.volume() != null && body.volume().signum() < 0) {
            throw ApiException.badRequest("Volume must be non-negative");
        }
        if (body.no().length() < ORDER_NO_MIN_LEN || body.no().length() > ORDER_NO_MAX_LEN) {
            throw ApiException.badRequest("No length must be " + ORDER_NO_MIN_LEN + ".." + ORDER_NO_MAX_LEN);
        }
        for (int i = 0; i < body.no().length(); i++) {
            char c = body.no().charAt(i);
            boolean allowed = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z') || c == '-';
            if (!allowed) {
                throw ApiException.badRequest("No must match [0-9A-Za-z-]");
            }
        }
    }

    private String resolveOrderNo(String submittedNo) {
        return ORDER_NO_PREFIX + "-" + ORDER_NO_TIME.format(LocalDateTime.now()) + "-" + submittedNo;
    }

    private void setTenant(CustomerApiPrincipal principal) {
        jdbc.queryForObject("select set_config('app.current_tenant_id', ?, true)",
            String.class, principal.tenantId());
    }

    private void requireNonBlank(String name, String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(name + " is required");
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }
}
