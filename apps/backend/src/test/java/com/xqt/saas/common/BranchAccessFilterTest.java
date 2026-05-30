package com.xqt.saas.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.xqt.saas.auth.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class BranchAccessFilterTest {

    private final BranchAccessFilter filter = new BranchAccessFilter();

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private void setPrincipal(List<String> roles, String branchId) {
        AuthPrincipal p = new AuthPrincipal(
            "u-1", "t-1", "T1", "alice", "Alice",
            roles, List.of(), 0L, "jti", branchId);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(p, null, List.of())
        );
    }

    @Test
    void noAuthReturnsEmpty() {
        var c = filter.forCurrent("o");
        assertThat(c.sql()).isEmpty();
        assertThat(c.params()).isEmpty();
    }

    @Test
    void adminGetsNoFilter() {
        setPrincipal(List.of("ADMIN"), "branch-shanghai");
        var c = filter.forCurrent("o");
        assertThat(c.sql()).isEmpty();
    }

    @Test
    void financeGetsNoFilter() {
        setPrincipal(List.of("FINANCE_MANAGER"), null);
        assertThat(filter.forCurrent("o").sql()).isEmpty();
        setPrincipal(List.of("FINANCE"), null);
        assertThat(filter.forCurrent("o").sql()).isEmpty();
    }

    @Test
    void branchManagerWithBranchIdFiltersByBranch() {
        setPrincipal(List.of("BRANCH_MANAGER"), "branch-sh");
        var c = filter.forCurrent("o");
        assertThat(c.sql()).contains("o.branch_id = ?");
        assertThat(c.params()).containsExactly("branch-sh");
    }

    @Test
    void branchManagerWithoutBranchIdFallsBackToNoFilter() {
        setPrincipal(List.of("BRANCH_MANAGER"), null);
        assertThat(filter.forCurrent("o").sql()).isEmpty();
        setPrincipal(List.of("BRANCH_MANAGER"), "");
        assertThat(filter.forCurrent("o").sql()).isEmpty();
    }

    @Test
    void salesmanFiltersByCustomerSubquery() {
        setPrincipal(List.of("SALESMAN"), null);
        var c = filter.forCurrent("o");
        assertThat(c.sql()).contains("customer_id IN");
        assertThat(c.sql()).contains("salesman_user_id");
        assertThat(c.params()).containsExactly("u-1");
    }

    @Test
    void forCustomersTableUsesDirectColumns() {
        setPrincipal(List.of("BRANCH_MANAGER"), "b-1");
        var c = filter.forCustomers("c");
        assertThat(c.sql()).contains("c.branch_id = ?");

        setPrincipal(List.of("SALESMAN"), null);
        c = filter.forCustomers("c");
        assertThat(c.sql()).contains("c.salesman_user_id = ?");
        assertThat(c.params()).containsExactly("u-1");
    }

    @Test
    void unknownRoleReturnsEmpty() {
        setPrincipal(List.of("WAREHOUSE_OP"), null);
        assertThat(filter.forCurrent("o").sql()).isEmpty();
    }
}
