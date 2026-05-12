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
import com.xqt.saas.customerapi.CustomerApiResponses.PreOrderResult;
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
            CustomerApiController controller = new CustomerApiController(service, mock(com.xqt.saas.rates.RateEngine.class));
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
            CustomerApiController controller = new CustomerApiController(service, mock(com.xqt.saas.rates.RateEngine.class));
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

        CustomerApiService service = new CustomerApiService(repository, json, jdbc);
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
