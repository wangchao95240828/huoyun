package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.JsonSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ACC 财务中心 → 资金账户 → 往来账户。
 * 直接读 finance_account_transaction 表（账户流水），按账户/客户/币种聚合。
 */
@RestController
@RequestMapping("/api/acc/account-transactions")
public class AccAccountTransactionsController {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccAccountTransactionsController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo,
        @RequestParam(required = false) Integer transactionType   // ACC: 按表头类型查询
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM finance_account_transaction"
                + " WHERE (?::text IS NULL OR transaction_no ILIKE ? OR remark ILIKE ?)"
                + "   AND (?::date IS NULL OR payment_time >= ?::date)"
                + "   AND (?::date IS NULL OR payment_time < (?::date + 1))"
                + "   AND (?::int IS NULL OR transaction_type = ?)",
                Long.class, search, search, search, dateFrom, dateFrom, dateTo, dateTo, transactionType, transactionType);
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, transaction_no, account_id, customer_id, transaction_type,"
                + "       currency, amount, fee, credit_amount, remark,"
                + "       payment_time, create_by, create_time"
                + " FROM finance_account_transaction"
                + " WHERE (?::text IS NULL OR transaction_no ILIKE ? OR remark ILIKE ?)"
                + "   AND (?::date IS NULL OR payment_time >= ?::date)"
                + "   AND (?::date IS NULL OR payment_time < (?::date + 1))"
                + "   AND (?::int IS NULL OR transaction_type = ?)"
                + " ORDER BY payment_time DESC NULLS LAST, create_time DESC"
                + " LIMIT ? OFFSET ?",
                search, search, search, dateFrom, dateFrom, dateTo, dateTo, transactionType, transactionType, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", String.valueOf(row.get("id")));
        out.put("transactionNo", row.get("transaction_no"));
        out.put("accountId", row.get("account_id"));
        out.put("customerId", row.get("customer_id"));
        out.put("transactionType", row.get("transaction_type"));
        out.put("currency", row.get("currency"));
        out.put("amount", row.get("amount"));
        out.put("fee", row.get("fee"));
        out.put("creditAmount", row.get("credit_amount"));
        out.put("remark", row.get("remark"));
        out.put("paymentTime", json.value(row.get("payment_time")));
        out.put("createBy", row.get("create_by"));
        out.put("createdAt", json.value(row.get("create_time")));
        return out;
    }
}
