package com.xqt.saas.acc;

import java.util.List;
import java.util.Map;

import com.xqt.saas.auth.AuthPrincipal;
import com.xqt.saas.common.ApiException;
import com.xqt.saas.framework.audit.AuditService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通用 ACC 审核流入口。前端 fetchAccBiz / batchAudit 会直接 POST 这里：
 *   POST /api/acc/{tab}/{id}/audit-biz
 *   POST /api/acc/{tab}/{id}/undo-biz
 *   POST /api/acc/{tab}/batch-audit         body: { ids: ["...", ...] }
 *   GET  /api/acc/{tab}/{id}/audit-history
 *
 * 这里把 tab 名映射到底层 table 名（绝大部分一致，只有 acc-branches → organizations 等几条例外）。
 */
@RestController
@RequestMapping("/api/acc")
public class AccAuditController {
    /** 前端 tab.api → 真实 DB 表名映射。前端绝大多数 tab 名和表名一致。 */
    private static final Map<String, String> TAB_TO_TABLE = Map.ofEntries(
        Map.entry("customers", "customers"),
        Map.entry("channels", "channels"),
        Map.entry("currencies", "finance_currency"),
        Map.entry("orders", "orders"),
        Map.entry("shipments", "shipments"),
        // packages（装箱单）是 shipments 的另一个视图；审核装箱单本质就是审核对应 shipment
        Map.entry("packages", "shipments"),
        Map.entry("bills", "customer_invoices"),
        Map.entry("payments", "partner_payments"),
        Map.entry("receiveds", "payments"),
        Map.entry("charges", "charges"),
        Map.entry("costs", "charges"),         // costs 走 charges 表 + side='AP'，audit 不区分
        Map.entry("suppliers", "partners"),
        Map.entry("fee-types", "charge_items"),
        Map.entry("branches", "organizations"),
        Map.entry("departments", "organizations"),
        Map.entry("remotes", "remote_zones"),
        Map.entry("fuels", "fuel_surcharge_rates"),
        // 022 主数据批次
        Map.entry("countries", "countries"),
        Map.entry("postcodes", "postcodes"),
        Map.entry("hscodes", "hs_codes"),
        Map.entry("bank-names", "bank_names"),
        Map.entry("districts", "districts"),
        Map.entry("customer-groups", "customer_groups"),
        Map.entry("warehouses", "warehouses"),
        Map.entry("returns", "return_orders"),
        // 023 异常流 + 财务扩展
        Map.entry("collects", "acc_collects"),
        Map.entry("detains", "acc_detains"),
        Map.entry("asks", "acc_asks"),
        Map.entry("reparations", "acc_reparations"),
        Map.entry("fees", "acc_fees"),
        Map.entry("customer-fines", "acc_fines"),
        Map.entry("supplier-fines", "acc_fines"),
        // 024 财务流水 + 2 字典
        Map.entry("customer-adjusts", "acc_finance_txns"),
        Map.entry("supplier-adjusts", "acc_finance_txns"),
        Map.entry("customer-refunds", "acc_finance_txns"),
        Map.entry("supplier-refunds", "acc_finance_txns"),
        Map.entry("customer-rebates", "acc_finance_txns"),
        Map.entry("supplier-rebates", "acc_finance_txns"),
        Map.entry("expense-categories", "acc_expense_categories"),
        Map.entry("fee-item-types", "acc_fee_item_types"),
        // 025 资金管理（banks 复用 financial_accounts）
        Map.entry("expenses", "acc_expenses"),
        Map.entry("banks", "financial_accounts"),
        Map.entry("transfers", "acc_transfers"),
        Map.entry("dividends", "acc_dividends"),
        Map.entry("borrowings", "acc_borrowings"),
        Map.entry("assets", "acc_assets"),
        Map.entry("cycles", "acc_cycles"),
        Map.entry("received-sms", "acc_received_sms"),
        // 026 HR 人事 + 提成
        Map.entry("employees", "acc_employees"),
        Map.entry("attendances", "acc_attendances"),
        Map.entry("wages", "acc_wages"),
        Map.entry("commission-rules", "acc_commission_rules"),
        Map.entry("commissions", "acc_commissions"),
        Map.entry("socials", "acc_socials"),
        Map.entry("social-persons", "acc_social_persons"),
        Map.entry("funds", "acc_funds"),
        Map.entry("fund-persons", "acc_fund_persons"),
        // 027 物流扩展
        Map.entry("stowages", "stowages"),
        Map.entry("stowage-categories", "stowage_categories"),
        Map.entry("ports", "stowage_ports"),
        Map.entry("stowage-steps", "acc_stowage_steps"),
        Map.entry("transits", "acc_transits"),
        Map.entry("dispatches", "acc_dispatches"),
        Map.entry("forecasts", "acc_forecasts"),
        Map.entry("tracks", "acc_track_items"),
        // 028 客户产品 + 系统杂项
        Map.entry("channel-accounts", "acc_channel_accounts"),
        Map.entry("product-items", "acc_product_items"),
        Map.entry("sold-tos", "acc_sold_tos"),
        Map.entry("potentials", "acc_potentials"),
        Map.entry("notices", "acc_notices"),
        Map.entry("logistics-interfaces", "acc_logistics_interfaces"),
        Map.entry("tasks", "acc_scheduled_tasks"),
        Map.entry("templates", "acc_message_templates")
    );

    private final AuditService auditService;

    public AccAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @PostMapping("/{tab}/{id}/audit-biz")
    public Map<String, Object> audit(@PathVariable String tab, @PathVariable String id) {
        String table = resolveTable(tab);
        AuthPrincipal p = currentPrincipal();
        auditService.audit(table, id, p.tenantId(), p.username());
        return Map.of("id", id, "audited", true);
    }

    @PostMapping("/{tab}/{id}/undo-biz")
    public Map<String, Object> undo(@PathVariable String tab, @PathVariable String id) {
        String table = resolveTable(tab);
        AuthPrincipal p = currentPrincipal();
        auditService.undoAudit(table, id, p.tenantId(), p.username());
        return Map.of("id", id, "audited", false);
    }

    @PostMapping("/{tab}/batch-audit")
    @SuppressWarnings("unchecked")
    public Map<String, Object> batchAudit(@PathVariable String tab, @RequestBody Map<String, Object> body) {
        String table = resolveTable(tab);
        Object idsRaw = body.get("ids");
        if (!(idsRaw instanceof List<?> list) || list.isEmpty()) {
            throw ApiException.badRequest("ids is required");
        }
        List<String> ids = ((List<Object>) list).stream().map(Object::toString).toList();
        AuthPrincipal p = currentPrincipal();
        AuditService.BatchResult result = auditService.batchAudit(table, ids, p.tenantId(), p.username());
        return Map.of("total", result.total(), "audited", result.audited(), "skipped", result.skipped());
    }

    @GetMapping("/{tab}/{id}/audit-history")
    public Map<String, Object> history(@PathVariable String tab, @PathVariable String id,
                                       @RequestParam(defaultValue = "20") int limit) {
        String table = resolveTable(tab);
        return Map.of("data", auditService.history(table, id, limit));
    }

    private String resolveTable(String tab) {
        String table = TAB_TO_TABLE.get(tab);
        if (table == null) {
            throw ApiException.badRequest("unknown ACC tab: " + tab);
        }
        return table;
    }

    private AuthPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal p)) {
            throw ApiException.unauthorized("authentication required");
        }
        return p;
    }
}
