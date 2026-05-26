package com.xqt.saas.stowage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.customerapi.CustomerApiPrincipal;
import com.xqt.saas.stowage.StowageRequests.SyncRequest;
import com.xqt.saas.stowage.StowageResponses.SyncResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 锁定 ACC doSync 的 11 项校验、跨客户检查、TrackNo 冲突、insert/update、attach/detach 行为。
 */
class StowageServiceTests {

    @Test
    void rejectsNoLengthOutOfRange() {
        StowageService service = build(mockRepo());
        assertThatThrownBy(() -> service.sync(principal(), sample(b -> b.no("AB"))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("订单号长度必须是6-30");
    }

    @Test
    void rejectsInvalidType() {
        StowageService service = build(mockRepo());
        assertThatThrownBy(() -> service.sync(principal(), sample(b -> b.type(2))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("必须为0或1");
    }

    @Test
    void rejectsZeroWeight() {
        StowageService service = build(mockRepo());
        assertThatThrownBy(() -> service.sync(principal(), sample(b -> b.weight(BigDecimal.ZERO))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Weight");
    }

    @Test
    void rejectsEmptyItems() {
        StowageService service = build(mockRepo());
        assertThatThrownBy(() -> service.sync(principal(), sample(b -> b.items(List.of()))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("当前配载暂未添加货件");
    }

    @Test
    void rejectsUnknownCategory() {
        StowageRepository repo = mockRepo();
        when(repo.findCategoryIdByName(any(), any())).thenReturn(null);
        StowageService service = build(repo);
        assertThatThrownBy(() -> service.sync(principal(), sample(b -> b.category("UnknownCat"))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("找不到指定的配载分类");
    }

    @Test
    void rejectsItemsBelongingToOtherCustomer() {
        StowageRepository repo = mockRepo();
        when(repo.findCategoryIdByName(any(), any())).thenReturn("cat-1");
        when(repo.findCartonsByTrackingNos(any(), any())).thenReturn(List.of(
            Map.of("carton_id", "c-1", "tracking_no", "SUB-1",
                "stowage_id", "", "customer_id", "OTHER-CUST", "shipment_no", "SHP-1")
        ));
        StowageService service = build(repo);
        assertThatThrownBy(() -> service.sync(principal(), sample(b -> {})))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("部分快件是其它客户的");
    }

    @Test
    void rejectsMissingTrackNos() {
        StowageRepository repo = mockRepo();
        when(repo.findCategoryIdByName(any(), any())).thenReturn("cat-1");
        // 输入 2 个，回来只 1 个 → 缺 1 个
        when(repo.findCartonsByTrackingNos(any(), any())).thenReturn(List.of(
            Map.of("carton_id", "c-1", "tracking_no", "SUB-1",
                "stowage_id", "", "customer_id", "cust-1", "shipment_no", "SHP-1")
        ));
        StowageService service = build(repo);
        assertThatThrownBy(() ->
            service.sync(principal(), sample(b -> b.items(List.of("SUB-1", "SUB-MISSING")))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("1");  // count 1
    }

    @Test
    void rejectsConflictingTrackNoBoundToOtherStowage() {
        StowageRepository repo = mockRepo();
        when(repo.findCategoryIdByName(any(), any())).thenReturn("cat-1");
        // 同一客户，但已经被另一个 stowage 绑了
        when(repo.findCartonsByTrackingNos(any(), any())).thenReturn(List.of(
            Map.of("carton_id", "c-1", "tracking_no", "SUB-1",
                "stowage_id", "other-stowage", "customer_id", "cust-1", "shipment_no", "SHP-1")
        ));
        when(repo.findStowageByNo(any(), any())).thenReturn(null);
        StowageService service = build(repo);
        assertThatThrownBy(() -> service.sync(principal(), sample(b -> {})))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("已经被其它的配载绑定");
    }

    @Test
    void createsNewStowageAndAttachesCartons() {
        StowageRepository repo = mockRepo();
        when(repo.findCategoryIdByName(any(), any())).thenReturn("cat-1");
        when(repo.findCartonsByTrackingNos(any(), any())).thenReturn(List.of(
            new java.util.HashMap<>(Map.of(
                "carton_id", "c-1", "tracking_no", "SUB-1",
                "customer_id", "cust-1", "shipment_no", "SHP-1"
            ))  // stowage_id 缺省 = null
        ));
        when(repo.findStowageByNo(any(), any())).thenReturn(null);
        when(repo.insertStowage(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
            any(), org.mockito.ArgumentMatchers.<Integer>any(), org.mockito.ArgumentMatchers.<Integer>any(),
            org.mockito.ArgumentMatchers.<Integer>any(),
            any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn("new-stowage-1");
        when(repo.attachCartons(any(), any(), any())).thenReturn(1);

        StowageService service = build(repo);
        SyncResult result = service.sync(principal(), sample(b -> {}));

        assertThat(result.stowageId()).isEqualTo("new-stowage-1");
        assertThat(result.created()).isTrue();
        assertThat(result.linkedItems()).isEqualTo(1);
        assertThat(result.detachedItems()).isZero();
    }

    @Test
    void rejectsExistingStowageOwnedByOtherCustomer() {
        StowageRepository repo = mockRepo();
        when(repo.findCategoryIdByName(any(), any())).thenReturn("cat-1");
        when(repo.findCartonsByTrackingNos(any(), any())).thenReturn(List.of(
            new java.util.HashMap<>(Map.of(
                "carton_id", "c-1", "tracking_no", "SUB-1",
                "customer_id", "cust-1", "shipment_no", "SHP-1"
            ))
        ));
        when(repo.findStowageByNo(any(), any())).thenReturn(new java.util.HashMap<>(Map.of(
            "id", "other-stowage",
            "customer_id", "OTHER-CUSTOMER",
            "stowage_no", "STO-001"
        )));
        StowageService service = build(repo);
        assertThatThrownBy(() -> service.sync(principal(), sample(b -> {})))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("已经有其它客户的相同单号的配载存在");
    }

    @Test
    void updatesExistingStowageAndDetachesRemovedItems() {
        StowageRepository repo = mockRepo();
        when(repo.findCategoryIdByName(any(), any())).thenReturn("cat-1");
        when(repo.findCartonsByTrackingNos(any(), any())).thenReturn(List.of(
            new java.util.HashMap<>(Map.of(
                "carton_id", "c-2", "tracking_no", "SUB-2",
                "customer_id", "cust-1", "shipment_no", "SHP-2"
            ))
        ));
        when(repo.findStowageByNo(any(), any())).thenReturn(new java.util.HashMap<>(Map.of(
            "id", "stowage-1",
            "customer_id", "cust-1",
            "stowage_no", "STO-001"
        )));
        // 旧 stowage 之前关联 c-old，本次没传 → 应该被解绑
        when(repo.findCartonsToDetach("tenant-1", "stowage-1", List.of("c-2")))
            .thenReturn(List.of("c-old"));
        when(repo.attachCartons(any(), any(), any())).thenReturn(1);
        when(repo.detachCartons(any(), any())).thenReturn(1);

        StowageService service = build(repo);
        SyncResult result = service.sync(principal(), sample(b -> b.items(List.of("SUB-2"))));

        assertThat(result.stowageId()).isEqualTo("stowage-1");
        assertThat(result.created()).isFalse();
        assertThat(result.linkedItems()).isEqualTo(1);
        assertThat(result.detachedItems()).isEqualTo(1);
    }

    // ===== helpers =====

    private StowageRepository mockRepo() {
        StowageRepository repo = mock(StowageRepository.class);
        return repo;
    }

    private StowageService build(StowageRepository repo) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(Object[].class))).thenReturn("tenant-1");
        return new StowageService(repo, jdbc);
    }

    private CustomerApiPrincipal principal() {
        return new CustomerApiPrincipal("cred-1", "tenant-1", "cust-1", "DOC-DEMO", "60000DEMO", "secret");
    }

    private SyncRequest sample(java.util.function.Consumer<SampleBuilder> tweak) {
        SampleBuilder b = new SampleBuilder();
        tweak.accept(b);
        return b.build();
    }

    private static class SampleBuilder {
        String no = "STO-001";
        String theDate = "2026-05-21";
        Integer type = 1;
        String category = "AIR";
        Integer count = 1;
        Integer piece = 1;
        Integer quantity = 1;
        BigDecimal weight = new BigDecimal("10.5");
        List<String> items = List.of("SUB-1");

        SampleBuilder no(String v) { this.no = v; return this; }
        SampleBuilder type(Integer v) { this.type = v; return this; }
        SampleBuilder weight(BigDecimal v) { this.weight = v; return this; }
        SampleBuilder items(List<String> v) { this.items = v; return this; }
        SampleBuilder category(String v) { this.category = v; return this; }

        SyncRequest build() {
            return new SyncRequest(no, theDate, type, category,
                count, piece, quantity, weight,
                null, null, null,
                null, null, null, null,
                null, "API", null, null,
                items);
        }
    }
}
