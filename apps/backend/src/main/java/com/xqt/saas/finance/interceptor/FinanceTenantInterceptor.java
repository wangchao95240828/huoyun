package com.xqt.saas.finance.interceptor;

import com.xqt.saas.auth.AuthPrincipal;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Intercepts({
    @Signature(type = Executor.class, method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(type = Executor.class, method = "update",
        args = {MappedStatement.class, Object.class})
})
public class FinanceTenantInterceptor implements Interceptor {

    private static final String SET_TENANT_SQL =
        "select set_config('app.current_tenant_id', ?, true)";

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        applyTenant(invocation);
        return invocation.proceed();
    }

    private void applyTenant(Invocation invocation) throws SQLException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            return;
        }
        Executor executor = (Executor) invocation.getTarget();
        Transaction tx = executor.getTransaction();
        Connection conn = tx.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(SET_TENANT_SQL)) {
            ps.setString(1, principal.tenantId());
            ps.execute();
        }
    }
}
