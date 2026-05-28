package com.xqt.saas.customerapi;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.customerapi.CustomerApiResponses.BalanceLine;
import com.xqt.saas.customerapi.CustomerApiResponses.BalanceList;
import com.xqt.saas.customerapi.CustomerApiResponses.CancelResult;
import com.xqt.saas.customerapi.CustomerApiResponses.ChannelInfo;
import com.xqt.saas.customerapi.CustomerApiResponses.ChannelList;
import com.xqt.saas.customerapi.CustomerApiResponses.OrderDeclareItem;
import com.xqt.saas.customerapi.CustomerApiResponses.OrderDetail;
import com.xqt.saas.customerapi.CustomerApiResponses.OrderDetailList;
import com.xqt.saas.customerapi.CustomerApiResponses.PreOrderResult;
import com.xqt.saas.customerapi.CustomerApiResponses.PreSubmitResult;
import com.xqt.saas.customerapi.CustomerApiResponses.StatusEntry;
import com.xqt.saas.customerapi.CustomerApiResponses.StatusList;
import com.xqt.saas.customerapi.CustomerApiResponses.SubmitResult;
import com.xqt.saas.customerapi.CustomerApiResponses.TrackingDetail;
import com.xqt.saas.customerapi.CustomerApiResponses.TrackingEvent;
import com.xqt.saas.customerapi.CustomerApiResponses.TrackingList;
import com.xqt.saas.rates.RateEngine;
import com.xqt.saas.rates.RateQuoteRequest;
import com.xqt.saas.rates.RateQuoteResponse.Quote;
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
    private final CarrierGatewayRegistry carrierGateways;
    private final RateEngine rateEngine;

    /** 生产应为 true：报价失败直接阻断 Submit，不退化为简化估算。 */
    @org.springframework.beans.factory.annotation.Value("${app.rates.strict-quote:false}")
    private boolean strictQuote;

    public CustomerApiService(CustomerApiRepository repository, JsonSupport json,
                              JdbcTemplate jdbc, CarrierGatewayRegistry carrierGateways,
                              RateEngine rateEngine) {
        this.repository = repository;
        this.json = json;
        this.jdbc = jdbc;
        this.carrierGateways = carrierGateways;
        this.rateEngine = rateEngine;
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

    /**
     * 对应 ACC act=Submit：DRAFT→SUBMITTED，落 shipments/cartons/declarations，调渠道取号。
     */
    @SuppressWarnings("unchecked")
    @Transactional(rollbackFor = Exception.class)
    public SubmitResult submitOrder(CustomerApiPrincipal principal, String no) {
        if (no == null || no.isBlank()) throw ApiException.badRequest("No is required");
        setTenant(principal);

        Map<String, Object> order = repository.findOrderForCustomerApi(
            principal.tenantId(), principal.customerId(), no);
        if (order == null) throw ApiException.notFound("order not found: " + no);
        String status = (String) order.get("status");
        if (!"DRAFT".equals(status)) {
            throw ApiException.badRequest("only DRAFT can be submitted, current=" + status);
        }
        String orderId = (String) order.get("order_id");
        String orderNo = (String) order.get("order_no");
        String customerRef = (String) order.get("customer_ref");

        Map<String, Object> metadata = readJsonMap(order.get("metadata"));
        Map<String, Object> accCompat = metadata.get("acc_compat") instanceof Map<?, ?> m
            ? (Map<String, Object>) m
            : Map.of();

        String channelCode = stringOrNull(accCompat.get("product"));
        if (channelCode == null) {
            throw ApiException.badRequest("Product (channel code) missing on order");
        }
        String channelId = repository.findChannelIdByCode(principal.tenantId(), channelCode);
        if (channelId == null) {
            throw ApiException.badRequest("channel not active: " + channelCode);
        }

        BigDecimal weight = asBigDecimal(accCompat.get("weight"));
        Integer piece = asInteger(accCompat.get("piece"));
        String country = stringOrNull(accCompat.get("country"));
        String currency = stringOrNull(accCompat.get("currency"));
        BigDecimal declaredValue = totalDeclaredValue(accCompat.get("declare"));

        String shipmentNo = "SHP-" + orderNo;
        String shipmentId = repository.insertShipment(
            principal.tenantId(), principal.customerId(), channelId,
            shipmentNo, customerRef, country, declaredValue, currency
        );

        // ─── 调 RateEngine 算 AR/AP 真实费用 ───
        // 对应 ACC Submit 行为：先调 Freight::getFee 算客户应收 + 成本应付，
        // 再扣客户余额。引擎拿不到价表/报价失败时退化为简化估算，保持向后兼容。
        String prepayCurrency = currency == null ? "CNY" : currency;
        Quote quote = null;
        BigDecimal prepayAmount;
        try {
            RateQuoteRequest req = new RateQuoteRequest(
                principal.customerId(),
                stringOrNull(accCompat.get("customerGroupId")),
                channelCode,
                stringOrNull(accCompat.get("serviceCode")),
                stringOrNull(accCompat.get("channelAccount")),
                country,
                stringOrNull(accCompat.get("postcode")),
                weight,
                piece,
                asBigDecimal(accCompat.get("volume")),
                declaredValue,
                prepayCurrency,
                java.time.LocalDate.now(),
                asInteger(accCompat.get("batteryType")),
                asInteger(accCompat.get("specialType")),
                asInteger(accCompat.get("type"))
            );
            quote = rateEngine.quote(principal.tenantId(), req);
            if (quote != null && !quote.blockers().isEmpty()) {
                throw ApiException.badRequest("报价被拒：" + String.join("; ", quote.blockers()));
            }
            prepayAmount = quote == null ? estimatePrepayAmount(weight, declaredValue) : quote.totalAmount();
        } catch (ApiException ex) {
            // blockers 是硬性拒绝，永远不能退化
            if (ex.getMessage() != null && ex.getMessage().startsWith("报价被拒")) {
                throw ex;
            }
            // strict 模式（生产）：报价失败直接阻断，不允许简化估算绕过正式价表
            if (strictQuote) {
                throw ApiException.badRequest("报价失败，无法下单（strict 模式）：" + ex.getMessage());
            }
            // 非 strict（dev/demo）：退化为简化估算，保证联调能继续
            prepayAmount = estimatePrepayAmount(weight, declaredValue);
        }

        String balanceAccountId = null;
        String prepaidChargeId = null;
        if (prepayAmount.signum() > 0) {
            Map<String, Object> balanceAccount = repository.findCustomerBalanceAccount(
                principal.tenantId(), principal.customerId(), prepayCurrency);
            if (balanceAccount != null && balanceAccount.get("id") != null) {
                balanceAccountId = (String) balanceAccount.get("id");
                BigDecimal currentBalance = (BigDecimal) balanceAccount.get("balance");
                if (currentBalance != null && currentBalance.compareTo(prepayAmount) < 0) {
                    throw ApiException.badRequest(
                        "客户余额不足，需要 " + prepayAmount.toPlainString()
                        + " " + prepayCurrency + "，当前 " + currentBalance.toPlainString());
                }
                if (!repository.decrementBalance(balanceAccountId, prepayAmount)) {
                    throw ApiException.badRequest("余额扣减失败，请重试");
                }
                String chargeItemId = repository.findDefaultFreightChargeItemId(principal.tenantId());
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("prepaid", true);
                evidence.put("balance_account_id", balanceAccountId);
                evidence.put("prepay_at", java.time.Instant.now().toString());
                if (quote != null) {
                    evidence.put("rate_card_id", quote.matched().rateCardId());
                    evidence.put("rate_card_line_id", quote.matched().rateCardLineId());
                    evidence.put("customer_rate_matched", quote.matched().customerRateMatched());
                    evidence.put("group_rate_matched", quote.matched().groupRateMatched());
                    evidence.put("commission_rule_id", quote.matched().commissionRuleId());
                    evidence.put("remote_level", quote.remoteLevel());
                    evidence.put("freight", quote.freight());
                    evidence.put("fuel", quote.fuelAmount());
                    evidence.put("surcharge", quote.surchargeAmount());
                    evidence.put("commission", quote.commission());
                    evidence.put("chargeable_weight_kg", quote.chargeableWeightKg());
                }
                prepaidChargeId = repository.insertPrepaidCharge(
                    principal.tenantId(), shipmentId, chargeItemId,
                    prepayAmount, prepayCurrency, json.toJson(evidence));

                // 引擎产出成本价时同时写一条 AP 行（status=ESTIMATED, side=AP）
                if (quote != null && quote.costTotal() != null && quote.costTotal().signum() > 0) {
                    Map<String, Object> apEvidence = new LinkedHashMap<>();
                    apEvidence.put("cost_estimated", true);
                    apEvidence.put("rate_card_id", quote.matched().costRateCardId());
                    apEvidence.put("freight", quote.costFreight());
                    apEvidence.put("fuel", quote.costFuel());
                    apEvidence.put("surcharge", quote.costSurcharge());
                    repository.insertCostCharge(
                        principal.tenantId(), shipmentId, chargeItemId,
                        quote.costTotal(), prepayCurrency, json.toJson(apEvidence));
                }
            }
            // 若客户没建预付账户，跳过预扣（兼容部分 B2B 月结客户）
        }

        // 按 channel 路由到合适 gateway。ACC: getPlugin($Code)。
        CarrierGateway gateway = carrierGateways.forChannel(principal.tenantId(), channelCode);
        CarrierGateway.Issuance issuance;
        try {
            issuance = gateway.submit(new CarrierGateway.SubmitContext(
                principal.tenantId(), principal.customerCode(), orderNo, customerRef,
                channelCode, country, weight, piece,
                mapOrEmpty(accCompat.get("receiver"))
            ));
        } catch (RuntimeException ex) {
            // 取号失败回滚预扣（事务也会回滚，但显式语义更清楚）
            throw ApiException.badRequest("渠道取号失败: " + ex.getMessage());
        }

        repository.insertCarton(
            principal.tenantId(), shipmentId, "001",
            weight == null ? BigDecimal.ZERO : weight,
            issuance.carrierTrackingNo(), issuance.carrierMasterTrackingNo()
        );

        if (accCompat.get("declare") instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) continue;
                Map<String, Object> row = (Map<String, Object>) m;
                repository.insertDeclaration(
                    principal.tenantId(), shipmentId,
                    stringOrNull(row.get("name")) == null ? "ITEM" : stringOrNull(row.get("name")),
                    stringOrNull(row.get("material")),
                    stringOrNull(row.get("hsCode")),
                    asBigDecimal(row.get("quantity")),
                    asBigDecimal(row.get("price")),
                    json.toJson(row)
                );
            }
        }

        int updated = repository.markOrderSubmitted(orderId);
        if (updated == 0) {
            throw ApiException.badRequest("order state changed concurrently");
        }

        return new SubmitResult(
            orderId, orderNo, customerRef, shipmentId, shipmentNo,
            issuance.carrierTrackingNo(), issuance.carrierMasterTrackingNo(), "SUBMITTED"
        );
    }

    /**
     * 对应 ACC act=Modify：仅 DRAFT 状态允许修改 metadata.acc_compat 字段；未传字段保持原值。
     */
    @SuppressWarnings("unchecked")
    @Transactional(rollbackFor = Exception.class)
    public OrderDetail modifyOrder(CustomerApiPrincipal principal, String no,
                                   CustomerApiRequests.ModifyOrder body) {
        if (no == null || no.isBlank()) throw ApiException.badRequest("No is required");
        if (body == null) throw ApiException.badRequest("body is required");
        setTenant(principal);

        Map<String, Object> order = repository.findOrderForCustomerApi(
            principal.tenantId(), principal.customerId(), no);
        if (order == null) throw ApiException.notFound("order not found: " + no);
        String status = (String) order.get("status");
        if (!"DRAFT".equals(status)) {
            throw ApiException.badRequest("only DRAFT can be modified, current=" + status);
        }
        String orderId = (String) order.get("order_id");

        Map<String, Object> metadata = new LinkedHashMap<>(readJsonMap(order.get("metadata")));
        Map<String, Object> accCompat = new LinkedHashMap<>(
            metadata.get("acc_compat") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of()
        );
        applyIfPresent(accCompat, "product", body.product());
        applyIfPresent(accCompat, "country", body.country());
        applyIfPresent(accCompat, "currency", body.currency());
        applyIfPresent(accCompat, "remark", body.remark());
        if (body.weight() != null) accCompat.put("weight", body.weight().toPlainString());
        if (body.volume() != null) accCompat.put("volume", body.volume().toPlainString());
        if (body.piece() != null) accCompat.put("piece", body.piece());
        if (body.receiver() != null) accCompat.put("receiver", body.receiver());
        if (body.shipper() != null) accCompat.put("shipper", body.shipper());
        if (body.shipTo() != null) accCompat.put("shipTo", body.shipTo());
        if (body.declare() != null) accCompat.put("declare", body.declare());
        metadata.put("acc_compat", accCompat);

        int updated = repository.updateOrderMetadata(orderId, json.toJson(metadata));
        if (updated == 0) {
            throw ApiException.badRequest("order state changed concurrently");
        }
        return queryDetail(principal, new CustomerApiRequests.OrderRefList(no, null))
            .express().get(no);
    }

    /**
     * 对应 ACC act=Cancel：把 orders 切到 CANCELLED；如果已经落 shipments，把 shipments 标为 EXCEPTION。
     * shipment_status 枚举无 CANCELLED，使用 EXCEPTION 作为最接近映射，便于对账时识别。
     */
    @Transactional(rollbackFor = Exception.class)
    public CancelResult cancelOrder(CustomerApiPrincipal principal, String no) {
        if (no == null || no.isBlank()) throw ApiException.badRequest("No is required");
        setTenant(principal);

        Map<String, Object> order = repository.findOrderForCustomerApi(
            principal.tenantId(), principal.customerId(), no);
        if (order == null) throw ApiException.notFound("order not found: " + no);
        String status = (String) order.get("status");
        if (!List.of("DRAFT", "SUBMITTED", "ACCEPTED", "FULFILLING").contains(status)) {
            throw ApiException.badRequest("cannot cancel from status " + status);
        }
        String orderId = (String) order.get("order_id");
        String orderNo = (String) order.get("order_no");
        String customerRef = (String) order.get("customer_ref");

        int updated = repository.markOrderCancelled(orderId);
        if (updated == 0) {
            throw ApiException.badRequest("order state changed concurrently");
        }
        boolean shipmentMarked = false;
        if (!"DRAFT".equals(status) && customerRef != null) {
            int n = repository.markShipmentExceptionForOrder(principal.tenantId(), customerRef);
            shipmentMarked = n > 0;
        }

        // 复刻 ACC Cancel：反扣余额 + 把预扣 charges 标 VOID
        BigDecimal refundedAmount = BigDecimal.ZERO;
        int refundedCount = 0;
        if (customerRef != null) {
            List<Map<String, Object>> prepaid = repository.findPrepaidCharges(
                principal.tenantId(), customerRef);
            List<String> chargeIds = new ArrayList<>();
            for (Map<String, Object> row : prepaid) {
                BigDecimal amt = (BigDecimal) row.get("amount");
                String evidenceJson = readJsonText(row.get("evidence"));
                Map<String, Object> evidence = evidenceJson == null
                    ? Map.of()
                    : json.fromJson(evidenceJson, new TypeReference<Map<String, Object>>() {});
                String acctId = (String) evidence.get("balance_account_id");
                if (acctId != null) {
                    repository.restoreBalance(acctId, amt);
                    refundedAmount = refundedAmount.add(amt);
                    refundedCount++;
                }
                chargeIds.add((String) row.get("id"));
            }
            repository.voidPrepaidCharges(principal.tenantId(), chargeIds);
        }

        return new CancelResult(orderNo, customerRef, status, "CANCELLED",
            shipmentMarked, refundedAmount, refundedCount);
    }

    /**
     * 复刻 ACC act=PreSubmit：与 Submit 同样的校验和试算，但不取号、不扣余额、不动状态。
     * 给客户端确认运费 + 余额是否够时调用。
     */
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public PreSubmitResult preSubmitOrder(CustomerApiPrincipal principal, String no) {
        if (no == null || no.isBlank()) throw ApiException.badRequest("No is required");
        setTenant(principal);

        Map<String, Object> order = repository.findOrderForCustomerApi(
            principal.tenantId(), principal.customerId(), no);
        if (order == null) throw ApiException.notFound("order not found: " + no);
        String status = (String) order.get("status");
        if (!"DRAFT".equals(status)) {
            throw ApiException.badRequest("only DRAFT can be pre-submitted, current=" + status);
        }
        String orderNo = (String) order.get("order_no");

        Map<String, Object> metadata = readJsonMap(order.get("metadata"));
        Map<String, Object> accCompat = metadata.get("acc_compat") instanceof Map<?, ?> m
            ? (Map<String, Object>) m
            : Map.of();
        String channelCode = stringOrNull(accCompat.get("product"));
        String currency = stringOrNull(accCompat.get("currency"));
        if (currency == null) currency = "CNY";
        BigDecimal weight = asBigDecimal(accCompat.get("weight"));
        BigDecimal declaredValue = totalDeclaredValue(accCompat.get("declare"));
        BigDecimal estimatedAmount = estimatePrepayAmount(weight, declaredValue);

        // 检查渠道存在
        boolean channelOk = channelCode != null
            && repository.findChannelIdByCode(principal.tenantId(), channelCode) != null;

        // 检查余额
        Map<String, Object> balanceAccount = repository.findCustomerBalanceAccount(
            principal.tenantId(), principal.customerId(), currency);
        BigDecimal currentBalance = balanceAccount == null
            ? null
            : (BigDecimal) balanceAccount.get("balance");
        boolean balanceOk = balanceAccount == null
            || (currentBalance != null && currentBalance.compareTo(estimatedAmount) >= 0);

        // 用 channel registry 探测 gateway 名（不真调）
        String gatewayKey = channelOk
            ? carrierGateways.forChannel(principal.tenantId(), channelCode).gatewayKey()
            : null;

        List<String> blockers = new ArrayList<>();
        if (!channelOk) blockers.add("渠道未启用: " + channelCode);
        if (!balanceOk) blockers.add("余额不足");

        return new PreSubmitResult(
            orderNo, channelCode, gatewayKey,
            estimatedAmount, currency,
            currentBalance,
            balanceAccount == null
                ? null
                : (currentBalance == null ? null : currentBalance.subtract(estimatedAmount)),
            blockers.isEmpty(), blockers);
    }

    /**
     * 简化的预估运费算法（ACC 实际走 RateEngine 完整公式）：
     * 默认 base 30 CNY，按可计重量 × 25 CNY/kg 估算；申报金额按 0.1% 附加费。
     * MVP 占位；接 RateEngine 后替换。
     */
    private BigDecimal estimatePrepayAmount(BigDecimal weight, BigDecimal declaredValue) {
        BigDecimal base = new BigDecimal("30");
        BigDecimal w = (weight == null || weight.signum() <= 0) ? BigDecimal.ONE : weight;
        BigDecimal freight = w.multiply(new BigDecimal("25"));
        BigDecimal surcharge = declaredValue == null
            ? BigDecimal.ZERO
            : declaredValue.multiply(new BigDecimal("0.001"));
        return base.add(freight).add(surcharge)
            .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonMap(Object metadataValue) {
        String raw = readJsonText(metadataValue);
        if (raw == null || raw.isBlank()) return Map.of();
        return json.fromJson(raw, new TypeReference<Map<String, Object>>() {});
    }

    private void applyIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }

    @SuppressWarnings("unchecked")
    private BigDecimal totalDeclaredValue(Object declare) {
        if (!(declare instanceof List<?> list)) return null;
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            BigDecimal price = asBigDecimal(((Map<String, Object>) m).get("price"));
            BigDecimal qty = asBigDecimal(((Map<String, Object>) m).get("quantity"));
            if (price == null) continue;
            BigDecimal line = qty == null ? price : price.multiply(qty);
            sum = sum.add(line);
            any = true;
        }
        return any ? sum : null;
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

    /**
     * 对应 ACC act=Status。输入一组 No，返回每个 No 的状态码。找不到的 No 返回 -1。
     */
    @Transactional(readOnly = true)
    public StatusList queryStatus(CustomerApiPrincipal principal, CustomerApiRequests.OrderRefList body) {
        List<String> nos = resolveNos(body);
        setTenant(principal);
        Map<String, Map<String, Object>> byInput = new HashMap<>();
        List<Map<String, Object>> rows = repository.findOrderStatuses(
            principal.tenantId(), principal.customerId(), nos);
        for (Map<String, Object> row : rows) {
            String ref = (String) row.get("customer_ref");
            String orderNo = (String) row.get("order_no");
            if (ref != null) byInput.putIfAbsent(ref.toLowerCase(), row);
            if (orderNo != null) byInput.putIfAbsent(orderNo.toLowerCase(), row);
        }
        List<StatusEntry> entries = new ArrayList<>(nos.size());
        for (String no : nos) {
            Map<String, Object> row = byInput.get(no.toLowerCase());
            if (row == null) {
                entries.add(new StatusEntry(no, null, AccStatusMapping.NOT_FOUND));
            } else {
                String status = (String) row.get("status");
                entries.add(new StatusEntry(no, status, AccStatusMapping.toAccCode(status)));
            }
        }
        return new StatusList(entries);
    }

    /**
     * 对应 ACC act=Query。返回订单详情 + 申报明细 + 子单号；保留旧字段名。
     * 当前订单还在 orders 草稿阶段，receiver/declare 从 metadata.acc_compat 还原；
     * Submit 之后会落 shipments/declarations，届时改为联合查询。
     */
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public OrderDetailList queryDetail(CustomerApiPrincipal principal, CustomerApiRequests.OrderRefList body) {
        List<String> nos = resolveNos(body);
        setTenant(principal);
        List<Map<String, Object>> rows = repository.findOrdersDetail(
            principal.tenantId(), principal.customerId(), nos);
        Map<String, OrderDetail> byInput = new LinkedHashMap<>();
        for (String no : nos) {
            byInput.put(no, emptyOrderDetail(no));
        }
        for (Map<String, Object> row : rows) {
            String ref = (String) row.get("customer_ref");
            String orderNo = (String) row.get("order_no");
            String matchKey = pickMatchingInput(nos, ref, orderNo);
            if (matchKey == null) continue;

            String metaRaw = readJsonText(row.get("metadata"));
            Map<String, Object> metadata = metaRaw == null
                ? Map.of()
                : json.fromJson(metaRaw, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> accCompat = metadata.get("acc_compat") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();

            String status = (String) row.get("status");
            BigDecimal weight = asBigDecimal(accCompat.get("weight"));
            BigDecimal volume = asBigDecimal(accCompat.get("volume"));
            Integer piece = asInteger(accCompat.get("piece"));
            String addTime = formatAddTime(row.get("created_at"));

            byInput.put(matchKey, new OrderDetail(
                ref != null ? ref : matchKey,
                null,
                List.of(),
                ref,
                status,
                AccStatusMapping.toAccCode(status),
                stringOrNull(accCompat.get("country")),
                stringOrNull(accCompat.get("product")),
                null,
                weight,
                piece,
                volume,
                null,
                stringOrNull(accCompat.get("currency")),
                stringOrNull(accCompat.get("materialsEN")),
                stringOrNull(accCompat.get("materialsCN")),
                stringOrNull(accCompat.get("remark")),
                addTime,
                mapOrEmpty(accCompat.get("receiver")),
                mapOrEmpty(accCompat.get("shipper")),
                mapOrEmpty(accCompat.get("shipTo")),
                declareItems(accCompat.get("declare"))
            ));
        }
        return new OrderDetailList(byInput);
    }

    /**
     * 对应 ACC act=Track / api/Track.php：批量轨迹聚合。
     *
     * 新模型的 tracking_events 已经把 Transit_Process / Stowage_Process / Express_Process 三路合并到一张表，
     * source 字段保留来源，无需在 SQL 里 UNION。Draft 阶段（Submit 未跑）shipments 为空，返回空 Track。
     */
    @Transactional(readOnly = true)
    public TrackingList queryTracking(CustomerApiPrincipal principal, CustomerApiRequests.OrderRefList body) {
        List<String> nos = resolveNos(body);
        setTenant(principal);
        Map<String, TrackingDetail> result = new LinkedHashMap<>();
        for (String no : nos) {
            result.put(no, emptyTracking(no));
        }

        List<Map<String, Object>> shipments = repository.findShipmentsForTracking(
            principal.tenantId(), principal.customerId(), nos);
        if (shipments.isEmpty()) {
            return new TrackingList(result);
        }

        Map<String, String> shipmentIdToInput = new HashMap<>();
        Map<String, Map<String, Object>> shipmentIdToRow = new HashMap<>();
        for (Map<String, Object> row : shipments) {
            String key = pickMatchingInput(nos,
                (String) row.get("customer_ref"),
                (String) row.get("shipment_no"));
            if (key == null) continue;
            String shipmentId = (String) row.get("shipment_id");
            shipmentIdToInput.put(shipmentId, key);
            shipmentIdToRow.put(shipmentId, row);
        }

        List<Map<String, Object>> events = repository.findTrackingEvents(
            principal.tenantId(), List.copyOf(shipmentIdToInput.keySet()));
        Map<String, List<TrackingEvent>> bucket = new HashMap<>();
        Map<String, Map<String, Object>> latestByShipment = new HashMap<>();
        for (Map<String, Object> ev : events) {
            String sid = (String) ev.get("shipment_id");
            bucket.computeIfAbsent(sid, k -> new ArrayList<>())
                .add(new TrackingEvent(
                    formatAddTime(ev.get("event_time")),
                    stringOrNull(ev.get("location")),
                    stringOrNull(ev.get("raw_status")),
                    stringOrNull(ev.get("tracking_no")),
                    stringOrNull(ev.get("source")),
                    stringOrNull(ev.get("normalized_status"))
                ));
            latestByShipment.put(sid, ev);
        }

        for (Map.Entry<String, String> e : shipmentIdToInput.entrySet()) {
            String shipmentId = e.getKey();
            String inputKey = e.getValue();
            Map<String, Object> shipmentRow = shipmentIdToRow.get(shipmentId);
            Map<String, Object> latest = latestByShipment.get(shipmentId);
            List<TrackingEvent> track = bucket.getOrDefault(shipmentId, List.of());

            String trackNo = (String) shipmentRow.get("first_tracking_no");
            String trackStatus = latest != null
                ? stringOrNull(latest.get("normalized_status"))
                : stringOrNull(shipmentRow.get("status"));
            String trackMsg = latest != null ? stringOrNull(latest.get("raw_status")) : null;
            String trackTime = latest != null ? formatAddTime(latest.get("event_time")) : null;

            result.put(inputKey, new TrackingDetail(
                (String) shipmentRow.get("customer_ref") != null
                    ? (String) shipmentRow.get("customer_ref")
                    : inputKey,
                trackNo,
                trackStatus,
                trackMsg,
                trackTime,
                track
            ));
        }
        return new TrackingList(result);
    }

    private TrackingDetail emptyTracking(String no) {
        return new TrackingDetail(no, null, null, null, null, List.of());
    }

    /** 对应 ACC act=Product / act=Channel：列出租户启用渠道。 */
    @Transactional(readOnly = true)
    public ChannelList listChannels(CustomerApiPrincipal principal) {
        setTenant(principal);
        List<Map<String, Object>> rows = repository.findActiveChannels(principal.tenantId());
        List<ChannelInfo> data = rows.stream()
            .map(row -> new ChannelInfo(
                (String) row.get("code"),
                (String) row.get("name"),
                (String) row.get("lane"),
                (String) row.get("last_mile_method"),
                Boolean.TRUE.equals(row.get("active"))
            ))
            .toList();
        return new ChannelList(data);
    }

    private List<String> resolveNos(CustomerApiRequests.OrderRefList body) {
        if (body == null) throw ApiException.badRequest("body is required");
        List<String> nos = body.resolved();
        if (nos.isEmpty()) throw ApiException.badRequest("No is required");
        if (nos.size() > 100) throw ApiException.badRequest("No list size must be <= 100");
        for (String n : nos) {
            if (n == null || n.isBlank()) throw ApiException.badRequest("No must not be blank");
        }
        return nos;
    }

    private String pickMatchingInput(List<String> nos, String customerRef, String orderNo) {
        for (String n : nos) {
            if (n.equalsIgnoreCase(customerRef) || n.equalsIgnoreCase(orderNo)) return n;
        }
        return null;
    }

    private OrderDetail emptyOrderDetail(String no) {
        return new OrderDetail(
            no, null, List.of(), null, null, AccStatusMapping.NOT_FOUND,
            null, null, null, null, null, null,
            null, null, null, null, null, null,
            Map.of(), Map.of(), Map.of(), List.of()
        );
    }

    @SuppressWarnings("unchecked")
    private List<OrderDeclareItem> declareItems(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<OrderDeclareItem> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            Map<String, Object> row = (Map<String, Object>) m;
            result.add(new OrderDeclareItem(
                stringOrNull(row.get("name")),
                stringOrNull(row.get("cnName")),
                stringOrNull(row.get("hsCode")),
                stringOrNull(row.get("origin")),
                asBigDecimal(row.get("price")),
                asBigDecimal(row.get("quantity")),
                stringOrNull(row.get("note"))
            ));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOrEmpty(Object raw) {
        return raw instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private String stringOrNull(Object value) {
        if (value == null) return null;
        String s = value.toString();
        return s.isBlank() ? null : s;
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        String s = value.toString();
        if (s.isBlank()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer asInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer i) return i;
        if (value instanceof Number n) return n.intValue();
        String s = value.toString();
        if (s.isBlank()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String readJsonText(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s;
        return value.toString();
    }

    private String formatAddTime(Object value) {
        if (value == null) return null;
        if (value instanceof OffsetDateTime odt) {
            return odt.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return value.toString();
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
