package com.xqt.saas.framework.cascade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.xqt.saas.common.ApiException;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 复刻 ACC 删除前的级联校验：删客户前查有没有未完结订单 / 未核销账单，
 * 删订单前查有没有已审核的费用，等等。
 *
 * ACC PHP 每个 doDelete 方法里都有一堆 if-else 查关联表；这里抽成声明式规则集。
 */
@Component
public class CascadeChecker {
    private final JdbcTemplate jdbc;
    private final Map<String, List<CascadeRule>> rulesByEntity = new ConcurrentHashMap<>();

    public CascadeChecker(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void init() {
        // ─── customers ───
        register("customers", List.of(
            CascadeRule.of("orders", "customer_id",
                "客户有 {n} 个订单未删除，请先处理订单"),
            CascadeRule.of("customer_invoices", "customer_id",
                "客户有 {n} 个账单未删除，请先处理账单"),
            CascadeRule.of("payments", "customer_id",
                "客户有 {n} 笔收款记录，不能删除"),
            CascadeRule.of("shipments", "customer_id",
                "客户有 {n} 个运单，不能删除")
        ));

        // ─── orders ───
        register("orders", List.of(
            // orders 删除时 cartons / charges 通常 cascade，但 audited 的不让删
            new CascadeRule("charges",
                "SELECT count(*) FROM charges ch JOIN shipments s ON s.id = ch.shipment_id " +
                "JOIN orders o ON o.customer_ref = s.customer_ref AND o.tenant_id = s.tenant_id " +
                "WHERE o.id = ?::uuid AND ch.audit_status = 'AUDITED'",
                "订单有 {n} 条已审核的费用，需先反审")
        ));

        // ─── shipments ───
        register("shipments", List.of(
            new CascadeRule("charges",
                "SELECT count(*) FROM charges WHERE shipment_id = ?::uuid AND audit_status = 'AUDITED'",
                "运单有 {n} 条已审核的费用，需先反审"),
            CascadeRule.of("label_files", "shipment_id",
                "运单有 {n} 个面单文件，请先删除")
        ));

        // ─── customer_invoices ───
        register("customer_invoices", List.of(
            new CascadeRule("payments",
                "SELECT count(*) FROM payments WHERE reference_no = (SELECT invoice_no FROM customer_invoices WHERE id = ?::uuid)",
                "账单已关联 {n} 笔收款，需先反核销"),
            new CascadeRule("customer_invoice_lines",
                "SELECT count(*) FROM customer_invoice_lines WHERE invoice_id = ?::uuid",
                "账单有 {n} 条明细需先清理")
        ));

        // ─── charges (AR + AP) ───
        register("charges", List.of(
            CascadeRule.of("customer_invoice_lines", "charge_id",
                "费用已被 {n} 张账单明细引用，请先撤销账单关联")
        ));

        // ─── channels ───
        register("channels", List.of(
            CascadeRule.of("shipments", "channel_id",
                "渠道关联 {n} 个运单，不能删除"),
            CascadeRule.of("rate_cards", "channel_id",
                "渠道关联 {n} 个价表，请先删除")
        ));

        // ─── partners (suppliers) ───
        register("partners", List.of(
            CascadeRule.of("partner_payments", "partner_id",
                "物流商已有 {n} 笔付款记录，不能删除")
        ));

        // ─── organizations (branches & departments) ───
        register("organizations", List.of(
            CascadeRule.of("organizations", "parent_id",
                "下属有 {n} 个子机构，请先转移或删除"),
            CascadeRule.of("orders", "branch_id",
                "分支已挂载 {n} 个订单，不能删除")
        ));

        // ─── 022 主数据批次的简单字典：默认没有阻塞依赖（业务表都按名称/编码引用，不带 FK） ───
        register("countries", List.of());
        register("postcodes", List.of());
        register("hs_codes", List.of());
        register("bank_names", List.of());
        register("customer_groups", List.of());

        register("districts", List.of(
            CascadeRule.of("districts", "parent_id",
                "下级有 {n} 个子行政区，请先转移或删除")
        ));

        register("warehouses", List.of(
            // warehouses 暂无 FK 直接挂业务，先空
        ));

        register("return_orders", List.of(
            // 退件已 cascade，无需级联检查
        ));

        // ─── 023 异常流 + 财务扩展 ───
        // 这一批主要是叶子表，没有下游依赖；保留空规则，方便后续业务追加
        register("acc_collects", List.of());
        register("acc_detains", List.of());
        register("acc_asks", List.of());
        register("acc_reparations", List.of());
        register("acc_fees", List.of());
        register("acc_fines", List.of());

        // ─── 024 财务流水 + 字典 ───
        register("acc_finance_txns", List.of());
        register("acc_expense_categories", List.of());
        register("acc_fee_item_types", List.of());

        // ─── 025 资金管理 ───
        register("acc_expenses", List.of());
        register("acc_transfers", List.of());
        register("acc_dividends", List.of());
        register("acc_borrowings", List.of());
        register("acc_assets", List.of());
        register("acc_cycles", List.of());
        register("acc_received_sms", List.of());
        register("financial_accounts", List.of(
            CascadeRule.of("acc_expenses", "bank_account_id",
                "账户有 {n} 笔费用记录，不能删除"),
            CascadeRule.of("acc_transfers", "from_bank_id",
                "账户有 {n} 笔转出记录，不能删除"),
            CascadeRule.of("acc_dividends", "bank_account_id",
                "账户有 {n} 笔分红记录，不能删除")
        ));

        // ─── 026 HR 人事 + 提成 ───
        register("acc_employees", List.of(
            CascadeRule.of("acc_wages", "employee_id",
                "员工有 {n} 条工资记录，请先删除工资"),
            CascadeRule.of("acc_attendances", "employee_id",
                "员工有 {n} 条考勤记录，请先删除考勤"),
            CascadeRule.of("acc_commissions", "employee_id",
                "员工有 {n} 条提成记录，请先删除提成"),
            CascadeRule.of("acc_social_persons", "employee_id",
                "员工有 {n} 条社保记录，请先删除"),
            CascadeRule.of("acc_fund_persons", "employee_id",
                "员工有 {n} 条公积金记录，请先删除")
        ));
        register("acc_attendances", List.of());
        register("acc_wages", List.of());
        register("acc_commission_rules", List.of(
            CascadeRule.of("acc_commissions", "rule_id",
                "提成规则已被 {n} 条提成记录引用，请先删除提成")
        ));
        register("acc_commissions", List.of());
        register("acc_socials", List.of(
            CascadeRule.of("acc_social_persons", "social_id",
                "社保有 {n} 条人员明细，请先删除人员")
        ));
        register("acc_social_persons", List.of());
        register("acc_funds", List.of(
            CascadeRule.of("acc_fund_persons", "fund_id",
                "公积金有 {n} 条人员明细，请先删除人员")
        ));
        register("acc_fund_persons", List.of());

        // ─── 027 物流扩展 ───
        register("stowages", List.of(
            CascadeRule.of("cartons", "stowage_id",
                "配载有 {n} 个箱单关联，请先移除"),
            CascadeRule.of("acc_stowage_steps", "stowage_id",
                "配载有 {n} 个步骤，请先删除步骤")
        ));
        register("stowage_categories", List.of(
            CascadeRule.of("stowages", "category_id",
                "配载分类有 {n} 个配载单引用，不能删除")
        ));
        register("stowage_ports", List.of(
            CascadeRule.of("stowages", "departure_port_id",
                "港口有 {n} 个配载单引用（始发港），不能删除"),
            CascadeRule.of("acc_transits", "from_port_id",
                "港口有 {n} 个转运记录引用，不能删除")
        ));
        register("acc_stowage_steps", List.of());
        register("acc_transits", List.of());
        register("acc_dispatches", List.of());
        register("acc_forecasts", List.of());
        register("acc_track_items", List.of());

        // ─── 028 客户产品 + 系统杂项 ───
        register("acc_channel_accounts", List.of());
        register("acc_product_items", List.of());
        register("acc_sold_tos", List.of());
        register("acc_potentials", List.of());
        register("acc_notices", List.of());
        register("acc_logistics_interfaces", List.of());
        register("acc_scheduled_tasks", List.of());
        register("acc_message_templates", List.of());
    }

    public void register(String entity, List<CascadeRule> rules) {
        rulesByEntity.put(entity, List.copyOf(rules));
    }

    /**
     * 删除前调用。所有规则的 count > 0 时报错聚合所有阻塞原因。
     */
    public void checkBeforeDelete(String entity, String entityId) {
        List<CascadeRule> rules = rulesByEntity.get(entity);
        if (rules == null || rules.isEmpty()) return;
        List<String> blockers = new ArrayList<>();
        for (CascadeRule rule : rules) {
            try {
                Long n = jdbc.queryForObject(rule.countSql(), Long.class, entityId);
                if (n != null && n > 0) {
                    blockers.add(rule.message().replace("{n}", String.valueOf(n)));
                }
            } catch (org.springframework.dao.DataAccessException ex) {
                // 关联表可能不存在（部分模块未上线），忽略
            }
        }
        if (!blockers.isEmpty()) {
            throw ApiException.badRequest(String.join("；", blockers));
        }
    }

    /** 级联规则的最简形态：固定 SQL + 消息模板。 */
    public record CascadeRule(String relatedTable, String countSql, String message) {
        /** 便捷构造：`SELECT count(*) FROM <table> WHERE <fk> = ?::uuid`。 */
        public static CascadeRule of(String table, String fkColumn, String message) {
            String sql = "SELECT count(*) FROM " + table + " WHERE " + fkColumn + " = ?::uuid";
            return new CascadeRule(table, sql, message);
        }
    }
}
