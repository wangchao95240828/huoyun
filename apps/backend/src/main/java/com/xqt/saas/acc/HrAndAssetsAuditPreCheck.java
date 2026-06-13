package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.framework.audit.AuditSideEffect;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * HR / 资产 类审核前置校验，对齐 ACC Assets.php / Wage.php / Borrowing.php：
 *  - 金额必须大于零（ACC L276/L1554/L2300 等）
 *  - 找不到币种（涉及金额的单据必须有币种）
 *  - 资产/工资/借支单据 entity 关联检查
 */
@Component
public class HrAndAssetsAuditPreCheck implements AuditSideEffect {
    private final JdbcTemplate jdbc;

    public HrAndAssetsAuditPreCheck(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean supports(String table) {
        return "acc_wages".equals(table)
            || "acc_borrowings".equals(table)
            || "acc_assets".equals(table)
            || "acc_dividends".equals(table)
            || "acc_expenses".equals(table)
            || "acc_transfers".equals(table)
            || "acc_reparations".equals(table);
    }

    @Override
    public void onAudited(String table, String entityId, String tenantId, String actorName) {
        // 资金副作用走各自的 SideEffect (例如 acc_finance_txns 通过 FinanceTxnAuditSideEffect)
    }

    @Override
    public void beforeAudit(String table, String entityId, String tenantId, String actorName) {
        // 通用：amount > 0
        String amountCol = pickAmountColumn(table);
        String currencyCol = hasCurrency(table) ? ", currency" : "";
        Map<String, Object> rec;
        try {
            rec = jdbc.queryForMap(
                "SELECT " + amountCol + " AS amount" + currencyCol
                + " FROM " + table + " WHERE id = ?::uuid",
                entityId);
        } catch (EmptyResultDataAccessException ex) {
            throw ApiException.notFound("找不到该" + zhTable(table));
        }
        BigDecimal amount = (BigDecimal) rec.get("amount");
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.badRequest(zhTable(table) + "金额必须大于零");
        }
        if (hasCurrency(table)) {
            String currency = (String) rec.get("currency");
            if (currency == null || currency.length() != 3) {
                throw ApiException.badRequest("找不到" + zhTable(table) + "的币种");
            }
        }
    }

    private String pickAmountColumn(String table) {
        return switch (table) {
            case "acc_wages"      -> "total";
            case "acc_reparations"-> "apply_amount";
            default               -> "amount";
        };
    }

    private boolean hasCurrency(String table) {
        return true;
    }

    private String zhTable(String table) {
        return switch (table) {
            case "acc_wages" -> "工资";
            case "acc_borrowings" -> "借支";
            case "acc_assets" -> "固定资产";
            case "acc_dividends" -> "分红";
            case "acc_expenses" -> "支出";
            case "acc_transfers" -> "转账";
            case "acc_reparations" -> "赔偿";
            default -> table;
        };
    }
}
