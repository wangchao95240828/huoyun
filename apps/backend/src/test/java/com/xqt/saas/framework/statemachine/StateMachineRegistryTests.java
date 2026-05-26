package com.xqt.saas.framework.statemachine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xqt.saas.common.ApiException;
import org.junit.jupiter.api.Test;

class StateMachineRegistryTests {

    @Test
    void auditFlowAllowsCanonicalTransitions() {
        StateMachineRegistry reg = init();
        assertThat(reg.nextStatus("audit", "PENDING", "audit")).isEqualTo("AUDITED");
        assertThat(reg.nextStatus("audit", "AUDITED", "undo")).isEqualTo("UNAUDITED");
        assertThat(reg.nextStatus("audit", "UNAUDITED", "edit")).isEqualTo("PENDING");
        assertThat(reg.nextStatus("audit", "UNAUDITED", "audit")).isEqualTo("AUDITED");
    }

    @Test
    void orderFlowRejectsInvalidTransitions() {
        StateMachineRegistry reg = init();
        // DRAFT 不能直接 complete
        assertThatThrownBy(() -> reg.check("orders", "DRAFT", "complete"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("非法状态转移");
        // 但 DRAFT 可以 submit
        assertThatThrownBy(() -> reg.check("orders", "COMPLETED", "submit"))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void chargesAuditedCannotGoBackToDraft() {
        StateMachineRegistry reg = init();
        // ACC: 已锁定的费用 (LOCKED) 不能回 DRAFT，只能 adjust
        assertThat(reg.nextStatus("charges", "LOCKED", "adjust")).isEqualTo("ADJUSTED");
        assertThat(reg.nextStatus("charges", "LOCKED", "void")).isNull();
    }

    @Test
    void unregisteredEntityPassesThrough() {
        StateMachineRegistry reg = init();
        // 未注册的实体不做约束，业务层自己负责
        reg.check("unknown_table", "ANY", "anything");
    }

    private StateMachineRegistry init() {
        StateMachineRegistry reg = new StateMachineRegistry();
        reg.init();
        return reg;
    }
}
