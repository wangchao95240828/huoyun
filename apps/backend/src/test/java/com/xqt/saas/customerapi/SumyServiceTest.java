package com.xqt.saas.customerapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.customerapi.CustomerApiResponses.SubmitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * sumy.php 第三方推单服务测试：校验 / 映射 / 逐单成败隔离。
 */
class SumyServiceTest {
    private CustomerApiService customerApi;
    private SumyService sumy;

    @BeforeEach
    void setup() {
        customerApi = mock(CustomerApiService.class);
        sumy = new SumyService(customerApi);
    }

    private CustomerApiPrincipal principal() {
        return new CustomerApiPrincipal("cred-1", "tenant-1", "cust-1", "CUST001", "key", "secret");
    }

    private SubmitResult okSubmit(String trackNo) {
        return new SubmitResult("oid", "DOC-1", "ref", "ship-1", "SHP-1",
            trackNo, "MASTER-1", "SUBMITTED");
    }

    private Map<String, Object> order(String platformId, Object weight) {
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("Name", "John Doe");
        rec.put("Province", "CA");
        rec.put("City", "Los Angeles");
        rec.put("Address", "1 Main St");
        rec.put("PostCode", "90001");
        rec.put("CountryCode", "US");
        rec.put("Mobile", "13800000000");
        Map<String, Object> prod = new LinkedHashMap<>();
        prod.put("CustomsName", "Toy");
        prod.put("CustomsCnName", "玩具");
        prod.put("HSCode", "9503");
        prod.put("DeclareValue", 5.0);
        prod.put("Quantity", 2);
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("PlatformOrderID", platformId);
        o.put("Channel", "EU-AIR-UPS");
        o.put("Weight", weight);
        o.put("CustomsName", "Toy");
        o.put("CustomsNameCN", "玩具");
        o.put("CustomsValue", 10.0);
        o.put("Receiver", rec);
        o.put("ProductList", List.of(prod));
        return o;
    }

    private Map<String, Object> body(Object... orders) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("ApiUserName", "thirdparty");
        b.put("ApiToken", "x".repeat(32));
        b.put("OrderList", List.of(orders));
        return b;
    }

    // ─── 1. 正常推单：映射成 PreOrder + 复用 submitOrder，返回成功 + 转单号 ───
    @Test
    void pushSuccessMapsAndSubmits() {
        when(customerApi.submitOrder(any(), eq("ORDER123"))).thenReturn(okSubmit("1Z999AA10123456784"));

        Map<String, Object> out = sumy.push(principal(), body(order("ORDER123", 1500)));

        assertThat(out.get("Success")).isEqualTo(true);
        List<?> result = (List<?>) out.get("Result");
        assertThat(result).hasSize(1);
        Map<?, ?> r0 = (Map<?, ?>) result.get(0);
        assertThat(r0.get("PlatformOrderID")).isEqualTo("ORDER123");
        assertThat(r0.get("Success")).isEqualTo(true);
        assertThat(r0.get("TrackingNumber")).isEqualTo("1Z999AA10123456784");

        // 验证映射：重量 1500 克 → 1.500 kg，渠道 = Channel，国家 = US
        ArgumentCaptor<CustomerApiRequests.PreOrder> cap =
            ArgumentCaptor.forClass(CustomerApiRequests.PreOrder.class);
        verify(customerApi, times(1)).preOrder(any(), cap.capture());
        CustomerApiRequests.PreOrder pre = cap.getValue();
        assertThat(pre.no()).isEqualTo("ORDER123");
        assertThat(pre.product()).isEqualTo("EU-AIR-UPS");
        assertThat(pre.country()).isEqualTo("US");
        assertThat(pre.weight()).isEqualByComparingTo("1.500");
        assertThat(pre.declare()).hasSize(1);
        assertThat(pre.declare().get(0).get("name")).isEqualTo("Toy");
    }

    // ─── 2. 单号格式非法（太短）→ 不调下单，返回失败 ───
    @Test
    void pushRejectsInvalidOrderId() {
        Map<String, Object> out = sumy.push(principal(), body(order("ABC", 1500)));

        assertThat(out.get("Success")).isEqualTo(false);
        Map<?, ?> r0 = (Map<?, ?>) ((List<?>) out.get("Result")).get(0);
        assertThat(r0.get("Success")).isEqualTo(false);
        assertThat((String) r0.get("ErrorMessage")).contains("订单号长度必须是6-30");
        verify(customerApi, never()).preOrder(any(), any());
        verify(customerApi, never()).submitOrder(any(), any());
    }

    // ─── 3. 重量非法（<=0）→ 失败 ───
    @Test
    void pushRejectsNonPositiveWeight() {
        Map<String, Object> out = sumy.push(principal(), body(order("ORDER123", 0)));

        Map<?, ?> r0 = (Map<?, ?>) ((List<?>) out.get("Result")).get(0);
        assertThat(r0.get("Success")).isEqualTo(false);
        assertThat((String) r0.get("ErrorMessage")).contains("必须为大于零");
    }

    // ─── 4. 逐单成败隔离：第一单 submit 失败，第二单成功 ───
    @Test
    void pushIsolatesPerOrderFailures() {
        when(customerApi.submitOrder(any(), eq("ORDER001")))
            .thenThrow(ApiException.badRequest("客户余额不足"));
        when(customerApi.submitOrder(any(), eq("ORDER002")))
            .thenReturn(okSubmit("TRACK-002"));

        Map<String, Object> out = sumy.push(principal(),
            body(order("ORDER001", 1000), order("ORDER002", 2000)));

        assertThat(out.get("Success")).isEqualTo(false); // 整体非全成功
        List<?> result = (List<?>) out.get("Result");
        assertThat(result).hasSize(2);
        Map<?, ?> r0 = (Map<?, ?>) result.get(0);
        Map<?, ?> r1 = (Map<?, ?>) result.get(1);
        assertThat(r0.get("Success")).isEqualTo(false);
        assertThat((String) r0.get("ErrorMessage")).contains("余额不足");
        assertThat(r1.get("Success")).isEqualTo(true);
        assertThat(r1.get("TrackingNumber")).isEqualTo("TRACK-002");
    }

    // ─── 5. OrderList 为空 → 整体 400 ───
    @Test
    void pushRejectsEmptyOrderList() {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("OrderList", List.of());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> sumy.push(principal(), b))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("OrderList");
    }
}
