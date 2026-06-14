package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.framework.cascade.CascadeChecker;
import com.xqt.saas.framework.fieldgate.FieldGate;
import com.xqt.saas.framework.money.MoneySnapshotService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 6 个财务流水 controller 的公共基类，共享 `acc_finance_txns` 表。
 * 用 side ('CUSTOMER' / 'SUPPLIER') 和 txn_type ('ADJUST' / 'REFUND' / 'REBATE') 区分。
 *
 * 对应 ACC 旧表：
 *   CAdjust/SAdjust   → side, txn_type='ADJUST' (调账)
 *   CRefund/SRefund   → side, txn_type='REFUND' (退款)
 *   Rebate (c/s)      → side, txn_type='REBATE' (返利)
 */
abstract class AccFinanceTxnsBase {
    protected static final String TABLE = "acc_finance_txns";

    protected final JdbcTemplate jdbc;
    protected final JsonSupport json;
    protected final CascadeChecker cascadeChecker;
    protected final FieldGate fieldGate;
    protected final MoneySnapshotService moneySnapshotService;
    protected final com.xqt.saas.common.BranchAccessFilter branchAccess;

    protected AccFinanceTxnsBase(JdbcTemplate jdbc, JsonSupport json,
                                 CascadeChecker cascadeChecker, FieldGate fieldGate,
                                 MoneySnapshotService moneySnapshotService,
                                 com.xqt.saas.common.BranchAccessFilter branchAccess) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
        this.moneySnapshotService = moneySnapshotService;
        this.branchAccess = branchAccess;
    }

    /** 'CUSTOMER' / 'SUPPLIER' */
    protected abstract String side();

    /** 'ADJUST' / 'REFUND' / 'REBATE' */
    protected abstract String txnType();

    /** 中文标签，用于错误消息（e.g. "客户调账"、"物流商退款"）。 */
    protected abstract String labelCn();

    protected Map<String, Object> listImpl(Integer page, Integer pageSize, String keyword,
                                           String dateFrom, String dateTo) {
        return listImpl(page, pageSize, keyword, dateFrom, dateTo, null);
    }

    protected Map<String, Object> listImpl(Integer page, Integer pageSize, String keyword,
                                           String dateFrom, String dateTo, String status) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            String auditStatus = (status == null || status.isBlank()) ? null : status;
            // CUSTOMER 侧按当前用户 branch/sales 过滤；SUPPLIER 侧不过滤
            var access = "CUSTOMER".equals(side())
                ? branchAccess.forCurrentViaCustomer("t")
                : com.xqt.saas.common.BranchAccessFilter.AccessClause.empty();

            java.util.List<Object> countParams = new java.util.ArrayList<>(java.util.Arrays.asList(
                side(), txnType(),
                search, search, search, search,
                dateFrom, dateFrom, dateTo, dateTo,
                auditStatus, auditStatus));
            countParams.addAll(access.params());
            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM acc_finance_txns t"
                + " LEFT JOIN customers c ON c.id = t.customer_id"
                + " LEFT JOIN partners  p ON p.id = t.partner_id"
                + " WHERE t.side = ? AND t.txn_type = ?"
                + "   AND (?::text IS NULL OR t.txn_no ILIKE ? OR c.name ILIKE ? OR p.name ILIKE ?)"
                + "   AND (?::date IS NULL OR t.the_date >= ?::date)"
                + "   AND (?::date IS NULL OR t.the_date < (?::date + 1))"
                + "   AND (?::text IS NULL OR t.audit_status = ?)"
                + access.sql(),
                Long.class, countParams.toArray());

            java.util.List<Object> listParams = new java.util.ArrayList<>(java.util.Arrays.asList(
                side(), txnType(),
                search, search, search, search,
                dateFrom, dateFrom, dateTo, dateTo,
                auditStatus, auditStatus));
            listParams.addAll(access.params());
            listParams.add(limit);
            listParams.add(offset);
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT t.id::text AS id, t.txn_no, t.the_date, t.amount, t.currency,"
                + " t.poundage, t.fx_rate, t.pay_currency, t.bank_id::text AS bank_id,"
                + " t.reason, t.remark, t.status, t.add_name, t.created_at,"
                + " t.audit_status, t.audited_at, t.audit_name,"
                + " c.name AS customer_name, p.name AS partner_name,"
                // 双币种快照（如有）: 用 entity_type/entity_id 关联
                + " ers.target_currency AS target_currency, ers.target_amount AS target_amount,"
                // 汇款信息（批量汇款后写入）
                + " t.financial_account_id::text AS financial_account_id,"
                + " fa.account_name AS financial_account_name,"
                + " t.remitted_at, t.remit_name"
                + " FROM acc_finance_txns t"
                + " LEFT JOIN customers c ON c.id = t.customer_id"
                + " LEFT JOIN partners  p ON p.id = t.partner_id"
                + " LEFT JOIN exchange_rate_snapshots ers"
                + "        ON ers.entity_type = 'acc_finance_txns' AND ers.entity_id = t.id::text"
                + " LEFT JOIN financial_accounts fa ON fa.id = t.financial_account_id"
                + " WHERE t.side = ? AND t.txn_type = ?"
                + "   AND (?::text IS NULL OR t.txn_no ILIKE ? OR c.name ILIKE ? OR p.name ILIKE ?)"
                + "   AND (?::date IS NULL OR t.the_date >= ?::date)"
                + "   AND (?::date IS NULL OR t.the_date < (?::date + 1))"
                + "   AND (?::text IS NULL OR t.audit_status = ?)"
                + access.sql()
                + " ORDER BY t.created_at DESC LIMIT ? OFFSET ?",
                listParams.toArray());

            // 合计行: 按当前过滤条件计算 SUM(amount)（不分页）
            java.util.List<Object> sumParams = new java.util.ArrayList<>(java.util.Arrays.asList(
                side(), txnType(),
                search, search, search, search,
                dateFrom, dateFrom, dateTo, dateTo,
                auditStatus, auditStatus));
            sumParams.addAll(access.params());
            java.math.BigDecimal sumAmount = jdbc.queryForObject(
                "SELECT coalesce(sum(t.amount), 0) FROM acc_finance_txns t"
                + " LEFT JOIN customers c ON c.id = t.customer_id"
                + " LEFT JOIN partners  p ON p.id = t.partner_id"
                + " WHERE t.side = ? AND t.txn_type = ?"
                + "   AND (?::text IS NULL OR t.txn_no ILIKE ? OR c.name ILIKE ? OR p.name ILIKE ?)"
                + "   AND (?::date IS NULL OR t.the_date >= ?::date)"
                + "   AND (?::date IS NULL OR t.the_date < (?::date + 1))"
                + "   AND (?::text IS NULL OR t.audit_status = ?)"
                + access.sql(),
                java.math.BigDecimal.class, sumParams.toArray());

            Map<String, Object> agg = new java.util.LinkedHashMap<>();
            agg.put("amount", sumAmount == null ? java.math.BigDecimal.ZERO : sumAmount);
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total, agg);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    /**
     * 批量汇款：把一批退款/调账/返利记录标记为已汇出。
     *  - status DRAFT/PENDING/APPROVED → PAID
     *  - 写入 financial_account_id（资金账户）、remitted_at（汇出时间）、remit_name（经手人）
     *  - 仅 AUDITED 状态（已审核）才允许汇款；未审/已汇款的会跳过
     */
    protected Map<String, Object> batchRemitImpl(java.util.List<String> ids,
                                                  String financialAccountId,
                                                  String remitName) {
        if (ids == null || ids.isEmpty()) {
            return Map.of("remitted", 0, "skipped", 0);
        }
        int remitted = 0, skipped = 0;
        for (String id : ids) {
            int updated = jdbc.update(
                "UPDATE acc_finance_txns SET"
                + "  status = 'PAID',"
                + "  financial_account_id = ?::uuid,"
                + "  remitted_at = now(),"
                + "  remit_name = ?"
                + " WHERE id = ?::uuid AND side = ? AND txn_type = ?"
                + "   AND audit_status = 'AUDITED'"
                + "   AND status <> 'PAID'",
                financialAccountId, remitName, id, side(), txnType());
            if (updated > 0) remitted++; else skipped++;
        }
        return Map.of("remitted", remitted, "skipped", skipped);
    }

    protected Map<String, Object> rawImpl(String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_finance_txns WHERE id = ?::uuid AND side = ? AND txn_type = ? LIMIT 1",
            id, side(), txnType());
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    protected Map<String, Object> createImpl(Map<String, Object> body) {
        Object txnNo = body.getOrDefault("no", body.get("txn_no"));
        if (txnNo == null || txnNo.toString().isBlank()) {
            throw ApiException.badRequest("单号必填");
        }
        if ("CUSTOMER".equals(side())) {
            if (body.get("customer_id") == null || body.get("customer_id").toString().isBlank()) {
                throw ApiException.badRequest("请选择客户");
            }
        } else if ("SUPPLIER".equals(side())) {
            if (body.get("partner_id") == null || body.get("partner_id").toString().isBlank()) {
                throw ApiException.badRequest("请选择供应商");
            }
        }
        BigDecimal amount = body.get("amount") instanceof Number n
            ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
        // ACC Received.php L377 / CRefund.php L436: 金额必须大于零
        if (amount.signum() <= 0) {
            throw ApiException.badRequest("金额必须大于零");
        }
        String currency = (String) body.getOrDefault("currency", "CNY");
        if (currency.length() != 3) {
            throw ApiException.badRequest("找不到币种");
        }
        String id = jdbc.queryForObject("""
            INSERT INTO acc_finance_txns (
              tenant_id, txn_no, the_date, side, txn_type,
              customer_id, partner_id, amount, currency, reason, remark, status, add_name
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid,
              ?, ?::date, ?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?
            )
            RETURNING id::text
            """, String.class,
            body.getOrDefault("no", body.get("txn_no")),
            body.getOrDefault("theDate", body.get("the_date")),
            side(), txnType(),
            "CUSTOMER".equals(side()) ? body.get("customer_id") : null,
            "SUPPLIER".equals(side()) ? body.get("partner_id") : null,
            amount, currency,
            body.get("reason"), body.get("remark"),
            body.getOrDefault("status", "PENDING"),
            body.getOrDefault("addName", body.get("add_name")));
        moneySnapshotService.snapshot(TABLE, id, amount, currency);
        return Map.of("id", id);
    }

    protected Map<String, Object> updateImpl(String id, Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_finance_txns WHERE id = ?::uuid AND side = ? AND txn_type = ?",
            String.class, id, side(), txnType());
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest(labelCn() + "已审核，字段不可修改: "
                + String.join(",", gate.rejected()) + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_finance_txns SET
              amount = coalesce(?, amount),
              reason = coalesce(?, reason),
              remark = coalesce(?, remark),
              status = coalesce(?, status)
            WHERE id = ?::uuid AND side = ? AND txn_type = ?
            """,
            allowed.get("amount") instanceof Number n ? new BigDecimal(n.toString()) : null,
            (String) allowed.get("reason"),
            (String) allowed.get("remark"),
            (String) allowed.get("status"),
            id, side(), txnType());
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    protected Map<String, Object> deleteImpl(String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_finance_txns WHERE id = ?::uuid AND side = ? AND txn_type = ?",
            String.class, id, side(), txnType());
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest(labelCn() + "已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update(
            "DELETE FROM acc_finance_txns WHERE id = ?::uuid AND side = ? AND txn_type = ?",
            id, side(), txnType());
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("no", row.get("txn_no"));
        out.put("theDate", json.value(row.get("the_date")));
        out.put("customerName", row.get("customer_name"));
        out.put("supplierName", row.get("partner_name"));
        out.put("amount", row.get("amount"));
        out.put("currency", row.get("currency"));
        out.put("poundage", row.get("poundage"));
        out.put("fxRate", row.get("fx_rate"));
        out.put("payCurrency", row.get("pay_currency"));
        out.put("bankId", row.get("bank_id"));
        // 双币种快照：用于前端 "¥X -> €Y" 显示
        out.put("targetCurrency", row.get("target_currency"));
        out.put("targetAmount", row.get("target_amount"));
        // 汇款信息（批量汇款后回填）
        out.put("financialAccountId", row.get("financial_account_id"));
        out.put("financialAccountName", row.get("financial_account_name"));
        out.put("remittedAt", json.value(row.get("remitted_at")));
        out.put("remitName", row.get("remit_name"));
        out.put("reason", row.get("reason"));
        out.put("remark", row.get("remark"));
        out.put("status", row.get("status"));
        out.put("addName", row.get("add_name"));
        out.put("addTime", json.value(row.get("created_at")));
        out.put("auditName", row.get("audit_name"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        return out;
    }
}
