package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 财务/会计模块通用的多条件 ad-hoc 过滤构造器。
 * 给 bills / receiveds / payments / costs / charges 4-5 个 controller 复用，
 * 避免每个 controller 各自手写一份。
 */
public final class AccFinanceFilters {
    private AccFinanceFilters() {}

    public static class Built {
        public final String sql;
        public final List<Object> params;
        Built(String sql, List<Object> params) { this.sql = sql; this.params = params; }
    }

    /**
     * 通用 4 多选 + 时间 + 金额：customerIds / currencies / statuses / auditStatuses
     * + createdFrom/To + amountFrom/To。每个 controller 传自己表里的列名。
     */
    public static Built build(
            String customerIdColumn,   // 如 "i.customer_id" / "p.customer_id" / "r.customer_id"
            String currencyColumn,     // 如 "i.currency"
            String statusColumn,       // 如 "i.status"
            String auditStatusColumn,  // 如 "i.audit_status"
            String createdAtColumn,    // 如 "i.issued_at" / "p.created_at" / "r.received_at"
            String amountColumn,       // 如 "i.total_amount" / "p.amount" / "r.amount"
            String customerIds, String currencies, String statuses, String auditStatuses,
            String createdFrom, String createdTo, String amountFrom, String amountTo) {
        StringBuilder sb = new StringBuilder();
        List<Object> p = new ArrayList<>();
        List<String> cidList = AccOrdersController.splitCsv(customerIds);
        List<String> curList = AccOrdersController.splitCsv(currencies);
        List<String> stList  = AccOrdersController.splitCsv(statuses);
        List<String> auList  = AccOrdersController.splitCsv(auditStatuses);
        if (!cidList.isEmpty()) {
            sb.append(" AND ").append(customerIdColumn).append("::text IN (")
              .append(AccOrdersController.qMarks(cidList.size())).append(")");
            p.addAll(cidList);
        }
        if (!curList.isEmpty()) {
            sb.append(" AND ").append(currencyColumn).append(" IN (")
              .append(AccOrdersController.qMarks(curList.size())).append(")");
            p.addAll(curList);
        }
        if (!stList.isEmpty()) {
            sb.append(" AND ").append(statusColumn).append(" IN (")
              .append(AccOrdersController.qMarks(stList.size())).append(")");
            p.addAll(stList);
        }
        if (!auList.isEmpty()) {
            sb.append(" AND ").append(auditStatusColumn).append(" IN (")
              .append(AccOrdersController.qMarks(auList.size())).append(")");
            p.addAll(auList);
        }
        if (createdFrom != null && !createdFrom.isBlank()) {
            sb.append(" AND ").append(createdAtColumn).append(" >= ?::date");
            p.add(createdFrom);
        }
        if (createdTo != null && !createdTo.isBlank()) {
            sb.append(" AND ").append(createdAtColumn).append(" < (?::date + 1)");
            p.add(createdTo);
        }
        try {
            if (amountFrom != null && !amountFrom.isBlank()) {
                sb.append(" AND ").append(amountColumn).append(" >= ?");
                p.add(new BigDecimal(amountFrom));
            }
            if (amountTo != null && !amountTo.isBlank()) {
                sb.append(" AND ").append(amountColumn).append(" <= ?");
                p.add(new BigDecimal(amountTo));
            }
        } catch (NumberFormatException ignored) { /* 非法数字忽略 */ }
        return new Built(sb.toString(), p);
    }
}
