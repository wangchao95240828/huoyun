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
    private final SubmitCompensationService compensationService;
    private final com.xqt.saas.webhook.WebhookService webhook;
    private final com.xqt.saas.labels.LabelStorage labelStorage;
    private final com.xqt.saas.labels.LabelRepository labelRepository;
    private final SubmitValidator submitValidator;

    /** 生产应为 true：报价失败直接阻断 Submit，不退化为简化估算。 */
    @org.springframework.beans.factory.annotation.Value("${app.rates.strict-quote:false}")
    private boolean strictQuote;

    public CustomerApiService(CustomerApiRepository repository, JsonSupport json,
                              JdbcTemplate jdbc, CarrierGatewayRegistry carrierGateways,
                              RateEngine rateEngine,
                              SubmitCompensationService compensationService,
                              com.xqt.saas.webhook.WebhookService webhook,
                              com.xqt.saas.labels.LabelStorage labelStorage,
                              com.xqt.saas.labels.LabelRepository labelRepository,
                              SubmitValidator submitValidator) {
        this.repository = repository;
        this.json = json;
        this.jdbc = jdbc;
        this.carrierGateways = carrierGateways;
        this.rateEngine = rateEngine;
        this.compensationService = compensationService;
        this.webhook = webhook;
        this.labelStorage = labelStorage;
        this.labelRepository = labelRepository;
        this.submitValidator = submitValidator;
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
        if (order == null) throw ApiException.notFound("找不到订单: " + no);
        String status = (String) order.get("status");
        // ACC Express.php L1976: 快件已签收，无法再次操作
        if ("COMPLETED".equals(status)) {
            throw ApiException.badRequest("快件已签收，无法再次操作");
        }
        // ACC Express.php L2243: 货物状态为【X】无法取消（取消由 cancel 端点处理，提交侧拦异常状态）
        if ("CANCELLED".equals(status) || "EXCEPTION".equals(status)) {
            throw ApiException.badRequest("订单状态为 " + status + "，无法提交");
        }
        if (!"DRAFT".equals(status)) {
            throw ApiException.badRequest("仅 DRAFT 状态可提交，当前状态: " + status);
        }
        // ACC Submit.php L111-117：JoinID > 0（已合并）拒绝 Submit
        String mergedTo = (String) order.get("merged_to_order_id");
        if (mergedTo != null && !mergedTo.isBlank()) {
            throw ApiException.badRequest("该订单已合并到主订单 " + mergedTo + "，请提交主订单而非这一单");
        }
        String orderId = (String) order.get("order_id");
        String orderNo = (String) order.get("order_no");
        String customerRef = (String) order.get("customer_ref");

        Map<String, Object> metadata = readJsonMap(order.get("metadata"));
        Map<String, Object> accCompat = metadata.get("acc_compat") instanceof Map<?, ?> m
            ? (Map<String, Object>) m
            : Map.of();

        // ═══ ACC Express.php L670: 至少录入一票快件 ═══
        Object packageListRaw = accCompat.get("packageList");
        List<?> packageList = packageListRaw instanceof List<?> pl ? pl : List.of();
        if (packageList.isEmpty()) {
            // 兜底：weight + piece 都没有也算 0 票货
            BigDecimal w = asBigDecimal(accCompat.get("weight"));
            Integer p = asInteger(accCompat.get("piece"));
            if ((w == null || w.signum() <= 0) && (p == null || p <= 0)) {
                throw ApiException.badRequest("请至少录入一票快件");
            }
        }

        // ═══ ACC Express.php L660 + L673 + L662：子单号 vs 件数一致性 + 单内重复 ═══
        if (!packageList.isEmpty()) {
            int withTracking = 0;
            int withoutTracking = 0;
            java.util.Set<String> seenTracking = new java.util.HashSet<>();
            int rowIdx = 0;
            for (Object item : packageList) {
                rowIdx++;
                if (!(item instanceof Map<?, ?> rowMap)) continue;
                Map<String, Object> row = (Map<String, Object>) rowMap;
                Object trackingNo = row.get("trackingNo");
                Object pieceRaw = row.get("piece");
                int rowPiece = pieceRaw instanceof Number n ? n.intValue() :
                    (pieceRaw == null ? 0 : Integer.parseInt(pieceRaw.toString()));
                String tn = trackingNo == null ? "" : trackingNo.toString().trim();
                if (!tn.isEmpty()) {
                    withTracking++;
                    // ACC L660: 有追踪号则件数必须为 1
                    if (rowPiece != 1) {
                        throw ApiException.badRequest("第 " + rowIdx + " 行有追踪号 [" + tn + "]，件数必须为 1");
                    }
                    String key = tn.toLowerCase();
                    // ACC L662: 子单号本单内重复
                    if (!seenTracking.add(key)) {
                        throw ApiException.badRequest("子单号 [" + tn + "] 在本单内重复");
                    }
                    // ACC L1246 L1252: 子单号已被其它快件作为转单号 / 子单号
                    Integer dupOther = repository.countTrackingNoConflict(
                        principal.tenantId(), tn, orderId);
                    if (dupOther != null && dupOther > 0) {
                        throw ApiException.badRequest("子单号 [" + tn + "] 已被其它快件占用");
                    }
                } else {
                    withoutTracking++;
                }
                // ACC L807 / L1854: 第 X 行计费重为零
                Object weightRaw = row.get("weight");
                BigDecimal rowWeight = weightRaw == null ? BigDecimal.ZERO
                    : new BigDecimal(weightRaw.toString());
                if (rowWeight.signum() <= 0) {
                    throw ApiException.badRequest("第 " + rowIdx + " 行的重量为零，请检查");
                }
            }
            // ACC L673: 如果有追踪号，则所有货物都需要追踪号
            if (withTracking > 0 && withoutTracking > 0) {
                throw ApiException.badRequest("如果有追踪号，则所有货物都需要追踪号");
            }
        }

        // ═══ ACC Express.php L706: 找不到该配送地区 ═══
        String country = stringOrNull(accCompat.get("country"));
        if (country == null || country.isBlank()) {
            throw ApiException.badRequest("找不到该配送地区，请选择目的地");
        }

        // ═══ ACC Express.php L1445: 无法找到快件绑定的币种 ═══
        String currency = stringOrNull(accCompat.get("currency"));
        if (currency == null || currency.length() != 3) {
            throw ApiException.badRequest("无法找到快件绑定的币种，请检查");
        }

        String channelCode = stringOrNull(accCompat.get("product"));
        if (channelCode == null) {
            // ACC L717: 找不到该销售产品
            throw ApiException.badRequest("找不到该销售产品，请选择");
        }
        String channelId = repository.findChannelIdByCode(principal.tenantId(), channelCode);
        if (channelId == null) {
            // ACC L720: 该销售产品已停用
            throw ApiException.badRequest("该销售产品已停用或不存在: " + channelCode);
        }

        BigDecimal weight = asBigDecimal(accCompat.get("weight"));
        Integer piece = asInteger(accCompat.get("piece"));
        BigDecimal declaredValue = totalDeclaredValue(accCompat.get("declare"));

        String shipmentNo = "SHP-" + orderNo;
        String shipmentId = repository.insertShipment(
            principal.tenantId(), principal.customerId(), channelId,
            shipmentNo, customerRef, country, declaredValue, currency
        );
        // 任务 S8：建立 shipment ↔ order 强关联（替代 customer_ref 软关联）
        repository.insertShipmentOrderLink(
            principal.tenantId(), shipmentId, orderId, "SUBMIT");

        // ─── 调 RateEngine 算 AR/AP 真实费用 ───
        // 对应 ACC Submit 行为：先调 Freight::getFee 算客户应收 + 成本应付，
        // 再扣客户余额。引擎拿不到价表/报价失败时退化为简化估算，保持向后兼容。
        String prepayCurrency = currency == null ? "CNY" : currency;
        Quote quote = null;
        BigDecimal prepayAmount;
        java.util.List<com.xqt.saas.rates.RateQuoteResponse.BreakdownLine> keywordSurcharges = java.util.List.of();
        try {
            RateQuoteRequest req = buildQuoteRequest(
                principal.customerId(), accCompat, channelCode, country,
                weight, piece, declaredValue, prepayCurrency);
            quote = rateEngine.quote(principal.tenantId(), req);
            if (quote != null && !quote.blockers().isEmpty()) {
                throw ApiException.badRequest("报价被拒：" + String.join("; ", quote.blockers()));
            }
            // 任务 S3 A1 接入：根据申报品名扫品名关键词附加费，叠加到 AR 行 + 预扣
            if (quote != null) {
                java.util.List<com.xqt.saas.rates.RateQuoteResponse.BreakdownLine> kw =
                    rateEngine.applyKeywordSurcharges(
                        principal.tenantId(), extractDeclarationNames(accCompat.get("declare")),
                        quote.freight(), java.time.LocalDate.now());
                keywordSurcharges = kw == null ? java.util.List.of() : kw;
            }
            BigDecimal baseTotal = quote == null ? estimatePrepayAmount(weight, declaredValue) : quote.totalAmount();
            BigDecimal kwSum = keywordSurcharges.stream()
                .map(com.xqt.saas.rates.RateQuoteResponse.BreakdownLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            prepayAmount = baseTotal.add(kwSum);
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
        // 预扣余额（仅对开了 prepay 账户的客户；月结客户跳过此分支）
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
                BigDecimal balBefore = currentBalance == null ? BigDecimal.ZERO : currentBalance;
                repository.recordBalanceLedger(
                    principal.tenantId(), balanceAccountId, "CUSTOMER", principal.customerId(),
                    "PREPAY", "order", orderNo, prepayCurrency, "DEBIT",
                    prepayAmount, balBefore, balBefore.subtract(prepayAmount),
                    principal.customerCode(), "下单预扣");
            } else {
                // 无 prepay 账户也强制写一条留痕（balance_before=after=0），
                // 让 customer-balance-history 看得见这单预扣，account_id 用占位 NULL → 用 0 UUID
                try {
                    String shellAcctId = jdbc.queryForObject("""
                        SELECT id::text FROM financial_accounts
                         WHERE owner_type='CUSTOMER' AND owner_id=?::uuid AND currency=?
                         LIMIT 1
                        """, String.class, principal.customerId(), prepayCurrency);
                    if (shellAcctId == null) {
                        // 自动建影子账户（balance=0）
                        shellAcctId = jdbc.queryForObject("""
                            INSERT INTO financial_accounts (tenant_id, owner_type, owner_id, account_name,
                                                            account_type, currency, balance, source, is_show)
                            VALUES (?::uuid, 'CUSTOMER', ?::uuid, ?, 'CASH', ?, 0, 'LOCAL', true)
                            RETURNING id::text
                            """, String.class, principal.tenantId(), principal.customerId(),
                            principal.customerCode() + " (预扣账户)", prepayCurrency);
                    }
                    repository.recordBalanceLedger(
                        principal.tenantId(), shellAcctId, "CUSTOMER", principal.customerId(),
                        "PREPAY", "order", orderNo, prepayCurrency, "DEBIT",
                        prepayAmount, BigDecimal.ZERO, prepayAmount.negate(),
                        principal.customerCode(), "下单预扣(影子账户留痕)");
                } catch (Exception ignored) {
                    // ledger 留痕失败不阻断下单
                }
            }
        }

        // 写 charges —— 与预扣解耦：月结客户也必须落 AR/AP 行，否则后续无法对账/收款
        if (prepayAmount.signum() > 0) {
            if (quote != null) {
                prepaidChargeId = writeBreakdownCharges(
                    principal.tenantId(), shipmentId, prepayCurrency,
                    balanceAccountId, quote, keywordSurcharges,
                    principal.customerId(), orderId);
            } else {
                // 无报价（dev/demo 估算）：落一笔合并 AR 行
                String chargeItemId = repository.findDefaultFreightChargeItemId(principal.tenantId());
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("prepaid", balanceAccountId != null);
                evidence.put("balance_account_id", balanceAccountId);
                evidence.put("prepay_at", java.time.Instant.now().toString());
                evidence.put("estimate", true);
                prepaidChargeId = repository.insertPrepaidCharge(
                    principal.tenantId(), shipmentId, chargeItemId,
                    prepayAmount, prepayCurrency, json.toJson(evidence),
                    principal.customerId(), orderId);
            }
        }

        // 按 channel 路由到合适 gateway。ACC: getPlugin($Code)。
        CarrierGateway gateway = carrierGateways.forChannel(principal.tenantId(), channelCode);

        // ACC 对齐：gateway.submit 前先做字段级校验（HSCode/品名/姓名/地址/省州 等），
        // 拦不合规的请求避免无意义的承运商 API 调用，且给中文错误消息（而非 UPS 英文 errors 透传）
        submitValidator.validate(channelCode, gateway.gatewayKey(), accCompat, weight, piece);

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

        // ACC 对齐：createOrder 成功后立即把承运商返回的 label PDF 落盘 + INSERT label_files。
        // 这样制单成功用户就能立刻下载/打印面单，不用再单独调 LabelService.generate。
        // 落盘后从 raw 移除 label_base64，避免 cartons.carrier_evidence 字段塞几百 KB 大文本。
        Map<String, Object> rawForEvidence = issuance.raw();
        if (rawForEvidence != null && rawForEvidence.get("label_base64") instanceof String b64 && !b64.isBlank()) {
            try {
                byte[] labelBytes = java.util.Base64.getDecoder().decode(b64);
                String ext = (String) rawForEvidence.getOrDefault("label_format", "pdf");
                com.xqt.saas.labels.LabelStorage.StoredFile stored =
                    labelStorage.save(principal.tenantId(), labelBytes, ext.toLowerCase());
                labelRepository.insertLabelFile(
                    principal.tenantId(), shipmentId, issuance.carrierTrackingNo(),
                    "PDF".equalsIgnoreCase(ext) ? "PDF" : "IMAGE",
                    stored.fileHash(), stored.fileExt(), stored.storagePath(), stored.fileSize(),
                    "SUBMIT", null
                );
                // 把大字段抹掉，只在 raw 里留个引用，cartons.carrier_evidence 就轻量了
                rawForEvidence = new LinkedHashMap<>(rawForEvidence);
                rawForEvidence.remove("label_base64");
                rawForEvidence.put("label_stored_hash", stored.fileHash());
            } catch (Exception labelEx) {
                // label 落盘失败不阻断制单（tracking 已生成），日志 + 继续
                System.err.println("[CustomerApiService] label storage failed for tracking="
                    + issuance.carrierTrackingNo() + " : " + labelEx.getMessage());
            }
        }

        // provider 的 request/response 完整落到 cartons.carrier_evidence，
        // 便于运维/客服在前端运单详情看到取号的真实证据（任务5 文档要求）
        String carrierEvidenceJson = rawForEvidence == null || rawForEvidence.isEmpty()
            ? null : json.toJson(rawForEvidence);
        try {
            repository.insertCarton(
                principal.tenantId(), shipmentId, "001",
                weight == null ? BigDecimal.ZERO : weight,
                issuance.carrierTrackingNo(), issuance.carrierMasterTrackingNo(),
                carrierEvidenceJson
            );
        } catch (RuntimeException ex) {
            // 任务 S2：provider 已取号但本地 insert 失败 → 触发补偿（best-effort cancel + 写 orphan 表）
            CarrierGateway gatewayForCompensation = carrierGateways.forChannel(principal.tenantId(), channelCode);
            String providerCode = gatewayForCompensation.gatewayKey();
            compensationService.compensateOrphanTracking(
                principal.tenantId(), providerCode,
                issuance.carrierMasterTrackingNo(), issuance.carrierTrackingNo(),
                channelCode, orderNo, customerRef,
                "insertCarton failed: " + ex.getMessage(),
                java.util.Map.of("orderNo", orderNo, "channelCode", channelCode),
                issuance.raw()
            );
            throw ex; // 让外层事务回滚
        }

        // 取号成功后累加渠道账号当日票池，供 RateEngine 限额检查使用（ACC: Channel_Limit）
        String channelAccountCode = stringOrNull(accCompat.get("channelAccount"));
        if (channelAccountCode != null) {
            repository.bumpChannelAccountUsage(
                principal.tenantId(), channelId, channelAccountCode,
                piece == null ? 1 : piece, weight);
        }

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

        // 发布 order.submitted webhook（事务提交后调度器会发出）
        Map<String, Object> webhookPayload = new LinkedHashMap<>();
        webhookPayload.put("orderNo", orderNo);
        webhookPayload.put("customerRef", customerRef);
        webhookPayload.put("status", "SUBMITTED");
        webhookPayload.put("trackingNo", issuance.carrierTrackingNo());
        webhookPayload.put("carrierMasterTrackingNo", issuance.carrierMasterTrackingNo());
        webhook.publish(principal.tenantId(), principal.customerId(), "order.submitted", webhookPayload);

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
                    BigDecimal before = repository.findAccountBalance(acctId);
                    if (before == null) before = BigDecimal.ZERO;
                    repository.restoreBalance(acctId, amt);
                    // 资金流水：预扣释放（余额增加 CREDIT）
                    repository.recordBalanceLedger(
                        principal.tenantId(), acctId, "CUSTOMER", principal.customerId(),
                        "PREPAY_RELEASE", "order", orderNo,
                        (String) row.get("currency"), "CREDIT",
                        amt, before, before.add(amt),
                        principal.customerCode(), "取消订单释放预扣");
                    refundedAmount = refundedAmount.add(amt);
                    refundedCount++;
                }
                chargeIds.add((String) row.get("id"));
            }
            repository.voidPrepaidCharges(principal.tenantId(), chargeIds);
        }

        // 发布 order.cancelled webhook
        Map<String, Object> cancelPayload = new LinkedHashMap<>();
        cancelPayload.put("orderNo", orderNo);
        cancelPayload.put("customerRef", customerRef);
        cancelPayload.put("previousStatus", status);
        cancelPayload.put("status", "CANCELLED");
        cancelPayload.put("refundedAmount", refundedAmount);
        webhook.publish(principal.tenantId(), principal.customerId(), "order.cancelled", cancelPayload);

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
        String country = stringOrNull(accCompat.get("country"));
        BigDecimal weight = asBigDecimal(accCompat.get("weight"));
        Integer piece = asInteger(accCompat.get("piece"));
        BigDecimal declaredValue = totalDeclaredValue(accCompat.get("declare"));

        List<String> blockers = new ArrayList<>();

        // 检查渠道存在
        boolean channelOk = channelCode != null
            && repository.findChannelIdByCode(principal.tenantId(), channelCode) != null;
        if (!channelOk) blockers.add("渠道未启用: " + channelCode);

        // ─── 与 Submit 完全同口径：调 RateEngine.quote()，blocker/strict 一致 ───
        Quote quote = null;
        BigDecimal estimatedAmount;
        if (channelOk) {
            try {
                quote = rateEngine.quote(principal.tenantId(),
                    buildQuoteRequest(principal.customerId(), accCompat, channelCode,
                        country, weight, piece, declaredValue, currency));
                if (quote != null && !quote.blockers().isEmpty()) {
                    blockers.addAll(quote.blockers());
                }
            } catch (ApiException ex) {
                // strict 模式报价失败 = 不可下单，反映为 blocker；非 strict 退化估算
                if (strictQuote) {
                    blockers.add("报价失败：" + ex.getMessage());
                }
            }
        }
        estimatedAmount = quote != null
            ? quote.totalAmount()
            : estimatePrepayAmount(weight, declaredValue);

        // 检查余额
        Map<String, Object> balanceAccount = repository.findCustomerBalanceAccount(
            principal.tenantId(), principal.customerId(), currency);
        BigDecimal currentBalance = balanceAccount == null
            ? null
            : (BigDecimal) balanceAccount.get("balance");
        boolean balanceOk = balanceAccount == null
            || (currentBalance != null && currentBalance.compareTo(estimatedAmount) >= 0);
        if (!balanceOk) blockers.add("余额不足");

        // 用 channel registry 探测 gateway 名（不真调）
        String gatewayKey = channelOk
            ? carrierGateways.forChannel(principal.tenantId(), channelCode).gatewayKey()
            : null;

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
     * 按 quote.breakdown 把运费拆成多笔 AR/AP charges（对应 ACC Express_Charge.Type 多行）：
     *   AR: FREIGHT / FUEL / REMOTE（每笔带 prepaid=true，cancel 时逐行退款，合计 = total）
     *   AP: FREIGHT / FUEL / REMOTE（status=ESTIMATED side=AP，不带 prepaid）
     * commission 不向客户预扣（不计入 total），仅记 evidence。
     *
     * @return 第一笔 AR 行的 id（用于 SubmitResult 关联）
     */
    private String writeBreakdownCharges(String tenantId, String shipmentId, String currency,
                                         String balanceAccountId, Quote quote,
                                         java.util.List<com.xqt.saas.rates.RateQuoteResponse.BreakdownLine> keywordSurcharges,
                                         String customerId, String orderId) {
        String prepayAt = java.time.Instant.now().toString();

        // ─── AR 多行 ───
        String firstChargeId = null;
        firstChargeId = insertArLine(tenantId, shipmentId, currency, balanceAccountId, prepayAt,
            "FREIGHT", quote.freight(), quote, firstChargeId, customerId, orderId);
        firstChargeId = insertArLine(tenantId, shipmentId, currency, balanceAccountId, prepayAt,
            "FUEL", quote.fuelAmount(), quote, firstChargeId, customerId, orderId);
        firstChargeId = insertArLine(tenantId, shipmentId, currency, balanceAccountId, prepayAt,
            "REMOTE", quote.surchargeAmount(), quote, firstChargeId, customerId, orderId);
        firstChargeId = insertArLine(tenantId, shipmentId, currency, balanceAccountId, prepayAt,
            "INSURANCE", quote.insuranceAmount(), quote, firstChargeId, customerId, orderId);
        firstChargeId = insertArLine(tenantId, shipmentId, currency, balanceAccountId, prepayAt,
            "BATTERY", quote.batteryAmount(), quote, firstChargeId, customerId, orderId);
        firstChargeId = insertArLine(tenantId, shipmentId, currency, balanceAccountId, prepayAt,
            "PROCESSING", quote.processingAmount(), quote, firstChargeId, customerId, orderId);

        // ─── 任务 S3 A1：品名关键词附加费各落一行 AR ───
        if (keywordSurcharges != null) {
            for (com.xqt.saas.rates.RateQuoteResponse.BreakdownLine line : keywordSurcharges) {
                firstChargeId = insertArLine(tenantId, shipmentId, currency, balanceAccountId, prepayAt,
                    line.code(), line.amount(), quote, firstChargeId, customerId, orderId);
            }
        }

        // ─── AP 多行（仅当引擎产出成本价）───
        if (quote.costTotal() != null && quote.costTotal().signum() > 0) {
            insertApLine(tenantId, shipmentId, currency, "FREIGHT", quote.costFreight(), quote, customerId, orderId);
            insertApLine(tenantId, shipmentId, currency, "FUEL", quote.costFuel(), quote, customerId, orderId);
            insertApLine(tenantId, shipmentId, currency, "REMOTE", quote.costSurcharge(), quote, customerId, orderId);
            insertApLine(tenantId, shipmentId, currency, "INSURANCE", quote.costInsurance(), quote, customerId, orderId);
            insertApLine(tenantId, shipmentId, currency, "BATTERY", quote.costBattery(), quote, customerId, orderId);
            insertApLine(tenantId, shipmentId, currency, "PROCESSING", quote.costProcessing(), quote, customerId, orderId);
        }
        return firstChargeId;
    }

    /** 任务 S3 A1：从 acc_compat.declare 取所有品名 name 字段。 */
    @SuppressWarnings("unchecked")
    private java.util.List<String> extractDeclarationNames(Object declare) {
        if (!(declare instanceof java.util.List<?> list)) return java.util.List.of();
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Object name = ((Map<String, Object>) m).get("name");
                if (name != null) out.add(name.toString());
            }
        }
        return out;
    }

    private String insertArLine(String tenantId, String shipmentId, String currency,
                                String balanceAccountId, String prepayAt,
                                String code, BigDecimal amount, Quote quote, String firstChargeId,
                                String customerId, String orderId) {
        if (amount == null || amount.signum() == 0) return firstChargeId;
        String chargeItemId = repository.findChargeItemIdByCode(tenantId, code);
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("prepaid", true);
        ev.put("balance_account_id", balanceAccountId);
        ev.put("prepay_at", prepayAt);
        ev.put("component", code);
        ev.put("rate_card_id", quote.matched().rateCardId());
        ev.put("rate_card_line_id", quote.matched().rateCardLineId());
        if ("FREIGHT".equals(code)) {
            ev.put("customer_rate_matched", quote.matched().customerRateMatched());
            ev.put("group_rate_matched", quote.matched().groupRateMatched());
            ev.put("commission_rule_id", quote.matched().commissionRuleId());
            ev.put("commission", quote.commission());
            ev.put("remote_level", quote.remoteLevel());
            ev.put("chargeable_weight_kg", quote.chargeableWeightKg());
        }
        String id = repository.insertChargeLine(tenantId, shipmentId, chargeItemId,
            "AR", amount, currency, json.toJson(ev), customerId, orderId);
        return firstChargeId == null ? id : firstChargeId;
    }

    private void insertApLine(String tenantId, String shipmentId, String currency,
                              String code, BigDecimal amount, Quote quote,
                              String customerId, String orderId) {
        if (amount == null || amount.signum() == 0) return;
        String chargeItemId = repository.findChargeItemIdByCode(tenantId, code);
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("cost_estimated", true);
        ev.put("component", code);
        ev.put("rate_card_id", quote.matched().costRateCardId());
        repository.insertChargeLine(tenantId, shipmentId, chargeItemId,
            "AP", amount, currency, json.toJson(ev), customerId, orderId);
    }

    /**
     * 从 metadata.acc_compat 构造 RateEngine 入参。PreSubmit 与 Submit 共用，保证报价口径一致。
     */
    @SuppressWarnings("unchecked")
    private RateQuoteRequest buildQuoteRequest(String customerId, Map<String, Object> accCompat,
                                               String channelCode, String country,
                                               BigDecimal weight, Integer piece,
                                               BigDecimal declaredValue, String currency) {
        // postcode 优先顶层；fallback 到 receiver.postcode（前端 /full 把邮编放收件人下）
        String postcode = stringOrNull(accCompat.get("postcode"));
        if (postcode == null && accCompat.get("receiver") instanceof Map<?, ?> r) {
            postcode = stringOrNull(((Map<String, Object>) r).get("postcode"));
        }
        return new RateQuoteRequest(
            customerId,
            stringOrNull(accCompat.get("customerGroupId")),
            channelCode,
            stringOrNull(accCompat.get("serviceCode")),
            stringOrNull(accCompat.get("channelAccount")),
            country,
            postcode,
            weight,
            piece,
            asBigDecimal(accCompat.get("volume")),
            declaredValue,
            currency == null ? "CNY" : currency,
            java.time.LocalDate.now(),
            asInteger(accCompat.get("batteryType")),
            asInteger(accCompat.get("specialType")),
            asInteger(accCompat.get("type"))
        );
    }

    /**
     * 简化的预估运费算法（ACC 实际走 RateEngine 完整公式）：
     * 默认 base 30 CNY，按可计重量 × 25 CNY/kg 估算；申报金额按 0.1% 附加费。
     * 仅非 strict（dev/demo）模式作为兜底，不再是生产主路径。
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
     * 对应 ACC act=Sync — 批量回查 status + tracking + 最近一条事件。
     * 同一单可能多 carton，按 order_no 聚合：取首个 tracking_no + 最新事件。
     */
    @Transactional(readOnly = true)
    public CustomerApiResponses.SyncList syncOrders(CustomerApiPrincipal principal,
                                                     CustomerApiRequests.OrderRefList body) {
        List<String> nos = resolveNos(body);
        if (nos.size() > 500) {
            throw ApiException.badRequest("sync 单次最多 500 条，当前 " + nos.size());
        }
        setTenant(principal);
        Map<String, Map<String, Object>> byInput = new LinkedHashMap<>();
        List<Map<String, Object>> rows = repository.findOrdersForSync(
            principal.tenantId(), principal.customerId(), nos);
        for (Map<String, Object> row : rows) {
            String ref = (String) row.get("customer_ref");
            String orderNo = (String) row.get("order_no");
            // 同单可能多 carton，已存在的不覆盖（首条 tracking + 已聚合的最近事件）
            if (ref != null) byInput.putIfAbsent(ref.toLowerCase(), row);
            if (orderNo != null) byInput.putIfAbsent(orderNo.toLowerCase(), row);
        }
        List<CustomerApiResponses.SyncEntry> entries = new ArrayList<>(nos.size());
        int found = 0;
        for (String no : nos) {
            Map<String, Object> row = byInput.get(no.toLowerCase());
            if (row == null) {
                entries.add(new CustomerApiResponses.SyncEntry(
                    no, null, AccStatusMapping.NOT_FOUND,
                    0, AccStatusMapping.deliveryName(0),
                    null, null, null, null));
            } else {
                found++;
                String status = (String) row.get("status");
                Object eventTime = row.get("last_event_at");
                int delivery = AccStatusMapping.toDeliveryCode((String) row.get("last_event_normalized"));
                entries.add(new CustomerApiResponses.SyncEntry(
                    no, status, AccStatusMapping.toAccCode(status),
                    delivery, AccStatusMapping.deliveryName(delivery),
                    (String) row.get("tracking_no"),
                    (String) row.get("master_tracking_no"),
                    (String) row.get("last_event"),
                    eventTime == null ? null : eventTime.toString()
                ));
            }
        }
        return new CustomerApiResponses.SyncList(nos.size(), found, entries);
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
                entries.add(new StatusEntry(no, null, AccStatusMapping.NOT_FOUND, "未知"));
            } else {
                String status = (String) row.get("status");
                int code = AccStatusMapping.toAccCode(status);
                entries.add(new StatusEntry(no, status, code, AccStatusMapping.deliveryName(code)));
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

    /**
     * 对应 ACC act=Product — 列出客户可用产品（价格表）。
     * 取所有 ACTIVE 的 AR 价表，按 channel join 拿名称、币种、运输方式。
     */
    @Transactional(readOnly = true)
    public CustomerApiResponses.ProductList listProducts(CustomerApiPrincipal principal) {
        setTenant(principal);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              c.code            AS channel_code,
              c.name            AS channel_name,
              c.lane            AS lane,
              c.last_mile_method AS last_mile_method,
              c.active          AS active,
              rc.currency       AS currency
            FROM rate_cards rc
            JOIN channels c ON c.id = rc.channel_id
            WHERE rc.tenant_id = ?::uuid
              AND rc.side = 'AR'
              AND rc.status = 'ACTIVE'
              AND c.active = true
              AND (rc.effective_to IS NULL OR rc.effective_to >= CURRENT_DATE)
            ORDER BY c.code
            """, principal.tenantId());

        List<CustomerApiResponses.ProductEntry> data = rows.stream()
            .map(row -> new CustomerApiResponses.ProductEntry(
                (String) row.get("channel_code"),
                (String) row.get("channel_name"),
                (String) row.get("channel_code"),
                (String) row.get("channel_name"),
                (String) row.get("currency"),
                (String) row.get("lane"),
                (String) row.get("last_mile_method"),
                null,
                Boolean.TRUE.equals(row.get("active"))
            ))
            .toList();
        return new CustomerApiResponses.ProductList(data);
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
