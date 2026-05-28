package com.xqt.saas.customerapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiResponse;
import com.xqt.saas.common.ItemResponse;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.customerapi.CustomerApiResponses.BalanceLine;
import com.xqt.saas.customerapi.CustomerApiResponses.BalanceList;
import com.xqt.saas.customerapi.CustomerApiResponses.ChannelInfo;
import com.xqt.saas.customerapi.CustomerApiResponses.ChannelList;
import com.xqt.saas.customerapi.CustomerApiResponses.OrderDetailList;
import com.xqt.saas.customerapi.CustomerApiResponses.PreOrderResult;
import com.xqt.saas.customerapi.CustomerApiResponses.StatusEntry;
import com.xqt.saas.customerapi.CustomerApiResponses.StatusList;
import com.xqt.saas.customerapi.CustomerApiResponses.CancelResult;
import com.xqt.saas.customerapi.CustomerApiResponses.OrderDetail;
import com.xqt.saas.customerapi.CustomerApiResponses.SubmitResult;
import com.xqt.saas.customerapi.CustomerApiResponses.TrackingEvent;
import com.xqt.saas.customerapi.CustomerApiResponses.TrackingList;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

class CustomerApiContractTests {

    @Test
    void balanceEndpointReturnsListWrappedInItemResponse() {
        CustomerApiPrincipal principal = principal();
        CustomerApiService service = mock(CustomerApiService.class);
        when(service.queryBalance(principal)).thenReturn(new BalanceList(
            principal.customerCode(),
            List.of(
                new BalanceLine("CNY", "DOC-DEMO CNY 预付余额", new BigDecimal("10000.00")),
                new BalanceLine("USD", "DOC-DEMO USD 预付余额", new BigDecimal("500.00"))
            )
        ));
        withPrincipal(principal, () -> {
            CustomerApiController controller = new CustomerApiController(service, mock(com.xqt.saas.rates.RateEngine.class), mock(com.xqt.saas.tracking.TrackingAggregator.class));
            ApiResponse<ItemResponse<BalanceList>> response = controller.balance();

            assertThat(response.ok()).isTrue();
            assertThat(response.data()).isNotNull();
            assertThat(response.data().item().customerCode()).isEqualTo("DOC-DEMO");
            assertThat(response.data().item().data()).hasSize(2);
            assertThat(response.data().item().data().get(0).currency()).isEqualTo("CNY");
            assertThat(response.data().item().data().get(0).balance()).isEqualByComparingTo("10000.00");
        });
    }

    @Test
    void preOrderEndpointReturnsDraftOrderId() {
        CustomerApiPrincipal principal = principal();
        CustomerApiService service = mock(CustomerApiService.class);
        CustomerApiRequests.PreOrder body = new CustomerApiRequests.PreOrder(
            "ORDER-0001", "tok", "PROD-A", "US",
            new BigDecimal("1.5"), 1, new BigDecimal("0.001"),
            "CNY", Map.of(), Map.of(), Map.of(), List.of(), List.of(), Map.of()
        );
        when(service.preOrder(principal, body)).thenReturn(new PreOrderResult(
            "00000000-0000-0000-0000-000000000001", "DOC-20260512000000000-ORDER-0001", "DRAFT", "DOC-DEMO"
        ));
        withPrincipal(principal, () -> {
            CustomerApiController controller = new CustomerApiController(service, mock(com.xqt.saas.rates.RateEngine.class), mock(com.xqt.saas.tracking.TrackingAggregator.class));
            ApiResponse<ItemResponse<PreOrderResult>> response = controller.preOrder(body);

            assertThat(response.ok()).isTrue();
            assertThat(response.data().item().orderId()).isNotBlank();
            assertThat(response.data().item().status()).isEqualTo("DRAFT");
        });
    }

    @Test
    void comparisonCaseAccBalanceResponseShape() {
        // ACC 旧响应：{ result: 0, data: [{ Currency, Code, Symbol, Balance }, ...] }
        // 新系统：ApiResponse.ok = true, data.item = { customerCode, data: [{ currency, accountName, balance }] }
        // 该测试锁定字段语义映射，避免后续重构改坏旧客户端契约。
        CustomerApiRepository repository = mock(CustomerApiRepository.class);
        JsonSupport json = new JsonSupport(new com.fasterxml.jackson.databind.ObjectMapper());
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(Object[].class))).thenReturn("tenant-1");
        when(repository.findBalances("tenant-1", "cust-1")).thenReturn(List.of(
            Map.of("currency", "CNY", "balance", new BigDecimal("10000.00"),
                "account_name", "DOC-DEMO CNY 预付余额", "status", "ACTIVE"),
            Map.of("currency", "USD", "balance", new BigDecimal("500.00"),
                "account_name", "DOC-DEMO USD 预付余额", "status", "ACTIVE")
        ));

        CustomerApiService service = new CustomerApiService(repository, json, jdbc, new CarrierGatewayRegistry(java.util.List.of(new NoopCarrierGateway()), new NoopCarrierGateway(), jdbc), org.mockito.Mockito.mock(com.xqt.saas.rates.RateEngine.class));
        CustomerApiPrincipal principal = new CustomerApiPrincipal(
            "cred-1", "tenant-1", "cust-1", "DOC-DEMO", "60000DEMO", "secret"
        );
        BalanceList result = service.queryBalance(principal);

        assertThat(result.customerCode()).isEqualTo("DOC-DEMO");
        assertThat(result.data()).extracting(BalanceLine::currency).containsExactly("CNY", "USD");
        assertThat(result.data()).extracting(BalanceLine::balance)
            .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
            .containsExactly(new BigDecimal("10000.00"), new BigDecimal("500.00"));
    }

    @Test
    void statusEndpointMapsOrderStatusToAccCode() {
        // 对应 ACC act=Status：旧响应 { Express: { ORDER_NO: statusCode } }
        // 新系统 StatusList.express[].statusCode 保留同样的整数语义，找不到返回 -1。
        CustomerApiRepository repository = mock(CustomerApiRepository.class);
        JsonSupport json = new JsonSupport(new com.fasterxml.jackson.databind.ObjectMapper());
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(Object[].class))).thenReturn("tenant-1");
        when(repository.findOrderStatuses("tenant-1", "cust-1", List.of("ORD-001", "ORD-002", "MISSING")))
            .thenReturn(List.of(
                Map.of("order_no", "DOC-ORD-001", "customer_ref", "ORD-001", "status", "DRAFT"),
                Map.of("order_no", "DOC-ORD-002", "customer_ref", "ORD-002", "status", "CANCELLED")
            ));

        CustomerApiService service = new CustomerApiService(repository, json, jdbc, new CarrierGatewayRegistry(java.util.List.of(new NoopCarrierGateway()), new NoopCarrierGateway(), jdbc), org.mockito.Mockito.mock(com.xqt.saas.rates.RateEngine.class));
        StatusList result = service.queryStatus(
            principal(),
            new CustomerApiRequests.OrderRefList(null, List.of("ORD-001", "ORD-002", "MISSING"))
        );

        assertThat(result.express()).extracting(StatusEntry::no)
            .containsExactly("ORD-001", "ORD-002", "MISSING");
        assertThat(result.express()).extracting(StatusEntry::statusCode)
            .containsExactly(0, 8, AccStatusMapping.NOT_FOUND);
    }

    @Test
    void queryEndpointReturnsAccCompatFromMetadata() {
        // 对应 ACC act=Query：当前订单还在 orders 草稿阶段，metadata.acc_compat 里保留客户提交的字段。
        CustomerApiRepository repository = mock(CustomerApiRepository.class);
        JsonSupport json = new JsonSupport(new com.fasterxml.jackson.databind.ObjectMapper());
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(Object[].class))).thenReturn("tenant-1");
        String metadataJson = """
            {"acc_compat":{"country":"US","product":"PROD-A","weight":"1.5","piece":1,"volume":"0.001","currency":"CNY","receiver":{"Consignee":"Alice","City":"NYC"},"declare":[{"name":"Toy","cnName":"玩具","price":"5.00","quantity":"2"}]}}
            """;
        when(repository.findOrdersDetail("tenant-1", "cust-1", List.of("ORD-001")))
            .thenReturn(List.of(
                Map.of("order_id", "00000000-0000-0000-0000-000000000001",
                    "order_no", "DOC-ORD-001",
                    "customer_ref", "ORD-001",
                    "status", "DRAFT",
                    "metadata", metadataJson,
                    "created_at", java.sql.Timestamp.valueOf("2026-05-12 10:00:00"))
            ));

        CustomerApiService service = new CustomerApiService(repository, json, jdbc, new CarrierGatewayRegistry(java.util.List.of(new NoopCarrierGateway()), new NoopCarrierGateway(), jdbc), org.mockito.Mockito.mock(com.xqt.saas.rates.RateEngine.class));
        OrderDetailList result = service.queryDetail(
            principal(),
            new CustomerApiRequests.OrderRefList("ORD-001", null)
        );

        assertThat(result.express()).containsKey("ORD-001");
        var detail = result.express().get("ORD-001");
        assertThat(detail.status()).isEqualTo("DRAFT");
        assertThat(detail.statusCode()).isZero();
        assertThat(detail.country()).isEqualTo("US");
        assertThat(detail.productCode()).isEqualTo("PROD-A");
        assertThat(detail.weight()).isEqualByComparingTo("1.5");
        assertThat(detail.piece()).isEqualTo(1);
        assertThat(detail.receiver()).containsEntry("Consignee", "Alice");
        assertThat(detail.declare()).hasSize(1);
        assertThat(detail.declare().get(0).cnName()).isEqualTo("玩具");
    }

    @Test
    void trackingEndpointAggregatesEventsByShipment() {
        // 对应 ACC act=Track：旧响应 { ReferenceNo, TrackNo, TrackStatus, Track: [{Time,Location,Activity}] }
        // 新系统从 tracking_events 表读取，按 event_time 升序输出。
        CustomerApiRepository repository = mock(CustomerApiRepository.class);
        JsonSupport json = new JsonSupport(new com.fasterxml.jackson.databind.ObjectMapper());
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(Object[].class))).thenReturn("tenant-1");
        when(repository.findShipmentsForTracking("tenant-1", "cust-1", List.of("ORD-001")))
            .thenReturn(List.of(
                Map.of("shipment_id", "11111111-1111-1111-1111-111111111111",
                    "shipment_no", "S-001",
                    "customer_ref", "ORD-001",
                    "status", "IN_TRANSIT",
                    "first_tracking_no", "1Z999AA10123456784")
            ));
        when(repository.findTrackingEvents("tenant-1", List.of("11111111-1111-1111-1111-111111111111")))
            .thenReturn(List.of(
                Map.<String, Object>of(
                    "shipment_id", "11111111-1111-1111-1111-111111111111",
                    "event_time", java.sql.Timestamp.valueOf("2026-05-13 08:00:00"),
                    "raw_status", "Picked up",
                    "normalized_status", "CREATED",
                    "location", "SHENZHEN",
                    "source", "CARRIER_API",
                    "tracking_no", "1Z999AA10123456784"
                ),
                Map.<String, Object>of(
                    "shipment_id", "11111111-1111-1111-1111-111111111111",
                    "event_time", java.sql.Timestamp.valueOf("2026-05-15 14:30:00"),
                    "raw_status", "In transit",
                    "normalized_status", "IN_TRANSIT",
                    "location", "HKG",
                    "source", "CARRIER_API",
                    "tracking_no", "1Z999AA10123456784"
                )
            ));

        CustomerApiService service = new CustomerApiService(repository, json, jdbc, new CarrierGatewayRegistry(java.util.List.of(new NoopCarrierGateway()), new NoopCarrierGateway(), jdbc), org.mockito.Mockito.mock(com.xqt.saas.rates.RateEngine.class));
        TrackingList result = service.queryTracking(
            principal(),
            new CustomerApiRequests.OrderRefList(null, List.of("ORD-001"))
        );

        var detail = result.data().get("ORD-001");
        assertThat(detail).isNotNull();
        assertThat(detail.referenceNo()).isEqualTo("ORD-001");
        assertThat(detail.trackNo()).isEqualTo("1Z999AA10123456784");
        assertThat(detail.trackStatus()).isEqualTo("IN_TRANSIT");
        assertThat(detail.track()).hasSize(2);
        assertThat(detail.track()).extracting(TrackingEvent::activity)
            .containsExactly("Picked up", "In transit");
    }

    @Test
    void trackingEndpointReturnsEmptyTrackWhenNoShipmentYet() {
        // Draft 阶段 shipments 还没有数据，Track 应返回空数组而不是 404。
        CustomerApiRepository repository = mock(CustomerApiRepository.class);
        JsonSupport json = new JsonSupport(new com.fasterxml.jackson.databind.ObjectMapper());
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(Object[].class))).thenReturn("tenant-1");
        when(repository.findShipmentsForTracking("tenant-1", "cust-1", List.of("ORD-NEW")))
            .thenReturn(List.of());

        CustomerApiService service = new CustomerApiService(repository, json, jdbc, new CarrierGatewayRegistry(java.util.List.of(new NoopCarrierGateway()), new NoopCarrierGateway(), jdbc), org.mockito.Mockito.mock(com.xqt.saas.rates.RateEngine.class));
        TrackingList result = service.queryTracking(
            principal(),
            new CustomerApiRequests.OrderRefList("ORD-NEW", null)
        );

        assertThat(result.data()).containsKey("ORD-NEW");
        assertThat(result.data().get("ORD-NEW").track()).isEmpty();
        assertThat(result.data().get("ORD-NEW").trackNo()).isNull();
    }

    @Test
    void channelsEndpointReturnsActiveChannels() {
        // 对应 ACC act=Product / act=Channel：旧响应 { Product: [{ Name, Code, Logistics }] }
        // 新系统返回 ChannelList，code/name/lane 都保留。
        CustomerApiRepository repository = mock(CustomerApiRepository.class);
        JsonSupport json = new JsonSupport(new com.fasterxml.jackson.databind.ObjectMapper());
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(Object[].class))).thenReturn("tenant-1");
        when(repository.findActiveChannels("tenant-1")).thenReturn(List.of(
            Map.of("code", "EU-AIR-UPS", "name", "欧线 UPS",
                "lane", "EU", "last_mile_method", "UPS", "active", true)
        ));

        CustomerApiService service = new CustomerApiService(repository, json, jdbc, new CarrierGatewayRegistry(java.util.List.of(new NoopCarrierGateway()), new NoopCarrierGateway(), jdbc), org.mockito.Mockito.mock(com.xqt.saas.rates.RateEngine.class));
        ChannelList result = service.listChannels(principal());

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0))
            .extracting(ChannelInfo::code, ChannelInfo::name, ChannelInfo::active)
            .containsExactly("EU-AIR-UPS", "欧线 UPS", true);
    }

    @Test
    void submitTransitionsDraftToSubmittedAndCreatesShipment() {
        // 对应 ACC act=Submit：DRAFT → SUBMITTED，落 shipments/cartons/declarations，调 carrier gateway 取号。
        CustomerApiRepository repository = mock(CustomerApiRepository.class);
        JsonSupport json = new JsonSupport(new com.fasterxml.jackson.databind.ObjectMapper());
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(Object[].class))).thenReturn("tenant-1");

        String metadataJson = """
            {"acc_compat":{"product":"EU-AIR-UPS","country":"US","weight":"1.5","piece":1,"currency":"CNY","declare":[{"name":"Toy","price":"5.00","quantity":"2","hsCode":"9503"}]}}
            """;
        when(repository.findOrderForCustomerApi("tenant-1", "cust-1", "ORD-S1"))
            .thenReturn(new java.util.HashMap<>(Map.of(
                "order_id", "00000000-0000-0000-0000-000000000001",
                "order_no", "DOC-ORD-S1",
                "customer_ref", "ORD-S1",
                "status", "DRAFT",
                "metadata", metadataJson
            )));
        when(repository.findChannelIdByCode("tenant-1", "EU-AIR-UPS")).thenReturn("ch-1");
        when(repository.insertShipment(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn("ship-1");
        when(repository.markOrderSubmitted("00000000-0000-0000-0000-000000000001")).thenReturn(1);

        CustomerApiService service = new CustomerApiService(repository, json, jdbc, new CarrierGatewayRegistry(java.util.List.of(new NoopCarrierGateway()), new NoopCarrierGateway(), jdbc), org.mockito.Mockito.mock(com.xqt.saas.rates.RateEngine.class));
        SubmitResult result = service.submitOrder(principal(), "ORD-S1");

        assertThat(result.status()).isEqualTo("SUBMITTED");
        assertThat(result.shipmentId()).isEqualTo("ship-1");
        assertThat(result.shipmentNo()).isEqualTo("SHP-DOC-ORD-S1");
        assertThat(result.trackingNo()).startsWith("NOOP-");
    }

    @Test
    void submitRejectsNonDraftOrders() {
        CustomerApiRepository repository = mock(CustomerApiRepository.class);
        JsonSupport json = new JsonSupport(new com.fasterxml.jackson.databind.ObjectMapper());
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(Object[].class))).thenReturn("tenant-1");
        when(repository.findOrderForCustomerApi("tenant-1", "cust-1", "ORD-X"))
            .thenReturn(new java.util.HashMap<>(Map.of(
                "order_id", "00000000-0000-0000-0000-000000000002",
                "order_no", "DOC-ORD-X",
                "customer_ref", "ORD-X",
                "status", "SUBMITTED",
                "metadata", "{}"
            )));

        CustomerApiService service = new CustomerApiService(repository, json, jdbc, new CarrierGatewayRegistry(java.util.List.of(new NoopCarrierGateway()), new NoopCarrierGateway(), jdbc), org.mockito.Mockito.mock(com.xqt.saas.rates.RateEngine.class));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.submitOrder(principal(), "ORD-X"))
            .isInstanceOf(com.xqt.saas.common.ApiException.class)
            .hasMessageContaining("only DRAFT");
    }

    @Test
    void modifyMergesMetadataOnlyOnDraft() {
        // 对应 ACC act=Modify：只能改 DRAFT；未传字段保留原值，传了的字段覆盖到 metadata.acc_compat。
        CustomerApiRepository repository = mock(CustomerApiRepository.class);
        JsonSupport json = new JsonSupport(new com.fasterxml.jackson.databind.ObjectMapper());
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(Object[].class))).thenReturn("tenant-1");

        java.util.concurrent.atomic.AtomicReference<String> savedMetadata = new java.util.concurrent.atomic.AtomicReference<>();
        when(repository.findOrderForCustomerApi("tenant-1", "cust-1", "ORD-M1"))
            .thenReturn(new java.util.HashMap<>(Map.of(
                "order_id", "00000000-0000-0000-0000-000000000003",
                "order_no", "DOC-ORD-M1",
                "customer_ref", "ORD-M1",
                "status", "DRAFT",
                "metadata", "{\"acc_compat\":{\"country\":\"US\",\"weight\":\"1.0\"}}"
            )));
        org.mockito.Mockito.doAnswer(inv -> {
            savedMetadata.set(inv.getArgument(1, String.class));
            return 1;
        }).when(repository).updateOrderMetadata(org.mockito.ArgumentMatchers.eq("00000000-0000-0000-0000-000000000003"), any(String.class));
        when(repository.findOrdersDetail("tenant-1", "cust-1", List.of("ORD-M1")))
            .thenReturn(List.of(new java.util.HashMap<>(Map.of(
                "order_id", "00000000-0000-0000-0000-000000000003",
                "order_no", "DOC-ORD-M1",
                "customer_ref", "ORD-M1",
                "status", "DRAFT",
                "metadata", savedMetadataOnRead(savedMetadata),
                "created_at", java.sql.Timestamp.valueOf("2026-05-13 09:00:00")
            ))));

        CustomerApiService service = new CustomerApiService(repository, json, jdbc, new CarrierGatewayRegistry(java.util.List.of(new NoopCarrierGateway()), new NoopCarrierGateway(), jdbc), org.mockito.Mockito.mock(com.xqt.saas.rates.RateEngine.class));
        OrderDetail result = service.modifyOrder(principal(), "ORD-M1",
            new CustomerApiRequests.ModifyOrder(null, "GB", new BigDecimal("2.5"), null, null,
                null, null, null, null, null, "rush"));

        // 保存的 metadata 含合并结果：country 改为 GB，weight 改为 2.5，remark=rush
        assertThat(savedMetadata.get()).contains("\"country\":\"GB\"");
        assertThat(savedMetadata.get()).contains("\"weight\":\"2.5\"");
        assertThat(savedMetadata.get()).contains("\"remark\":\"rush\"");
        assertThat(result).isNotNull();
    }

    @Test
    void cancelOnDraftDoesNotTouchShipments() {
        CustomerApiRepository repository = mock(CustomerApiRepository.class);
        JsonSupport json = new JsonSupport(new com.fasterxml.jackson.databind.ObjectMapper());
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(Object[].class))).thenReturn("tenant-1");
        when(repository.findOrderForCustomerApi("tenant-1", "cust-1", "ORD-C1"))
            .thenReturn(new java.util.HashMap<>(Map.of(
                "order_id", "id-c1",
                "order_no", "DOC-ORD-C1",
                "customer_ref", "ORD-C1",
                "status", "DRAFT",
                "metadata", "{}"
            )));
        when(repository.markOrderCancelled("id-c1")).thenReturn(1);

        CustomerApiService service = new CustomerApiService(repository, json, jdbc, new CarrierGatewayRegistry(java.util.List.of(new NoopCarrierGateway()), new NoopCarrierGateway(), jdbc), org.mockito.Mockito.mock(com.xqt.saas.rates.RateEngine.class));
        CancelResult result = service.cancelOrder(principal(), "ORD-C1");

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.previousStatus()).isEqualTo("DRAFT");
        assertThat(result.shipmentMarked()).isFalse();
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
            .markShipmentExceptionForOrder(any(), any());
    }

    @Test
    void cancelOnSubmittedAlsoMarksShipmentException() {
        CustomerApiRepository repository = mock(CustomerApiRepository.class);
        JsonSupport json = new JsonSupport(new com.fasterxml.jackson.databind.ObjectMapper());
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(Object[].class))).thenReturn("tenant-1");
        when(repository.findOrderForCustomerApi("tenant-1", "cust-1", "ORD-C2"))
            .thenReturn(new java.util.HashMap<>(Map.of(
                "order_id", "id-c2",
                "order_no", "DOC-ORD-C2",
                "customer_ref", "ORD-C2",
                "status", "SUBMITTED",
                "metadata", "{}"
            )));
        when(repository.markOrderCancelled("id-c2")).thenReturn(1);
        when(repository.markShipmentExceptionForOrder("tenant-1", "ORD-C2")).thenReturn(1);

        CustomerApiService service = new CustomerApiService(repository, json, jdbc, new CarrierGatewayRegistry(java.util.List.of(new NoopCarrierGateway()), new NoopCarrierGateway(), jdbc), org.mockito.Mockito.mock(com.xqt.saas.rates.RateEngine.class));
        CancelResult result = service.cancelOrder(principal(), "ORD-C2");

        assertThat(result.previousStatus()).isEqualTo("SUBMITTED");
        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.shipmentMarked()).isTrue();
    }

    private String savedMetadataOnRead(java.util.concurrent.atomic.AtomicReference<String> ref) {
        // 读详情之前 modifyOrder 已经写过 metadata，这里返回最新值
        return ref.get() != null ? ref.get() : "{}";
    }

    private CustomerApiPrincipal principal() {
        return new CustomerApiPrincipal("cred-1", "tenant-1", "cust-1", "DOC-DEMO", "60000DEMO", "secret");
    }

    private void withPrincipal(CustomerApiPrincipal principal, Runnable body) {
        SecurityContext previous = SecurityContextHolder.getContext();
        try {
            SecurityContext ctx = new SecurityContextImpl();
            ctx.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER_API"))
            ));
            SecurityContextHolder.setContext(ctx);
            body.run();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
