package com.xqt.saas.framework.statemachine;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.xqt.saas.common.ApiException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 复刻 ACC 各业务单据的状态机：哪些状态间允许转移、哪些不允许。
 *
 * ACC 旧逻辑硬编码在每个 PHP 类的 audit/edit/delete/cancel 方法里：
 *   - 订单 SUBMITTED 后不能改地址
 *   - 账单 AUDITED 后不能改金额（只能反审）
 *   - 收款 AUDITED 后不能删除
 * 这里统一抽到声明式注册表，按 (entity, fromStatus, event) → toStatus 查询。
 */
@Component
public class StateMachineRegistry {
    private final Map<String, EntityStateMachine> machines = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        // ─── 通用审核流：所有"可审核"实体共享 ───
        // PENDING ──(audit)──→ AUDITED ──(undo)──→ UNAUDITED ──(edit)──→ PENDING
        EntityStateMachine auditFlow = EntityStateMachine.builder()
            .transition("PENDING", "audit", "AUDITED")
            .transition("AUDITED", "undo", "UNAUDITED")
            .transition("UNAUDITED", "edit", "PENDING")
            .transition("UNAUDITED", "audit", "AUDITED")
            .build();
        machines.put("audit", auditFlow);

        // ─── 订单状态机 ───
        // 对应 orders.status enum: DRAFT / SUBMITTED / ACCEPTED / FULFILLING / COMPLETED / CANCELLED / EXCEPTION
        machines.put("orders", EntityStateMachine.builder()
            .transition("DRAFT", "submit", "SUBMITTED")
            .transition("DRAFT", "cancel", "CANCELLED")
            .transition("SUBMITTED", "accept", "ACCEPTED")
            .transition("SUBMITTED", "cancel", "CANCELLED")
            .transition("ACCEPTED", "fulfill", "FULFILLING")
            .transition("ACCEPTED", "cancel", "CANCELLED")
            .transition("FULFILLING", "complete", "COMPLETED")
            .transition("FULFILLING", "exception", "EXCEPTION")
            .transition("EXCEPTION", "fulfill", "FULFILLING")
            .build());

        // ─── 运单状态机 ───
        // shipments.status enum: DRAFT/ORDERED/IN_WAREHOUSE/MEASURED/BOOKED/IN_TRANSIT/DELIVERED/CLOSED/EXCEPTION
        machines.put("shipments", EntityStateMachine.builder()
            .transition("DRAFT", "order", "ORDERED")
            .transition("ORDERED", "warehouse-in", "IN_WAREHOUSE")
            .transition("ORDERED", "exception", "EXCEPTION")
            .transition("IN_WAREHOUSE", "measure", "MEASURED")
            .transition("MEASURED", "book", "BOOKED")
            .transition("BOOKED", "depart", "IN_TRANSIT")
            .transition("IN_TRANSIT", "deliver", "DELIVERED")
            .transition("DELIVERED", "close", "CLOSED")
            .transition("ORDERED", "cancel-shipment", "EXCEPTION")
            .build());

        // ─── 应收应付状态机 (charges.status enum) ───
        // DRAFT / ESTIMATED / LOCKED / ADJUSTED / VOID
        machines.put("charges", EntityStateMachine.builder()
            .transition("DRAFT", "estimate", "ESTIMATED")
            .transition("ESTIMATED", "lock", "LOCKED")
            .transition("LOCKED", "adjust", "ADJUSTED")
            .transition("ADJUSTED", "lock", "LOCKED")
            .transition("DRAFT", "void", "VOID")
            .transition("ESTIMATED", "void", "VOID")
            .build());

        // ─── 账单状态机 (customer_invoices.status text) ───
        // DRAFT / ISSUED / PARTIALLY_PAID / PAID / VOID
        machines.put("customer_invoices", EntityStateMachine.builder()
            .transition("DRAFT", "issue", "ISSUED")
            .transition("ISSUED", "partial-pay", "PARTIALLY_PAID")
            .transition("ISSUED", "pay", "PAID")
            .transition("PARTIALLY_PAID", "pay", "PAID")
            .transition("DRAFT", "void", "VOID")
            .transition("ISSUED", "void", "VOID")
            .build());
    }

    /**
     * 检查转移合法性。非法转移会抛 ApiException。
     */
    public void check(String entity, String fromStatus, String event) {
        EntityStateMachine machine = machines.get(entity);
        if (machine == null) {
            return; // 未注册的实体不做约束（向后兼容）
        }
        String target = machine.next(fromStatus, event);
        if (target == null) {
            throw ApiException.badRequest(
                String.format("[%s] 非法状态转移: %s --%s--> ?", entity, fromStatus, event));
        }
    }

    /** 返回转移后的状态，未注册返回 null（业务层自己决定是否报错）。 */
    public String nextStatus(String entity, String fromStatus, String event) {
        EntityStateMachine machine = machines.get(entity);
        return machine == null ? null : machine.next(fromStatus, event);
    }

    /** 列出从当前状态可触发的所有 event。 */
    public Set<String> availableEvents(String entity, String fromStatus) {
        EntityStateMachine machine = machines.get(entity);
        return machine == null ? Set.of() : machine.eventsFrom(fromStatus);
    }

    public List<String> registeredEntities() {
        return List.copyOf(machines.keySet());
    }
}
