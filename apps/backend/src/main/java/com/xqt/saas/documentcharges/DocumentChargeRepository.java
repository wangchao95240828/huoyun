package com.xqt.saas.documentcharges;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.JsonSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DocumentChargeRepository {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public DocumentChargeRepository(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** 把订单下所有 ESTIMATED 状态的 charges 推进到 CONFIRMED。 */
    @Transactional(rollbackFor = Exception.class)
    public int confirmEstimatedCharges(String tenantId, String orderId, String side) {
        return jdbc.update("""
            UPDATE charges ch
            SET status = 'CONFIRMED'
            FROM shipments sh
            JOIN orders o ON o.customer_ref = sh.customer_ref AND o.tenant_id = sh.tenant_id
            WHERE ch.shipment_id = sh.id
              AND ch.tenant_id = ?::uuid
              AND ch.status = 'ESTIMATED'
              AND ch.side = ?::charge_side
              AND o.id = ?::uuid
            """, tenantId, side, orderId);
    }

    /**
     * 查可计入账单的 AR 费用：审核通过 + 未核销/部分核销 + 属于该客户 + 指定日期段。
     */
    public List<Map<String, Object>> findBillableArCharges(String tenantId, String customerId,
                                                            LocalDate dateFrom, LocalDate dateTo,
                                                            String currency) {
        return jdbc.queryForList("""
            SELECT ch.id::text AS id, ch.amount, ch.paid_amount, ch.unpaid_amount,
                   ch.currency, ch.shipment_id::text AS shipment_id,
                   ch.audit_status, ch.settlement_status,
                   sh.customer_id::text AS customer_id
            FROM charges ch
            LEFT JOIN shipments sh ON sh.id = ch.shipment_id
            WHERE ch.tenant_id = ?::uuid
              AND ch.side = 'AR'
              AND ch.audit_status = 'AUDITED'
              AND ch.settlement_status <> 'VOID'
              AND ch.settlement_status <> 'SETTLED'
              AND (sh.customer_id = ?::uuid OR ch.customer_id = ?::uuid)
              AND (?::char(3) IS NULL OR ch.currency = ?)
              AND ch.created_at >= ?
              AND ch.created_at < ? + interval '1 day'
            ORDER BY ch.created_at
            """, tenantId, customerId, customerId, currency, currency, dateFrom, dateTo);
    }

    public List<Map<String, Object>> findChargesByIds(String tenantId, List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        String inList = "(" + String.join(",", java.util.Collections.nCopies(ids.size(), "?::uuid")) + ")";
        Object[] params = new Object[ids.size() + 1];
        params[0] = tenantId;
        for (int i = 0; i < ids.size(); i++) params[i + 1] = ids.get(i);
        return jdbc.queryForList(
            "SELECT id::text AS id, amount, paid_amount, unpaid_amount, currency, "
            + "shipment_id::text AS shipment_id, audit_status, settlement_status "
            + "FROM charges WHERE tenant_id = ?::uuid AND id IN " + inList, params);
    }

    /** 生成客户账单单号：YYYYMMDD-HHMM-XXXX 随机。 */
    public String nextInvoiceNo(String tenantId, String prefix) {
        return prefix + java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    @Transactional(rollbackFor = Exception.class)
    public String insertCustomerInvoice(String tenantId, String customerId, String invoiceNo,
                                        String currency, BigDecimal total, String templateCode) {
        return jdbc.queryForObject("""
            INSERT INTO customer_invoices (
              tenant_id, customer_id, invoice_no, template_code, currency,
              total_amount, status, paid_amount, unpaid_amount, writeoff_status
            ) VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, 'CONFIRMED', 0, ?, 'UNPAID')
            RETURNING id::text
            """, String.class, tenantId, customerId, invoiceNo,
            templateCode == null ? "STANDARD" : templateCode, currency, total, total);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insertCustomerInvoiceLine(String tenantId, String invoiceId, String chargeId,
                                          String shipmentId, BigDecimal amount) {
        jdbc.update("""
            INSERT INTO customer_invoice_lines (tenant_id, invoice_id, charge_id, shipment_id, amount)
            VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?)
            """, tenantId, invoiceId, chargeId, shipmentId, amount);
    }

    @Transactional(rollbackFor = Exception.class)
    public int markChargesBilled(String tenantId, List<String> chargeIds, String invoiceNo) {
        if (chargeIds == null || chargeIds.isEmpty()) return 0;
        String inList = "(" + String.join(",", java.util.Collections.nCopies(chargeIds.size(), "?::uuid")) + ")";
        Object[] params = new Object[chargeIds.size() + 2];
        params[0] = invoiceNo;
        params[1] = tenantId;
        for (int i = 0; i < chargeIds.size(); i++) params[i + 2] = chargeIds.get(i);
        return jdbc.update(
            "UPDATE charges SET source_ref = ? "
            + "WHERE tenant_id = ?::uuid AND id IN " + inList, params);
    }

    /** 收款核销：写 payments + 联动 invoice paid_amount/writeoff_status + charges paid_amount。 */
    @Transactional(rollbackFor = Exception.class)
    public String insertPayment(String tenantId, String customerId, BigDecimal amount,
                                String currency, String referenceNo) {
        return jdbc.queryForObject("""
            INSERT INTO payments (tenant_id, customer_id, currency, amount, received_at, reference_no)
            VALUES (?::uuid, ?::uuid, ?, ?, now(), ?)
            RETURNING id::text
            """, String.class, tenantId, customerId, currency, amount, referenceNo);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyPaymentToInvoice(String tenantId, String invoiceId,
                                                     BigDecimal amount) {
        Map<String, Object> inv = findInvoice(tenantId, invoiceId);
        if (inv == null) return null;
        BigDecimal currentPaid = (BigDecimal) inv.get("paid_amount");
        BigDecimal total = (BigDecimal) inv.get("total_amount");
        BigDecimal newPaid = currentPaid.add(amount);
        BigDecimal newUnpaid = total.subtract(newPaid);
        String status = newPaid.compareTo(total) >= 0 ? "PAID"
            : newPaid.signum() > 0 ? "PARTIAL" : "UNPAID";
        jdbc.update("""
            UPDATE customer_invoices
            SET paid_amount = ?, unpaid_amount = ?, writeoff_status = ?, updated_at = now()
            WHERE id = ?::uuid AND tenant_id = ?::uuid
            """, newPaid, newUnpaid.max(BigDecimal.ZERO), status, invoiceId, tenantId);
        // 按行比例分摊到 charges
        List<Map<String, Object>> lines = jdbc.queryForList("""
            SELECT charge_id::text AS charge_id, amount
            FROM customer_invoice_lines
            WHERE invoice_id = ?::uuid AND tenant_id = ?::uuid AND charge_id IS NOT NULL
            """, invoiceId, tenantId);
        for (Map<String, Object> line : lines) {
            BigDecimal lineAmount = (BigDecimal) line.get("amount");
            BigDecimal share = lineAmount.multiply(amount).divide(total, 2,
                java.math.RoundingMode.HALF_UP);
            jdbc.update("""
                UPDATE charges SET paid_amount = paid_amount + ?
                WHERE id = ?::uuid AND tenant_id = ?::uuid
                """, share, line.get("charge_id"), tenantId);
        }
        inv.put("paid_amount", newPaid);
        inv.put("unpaid_amount", newUnpaid.max(BigDecimal.ZERO));
        inv.put("writeoff_status", status);
        return inv;
    }

    public Map<String, Object> findInvoice(String tenantId, String invoiceId) {
        try {
            return jdbc.queryForMap("""
                SELECT id::text AS id, invoice_no, currency,
                       total_amount, paid_amount, unpaid_amount, writeoff_status, customer_id::text AS customer_id
                FROM customer_invoices
                WHERE tenant_id = ?::uuid AND id = ?::uuid
                """, tenantId, invoiceId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    /**
     * 利润聚合：按 by 维度（customer/channel/branch/month/day）+ 日期段。
     *
     * 与 AccProfitsController.summary 同口径（消除双轨）：
     *   profit = AR收入 - AP成本 + adjustments(finance_txns+fines) - reparation
     *   adjustments 方向：side='CUSTOMER' → +amount；side='SUPPLIER' → -amount
     *
     * 维度可分摊性：
     *   customer / month / day：finance_txns/fines 可按 customer_id 或 the_date 分摊
     *   channel / branch：无可分摊键，adjustments=0
     */
    public List<Map<String, Object>> aggregateProfit(String tenantId, String groupBy,
                                                     LocalDate dateFrom, LocalDate dateTo) {
        String dim = switch (groupBy == null ? "" : groupBy) {
            case "customer" -> "coalesce(sh.customer_id::text, ch.customer_id::text)";
            case "channel" -> "sh.channel_id::text";
            case "branch" -> "ch.branch_id::text";
            case "month" -> "to_char(ch.created_at, 'YYYY-MM')";
            default -> "to_char(ch.created_at, 'YYYY-MM-DD')";
        };
        try {
            // 1) 主体：两层聚合（per-shipment → per-dim），含 reparation 子查询
            String innerSql = ("""
                SELECT %s AS dim,
                       sh.id AS shipment_id,
                       sum(CASE WHEN ch.side = 'AR' THEN ch.amount ELSE 0 END) AS revenue,
                       sum(CASE WHEN ch.side = 'AP' THEN ch.amount ELSE 0 END) AS cost,
                       coalesce((
                         SELECT sum(r.apply_amount) FROM acc_reparations r
                         WHERE r.shipment_id = sh.id AND r.audit_status = 'AUDITED'
                       ), 0) AS reparation
                FROM charges ch
                LEFT JOIN shipments sh ON sh.id = ch.shipment_id
                WHERE ch.tenant_id = ?::uuid
                  AND ch.audit_status = 'AUDITED'
                  AND ch.settlement_status <> 'VOID'
                  AND ch.created_at >= ?
                  AND ch.created_at < ? + interval '1 day'
                GROUP BY %s, sh.id
                """).formatted(dim, dim);
            List<Map<String, Object>> rows = jdbc.queryForList(("""
                SELECT
                  dim,
                  count(*) AS shipment_count,
                  sum(revenue) AS revenue,
                  sum(cost) AS cost,
                  sum(reparation) AS reparation
                FROM (%s) sub
                GROUP BY dim
                """).formatted(innerSql), tenantId, dateFrom, dateTo);

            // 2) 调整项（仅 customer / month / day 维度可分摊）
            Map<String, BigDecimal> adj = aggregateAdjustments(tenantId, groupBy, dateFrom, dateTo);
            boolean adjSupported = !adj.isEmpty()
                || "customer".equals(groupBy) || "month".equals(groupBy) || "day".equals(groupBy)
                || (groupBy != null && groupBy.isEmpty())  // default = day
                || groupBy == null;

            // 3) 合并到每行，重算 profit = revenue - cost - reparation + adjustments
            List<Map<String, Object>> out = new java.util.ArrayList<>();
            for (Map<String, Object> r : rows) {
                Map<String, Object> m = new java.util.LinkedHashMap<>(r);
                BigDecimal revenue = (BigDecimal) m.getOrDefault("revenue", BigDecimal.ZERO);
                BigDecimal cost = (BigDecimal) m.getOrDefault("cost", BigDecimal.ZERO);
                BigDecimal reparation = (BigDecimal) m.getOrDefault("reparation", BigDecimal.ZERO);
                if (reparation == null) reparation = BigDecimal.ZERO;
                BigDecimal a = adj.getOrDefault((String) m.get("dim"), BigDecimal.ZERO);
                m.put("adjustments", a);
                m.put("adjustments_supported", adjSupported);
                m.put("profit", revenue.subtract(cost).add(a).subtract(reparation));
                out.add(m);
            }
            out.sort((x, y) -> ((BigDecimal) y.get("profit"))
                .compareTo((BigDecimal) x.get("profit")));
            return out;
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    /** 仅 customer / month / day 维度有可分摊键；其它返回空 map。 */
    private Map<String, BigDecimal> aggregateAdjustments(String tenantId, String groupBy,
                                                          LocalDate dateFrom, LocalDate dateTo) {
        Map<String, BigDecimal> bucket = new java.util.LinkedHashMap<>();
        String dimExprTemplate;
        switch (groupBy == null ? "" : groupBy) {
            case "customer" -> dimExprTemplate = "%s.customer_id::text";
            case "month" -> dimExprTemplate = "to_char(%s.the_date, 'YYYY-MM')";
            case "channel", "branch" -> { return bucket; }
            default -> dimExprTemplate = "to_char(%s.the_date, 'YYYY-MM-DD')";
        }
        // finance_txns
        String tDim = dimExprTemplate.formatted("t");
        addBucket(bucket,
            "SELECT " + tDim + " AS dim,"
          + " sum(CASE WHEN t.side='CUSTOMER' THEN t.amount ELSE -t.amount END) AS adj"
          + " FROM acc_finance_txns t"
          + " WHERE t.tenant_id = ?::uuid AND t.audit_status = 'AUDITED'"
          + "   AND t.the_date IS NOT NULL"
          + "   AND t.the_date >= ? AND t.the_date <= ?"
          + " GROUP BY " + tDim,
            tenantId, dateFrom, dateTo);
        // fines
        String fDim = dimExprTemplate.formatted("f");
        addBucket(bucket,
            "SELECT " + fDim + " AS dim,"
          + " sum(CASE WHEN f.side='CUSTOMER' THEN f.amount ELSE -f.amount END) AS adj"
          + " FROM acc_fines f"
          + " WHERE f.tenant_id = ?::uuid AND f.audit_status = 'AUDITED'"
          + "   AND f.the_date IS NOT NULL"
          + "   AND f.the_date >= ? AND f.the_date <= ?"
          + " GROUP BY " + fDim,
            tenantId, dateFrom, dateTo);
        return bucket;
    }

    private void addBucket(Map<String, BigDecimal> bucket, String sql,
                            String tenantId, LocalDate dateFrom, LocalDate dateTo) {
        try {
            for (Map<String, Object> row : jdbc.queryForList(sql, tenantId, dateFrom, dateTo)) {
                String dim = (String) row.get("dim");
                if (dim == null) continue;
                BigDecimal v = (BigDecimal) row.get("adj");
                if (v == null) continue;
                bucket.merge(dim, v, BigDecimal::add);
            }
        } catch (DataAccessException ignored) {
        }
    }

    /** Void：把 charge 置 VOID 并退还余额（若曾从余额扣过）。返回退款金额（用于审计）。 */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal voidChargeAndRefund(String tenantId, String chargeId) {
        Map<String, Object> ch;
        try {
            ch = jdbc.queryForMap("""
                SELECT id::text AS id, amount, paid_amount, evidence::text AS evidence_text
                FROM charges
                WHERE tenant_id = ?::uuid AND id = ?::uuid
                """, tenantId, chargeId);
        } catch (EmptyResultDataAccessException ex) {
            return BigDecimal.ZERO;
        }
        BigDecimal refund = BigDecimal.ZERO;
        String evidenceText = (String) ch.get("evidence_text");
        if (evidenceText != null && !evidenceText.isBlank()) {
            Map<String, Object> evidence = json.fromJson(evidenceText,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            if (Boolean.TRUE.equals(evidence.get("prepaid"))) {
                String acctId = (String) evidence.get("balance_account_id");
                if (acctId != null) {
                    BigDecimal amount = (BigDecimal) ch.get("amount");
                    jdbc.update(
                        "UPDATE financial_accounts SET balance = balance + ? WHERE id = ?::uuid",
                        amount, acctId);
                    refund = amount;
                }
            }
        }
        jdbc.update("UPDATE charges SET status = 'VOID' WHERE id = ?::uuid", chargeId);
        return refund;
    }

    // ───────────────────── 供应商（AP）侧闭环 ─────────────────────

    /**
     * 查可计入供应商账单的 AP 费用：审核通过 + 未付清 + 该供应商关联（carrier 链）+ 日期段。
     * partner 通过 partners.carrier_id ↔ channel_cost_policies.carrier_id ↔ shipments.channel_id 关联。
     */
    public List<Map<String, Object>> findBillableApCharges(String tenantId, String partnerId,
                                                           LocalDate dateFrom, LocalDate dateTo,
                                                           String currency) {
        return jdbc.queryForList("""
            SELECT DISTINCT ch.id::text AS id, ch.amount, ch.paid_amount, ch.unpaid_amount,
                   ch.currency, ch.shipment_id::text AS shipment_id,
                   ch.charge_item_id::text AS charge_item_id, ch.created_at
            FROM charges ch
            JOIN shipments sh ON sh.id = ch.shipment_id
            JOIN channel_cost_policies ccp ON ccp.channel_id = sh.channel_id
            JOIN partners p ON p.carrier_id = ccp.carrier_id
            WHERE ch.tenant_id = ?::uuid
              AND ch.side = 'AP'
              AND ch.audit_status = 'AUDITED'
              AND ch.settlement_status NOT IN ('VOID', 'SETTLED')
              AND p.id = ?::uuid
              AND (?::char(3) IS NULL OR ch.currency = ?)
              AND ch.created_at >= ?
              AND ch.created_at < ? + interval '1 day'
            ORDER BY ch.created_at
            """, tenantId, partnerId, currency, currency, dateFrom, dateTo);
    }

    public Map<String, Object> findPartnerInvoice(String tenantId, String invoiceId) {
        try {
            return jdbc.queryForMap("""
                SELECT id::text AS id, invoice_no, currency, partner_id::text AS partner_id,
                       total_amount, paid_amount, unpaid_amount, writeoff_status
                FROM partner_invoices
                WHERE tenant_id = ?::uuid AND id = ?::uuid
                """, tenantId, invoiceId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public String insertPartnerInvoice(String tenantId, String partnerId, String invoiceNo,
                                       String currency, BigDecimal total) {
        return jdbc.queryForObject("""
            INSERT INTO partner_invoices (
              tenant_id, partner_id, invoice_no, currency,
              total_amount, paid_amount, unpaid_amount, status, writeoff_status, invoice_date
            ) VALUES (?::uuid, ?::uuid, ?, ?, ?, 0, ?, 'CONFIRMED', 'UNPAID', now())
            RETURNING id::text
            """, String.class, tenantId, partnerId, invoiceNo, currency, total, total);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insertPartnerInvoiceLine(String tenantId, String invoiceId, String chargeId,
                                         String shipmentId, String chargeItemId,
                                         String currency, BigDecimal amount, int lineNo) {
        jdbc.update("""
            INSERT INTO partner_invoice_lines (
              tenant_id, invoice_id, charge_id, shipment_id, charge_item_id,
              line_no, currency, amount
            ) VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, ?, ?)
            """, tenantId, invoiceId, chargeId, shipmentId, chargeItemId, lineNo, currency, amount);
    }

    @Transactional(rollbackFor = Exception.class)
    public String insertPartnerPayment(String tenantId, String partnerId, BigDecimal amount,
                                       String currency, String bankAccountId, String referenceNo) {
        String paymentNo = "PP" + java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        return jdbc.queryForObject("""
            INSERT INTO partner_payments (
              tenant_id, partner_id, payment_no, financial_account_id, currency, amount,
              status, paid_at, reference_no
            ) VALUES (?::uuid, ?::uuid, ?, ?::uuid, ?, ?, 'CONFIRMED', now(), ?)
            RETURNING id::text
            """, String.class, tenantId, partnerId, paymentNo,
            bankAccountId, currency, amount, referenceNo);
    }

    /** 付款核销供应商账单：更新 invoice paid/unpaid/writeoff_status + 按行回写 AP charges。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyPaymentToPartnerInvoice(String tenantId, String invoiceId,
                                                            BigDecimal amount) {
        Map<String, Object> inv = findPartnerInvoice(tenantId, invoiceId);
        if (inv == null) return null;
        BigDecimal currentPaid = (BigDecimal) inv.get("paid_amount");
        BigDecimal total = (BigDecimal) inv.get("total_amount");
        BigDecimal newPaid = currentPaid.add(amount);
        BigDecimal newUnpaid = total.subtract(newPaid);
        String status = newPaid.compareTo(total) >= 0 ? "PAID"
            : newPaid.signum() > 0 ? "PARTIAL" : "UNPAID";
        jdbc.update("""
            UPDATE partner_invoices
            SET paid_amount = ?, unpaid_amount = ?, writeoff_status = ?, updated_at = now()
            WHERE id = ?::uuid AND tenant_id = ?::uuid
            """, newPaid, newUnpaid.max(BigDecimal.ZERO), status, invoiceId, tenantId);
        // 按行比例回写 AP charges.paid_amount（触发器会同步 settlement_status）
        List<Map<String, Object>> lines = jdbc.queryForList("""
            SELECT charge_id::text AS charge_id, amount
            FROM partner_invoice_lines
            WHERE invoice_id = ?::uuid AND tenant_id = ?::uuid AND charge_id IS NOT NULL
            """, invoiceId, tenantId);
        for (Map<String, Object> line : lines) {
            BigDecimal lineAmount = (BigDecimal) line.get("amount");
            if (total.signum() == 0) continue;
            BigDecimal share = lineAmount.multiply(amount).divide(total, 2,
                java.math.RoundingMode.HALF_UP);
            jdbc.update("""
                UPDATE charges SET paid_amount = paid_amount + ?
                WHERE id = ?::uuid AND tenant_id = ?::uuid
                """, share, line.get("charge_id"), tenantId);
        }
        inv.put("paid_amount", newPaid);
        inv.put("unpaid_amount", newUnpaid.max(BigDecimal.ZERO));
        inv.put("writeoff_status", status);
        return inv;
    }

    // ───────────────────── 资金账户流水（balance_ledger） ─────────────────────

    /** 读资金账户当前余额（写流水时取 before/after 用）。 */
    public BigDecimal findAccountBalance(String accountId) {
        try {
            return jdbc.queryForObject(
                "SELECT balance FROM financial_accounts WHERE id = ?::uuid", BigDecimal.class, accountId);
        } catch (DataAccessException ex) {
            return null;
        }
    }

    /** 调整资金账户余额（delta 正负皆可），返回是否命中。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean adjustAccountBalance(String accountId, BigDecimal delta) {
        return jdbc.update(
            "UPDATE financial_accounts SET balance = balance + ?, last_update = now() WHERE id = ?::uuid",
            delta, accountId) > 0;
    }

    /**
     * 写一条资金账户流水（balance_ledger）。对应 ACC Customer_Balance_History。
     * amount 传正数，方向由 direction（CREDIT 增 / DEBIT 减）表达。
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordBalanceLedger(String tenantId, String accountId, String ownerType,
                                    String ownerId, String bizType, String sourceType,
                                    String sourceRef, String currency, String direction,
                                    BigDecimal amount, BigDecimal balanceBefore,
                                    BigDecimal balanceAfter, String operator, String remark) {
        jdbc.update("""
            INSERT INTO balance_ledger (
              tenant_id, account_id, owner_type, owner_id, biz_type,
              source_type, source_ref, currency, direction, amount,
              balance_before, balance_after, operator, remark
            ) VALUES (
              ?::uuid, ?::uuid, ?, ?::uuid, ?::balance_ledger_biz_type,
              ?, ?, ?, ?::balance_ledger_direction, ?,
              ?, ?, ?, ?
            )
            """, tenantId, accountId, ownerType, ownerId, bizType,
            sourceType, sourceRef, currency, direction, amount,
            balanceBefore, balanceAfter, operator, remark);
    }

    /** 记一笔汇率快照（金额动作发生时调用，便于事后对账重现）。 */
    @Transactional(rollbackFor = Exception.class)
    public String insertFxSnapshot(String tenantId, String fromCurrency, String toCurrency,
                                   BigDecimal rate, String source) {
        return jdbc.queryForObject("""
            INSERT INTO fx_rate_snapshots (tenant_id, from_currency, to_currency, rate, source)
            VALUES (?::uuid, ?, ?, ?, ?)
            RETURNING id::text
            """, String.class, tenantId, fromCurrency, toCurrency,
            rate == null ? BigDecimal.ONE : rate, source == null ? "LOCAL" : source);
    }
}
