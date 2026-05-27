package com.xqt.saas.framework.fieldgate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 复刻 ACC 审核后字段锁定。
 *
 * ACC 行为：单据审核后，金额 / 客户 / 渠道 / 日期等核心字段只读，只允许改 remark 等次要字段。
 * 想改主字段必须先反审。这里把"哪些字段在审核后可改"集中声明，service 层 update 时调
 * filterAllowedFields() 过滤掉不允许的字段。
 */
@Component
public class FieldGate {
    private final Map<EntityState, Set<String>> mutableFieldsByEntityState = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        // 默认：审核状态 = AUDITED 时，只允许改 remark 类字段；其它字段必须先反审才能改

        // ─── customer_invoices（账单）───
        // AUDITED 状态：禁止改金额 / 客户 / 币种；允许 remark / status（系统流转）
        registerMutable("customer_invoices", "AUDITED", Set.of("status", "remark", "audit_name", "audited_at"));

        // ─── charges（应收应付）───
        // AUDITED：金额、币种、shipment、charge_item 都不能改
        registerMutable("charges", "AUDITED", Set.of("status", "remark", "audit_name", "audited_at"));

        // ─── payments / partner_payments（收款 / 付款）───
        // AUDITED：金额、客户 / 物流商、币种、reference_no 都不能改
        registerMutable("payments", "AUDITED", Set.of("audit_name", "audited_at"));
        registerMutable("partner_payments", "AUDITED", Set.of("audit_name", "audited_at"));

        // ─── orders ───
        // 进入 SUBMITTED 后只读关键字段，只能 cancel 状态机推进
        registerMutable("orders", "AUDITED", Set.of("status", "audit_name", "audited_at"));

        // ─── shipments ───
        registerMutable("shipments", "AUDITED", Set.of("status", "remote_level", "audit_name", "audited_at"));

        // ─── 主数据类（customers / channels / 等）───
        // AUDITED：编码不能改，但名称 / 启用状态可改
        registerMutable("customers", "AUDITED", Set.of("name", "credit_limit", "audit_name", "audited_at"));
        registerMutable("channels", "AUDITED", Set.of("name", "active", "audit_name", "audited_at"));
        registerMutable("partners", "AUDITED", Set.of("name", "status", "audit_name", "audited_at"));
        registerMutable("finance_currency", "AUDITED", Set.of("name", "audit_name", "audited_at"));
        registerMutable("charge_items", "AUDITED", Set.of("name", "audit_name", "audited_at"));
        registerMutable("organizations", "AUDITED", Set.of("name", "is_active", "audit_name", "audited_at"));
        registerMutable("remote_zones", "AUDITED", Set.of("level", "audit_name", "audited_at"));
        registerMutable("fuel_surcharge_rates", "AUDITED", Set.of("rate", "audit_name", "audited_at"));

        // ─── 022 主数据批次：审核后只能改 name/启用状态等次要字段 ───
        registerMutable("countries", "AUDITED", Set.of("cn_name", "en_name", "is_open", "audit_name", "audited_at"));
        registerMutable("postcodes", "AUDITED", Set.of("region", "city", "state_code", "audit_name", "audited_at"));
        registerMutable("hs_codes", "AUDITED", Set.of("name_en", "name_cn", "category", "audit_name", "audited_at"));
        registerMutable("bank_names", "AUDITED", Set.of("name", "swift", "audit_name", "audited_at"));
        registerMutable("districts", "AUDITED", Set.of("name", "audit_name", "audited_at"));
        registerMutable("customer_groups", "AUDITED", Set.of("name", "remark", "audit_name", "audited_at"));
        registerMutable("warehouses", "AUDITED", Set.of("name", "address", "status", "audit_name", "audited_at"));
        registerMutable("return_orders", "AUDITED", Set.of("status", "reason", "audit_name", "audited_at"));

        // ─── 023 异常流 + 财务扩展 ───
        // 已审核业务单据：原则上只允许改状态 + 备注，金额/客户/单据号都不能动
        registerMutable("acc_collects", "AUDITED", Set.of("status", "remark", "audit_name", "audited_at"));
        registerMutable("acc_detains", "AUDITED", Set.of("status", "reason", "audit_name", "audited_at"));
        registerMutable("acc_asks", "AUDITED", Set.of("status", "content", "audit_name", "audited_at"));
        // 赔偿：审核后只允许改 paid_amount 实际赔付（理赔可能后续审批）+ status
        registerMutable("acc_reparations", "AUDITED",
            Set.of("status", "paid_amount", "reason", "audit_name", "audited_at"));
        registerMutable("acc_fees", "AUDITED", Set.of("name", "remark", "audit_name", "audited_at"));
        registerMutable("acc_fines", "AUDITED", Set.of("remark", "audit_name", "audited_at"));

        // ─── 024 财务流水 + 字典 ───
        // 已审核：金额 / 客户 / 物流商 锁；只允许改 remark、status（系统流转）
        registerMutable("acc_finance_txns", "AUDITED",
            Set.of("status", "remark", "audit_name", "audited_at"));
        registerMutable("acc_expense_categories", "AUDITED",
            Set.of("name", "remark", "audit_name", "audited_at"));
        registerMutable("acc_fee_item_types", "AUDITED",
            Set.of("name", "color", "remark", "audit_name", "audited_at"));

        // ─── 025 资金管理 ───
        // 资金类业务单据审核后默认只允许改 remark / 显示开关；金额/收付方都锁定
        registerMutable("acc_expenses", "AUDITED", Set.of("remark", "name", "audit_name", "audited_at"));
        registerMutable("financial_accounts", "AUDITED",
            Set.of("account_name", "balance", "is_show", "last_update", "audit_name", "audited_at"));
        registerMutable("acc_transfers", "AUDITED", Set.of("remark", "audit_name", "audited_at"));
        registerMutable("acc_dividends", "AUDITED", Set.of("remark", "audit_name", "audited_at"));
        registerMutable("acc_borrowings", "AUDITED", Set.of("remark", "audit_name", "audited_at"));
        registerMutable("acc_assets", "AUDITED", Set.of("name", "remark", "audit_name", "audited_at"));
        registerMutable("acc_cycles", "AUDITED", Set.of("remark", "audit_name", "audited_at"));
        registerMutable("acc_received_sms", "AUDITED", Set.of("raw_content", "audit_name", "audited_at"));

        // ─── 026 HR 人事 + 提成 ───
        registerMutable("acc_employees", "AUDITED", Set.of("name", "mobile", "position", "status", "entry_date", "audit_name", "audited_at"));
        registerMutable("acc_attendances", "AUDITED", Set.of("status", "sign_in_time", "sign_out_time", "remark", "audit_name", "audited_at"));
        registerMutable("acc_wages", "AUDITED", Set.of("remark", "audit_name", "audited_at"));
        registerMutable("acc_commission_rules", "AUDITED", Set.of("name", "percent", "amount", "sales", "profit", "remark", "audit_name", "audited_at"));
        registerMutable("acc_commissions", "AUDITED", Set.of("status", "remark", "audit_name", "audited_at"));
        registerMutable("acc_socials", "AUDITED", Set.of("remark", "audit_name", "audited_at"));
        registerMutable("acc_social_persons", "AUDITED", Set.of("person_amount", "company_amount", "remark", "audit_name", "audited_at"));
        registerMutable("acc_funds", "AUDITED", Set.of("remark", "audit_name", "audited_at"));
        registerMutable("acc_fund_persons", "AUDITED", Set.of("person_amount", "company_amount", "remark", "audit_name", "audited_at"));

        // ─── 027 物流扩展 ───
        registerMutable("stowages", "AUDITED", Set.of("status", "remark", "audit_name", "audited_at"));
        registerMutable("stowage_categories", "AUDITED", Set.of("name", "sort_order", "is_active", "audit_name", "audited_at"));
        registerMutable("stowage_ports", "AUDITED", Set.of("name", "country", "is_active", "audit_name", "audited_at"));
        registerMutable("acc_stowage_steps", "AUDITED", Set.of("name", "step_order", "location", "status", "remark", "audit_name", "audited_at"));
        registerMutable("acc_transits", "AUDITED", Set.of("status", "tariff", "cost", "remark", "audit_name", "audited_at"));
        registerMutable("acc_dispatches", "AUDITED", Set.of("contact_name", "contact_mobile", "pick_address", "status", "remark", "audit_name", "audited_at"));
        registerMutable("acc_forecasts", "AUDITED", Set.of("package_count", "weight", "volume", "status", "remark", "audit_name", "audited_at"));
        registerMutable("acc_track_items", "AUDITED", Set.of("name", "name_en", "sort_order", "is_active", "audit_name", "audited_at"));

        // ─── 028 客户产品 + 系统杂项 ───
        registerMutable("acc_channel_accounts", "AUDITED", Set.of("account_name", "api_key", "api_secret", "endpoint_url", "is_active", "remark", "audit_name", "audited_at"));
        registerMutable("acc_product_items", "AUDITED", Set.of("name", "name_en", "hs_code", "category", "unit_price", "is_active", "remark", "audit_name", "audited_at"));
        registerMutable("acc_sold_tos", "AUDITED", Set.of("contact_name", "contact_mobile", "company_name", "country", "state", "city", "address", "postcode", "is_default", "remark", "audit_name", "audited_at"));
        registerMutable("acc_potentials", "AUDITED", Set.of("company_name", "contact_name", "contact_mobile", "source", "status", "remark", "audit_name", "audited_at"));
        registerMutable("acc_notices", "AUDITED", Set.of("title", "content", "notice_type", "audit_name", "audited_at"));
        registerMutable("acc_logistics_interfaces", "AUDITED", Set.of("name", "api_key", "api_secret", "endpoint_url", "is_enabled", "config_json", "remark", "audit_name", "audited_at"));
        registerMutable("acc_scheduled_tasks", "AUDITED", Set.of("task_name", "cron_expr", "is_enabled", "config_json", "remark", "audit_name", "audited_at"));
        registerMutable("acc_message_templates", "AUDITED", Set.of("template_name", "title", "content", "is_active", "remark", "audit_name", "audited_at"));
    }

    public void registerMutable(String entity, String auditStatus, Set<String> allowedFields) {
        mutableFieldsByEntityState.put(new EntityState(entity, auditStatus), Set.copyOf(allowedFields));
    }

    /**
     * 给定一个 update payload，根据当前实体的审核状态过滤出"被允许改"的字段。
     * 若 currentAuditStatus 不是 'AUDITED'，返回原 payload（PENDING/UNAUDITED 允许全字段改）。
     * 若是 'AUDITED'，只保留允许列表里的字段；被剔除的字段记录到 rejected。
     */
    public FilterResult filterAllowedFields(String entity, String currentAuditStatus,
                                            Map<String, Object> payload) {
        if (!"AUDITED".equals(currentAuditStatus)) {
            return new FilterResult(payload, List.of());
        }
        Set<String> allowed = mutableFieldsByEntityState.getOrDefault(
            new EntityState(entity, "AUDITED"), Set.of());
        Map<String, Object> filtered = new LinkedHashMap<>();
        List<String> rejected = new java.util.ArrayList<>();
        for (var entry : payload.entrySet()) {
            if (allowed.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            } else {
                rejected.add(entry.getKey());
            }
        }
        return new FilterResult(filtered, rejected);
    }

    public record FilterResult(Map<String, Object> allowed, List<String> rejected) {
    }

    private record EntityState(String entity, String auditStatus) {
    }
}
