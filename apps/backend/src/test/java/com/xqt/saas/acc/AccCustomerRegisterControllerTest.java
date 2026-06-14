package com.xqt.saas.acc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Map;

import com.xqt.saas.auth.PasswordHasher;
import com.xqt.saas.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AccCustomerRegisterControllerTest {

    private AccCustomerRegisterController controller() {
        return new AccCustomerRegisterController(mock(JdbcTemplate.class), mock(PasswordHasher.class));
    }

    @Test
    void register_chineseCustomerCode_throws() {
        assertThatThrownBy(() -> controller().register(Map.of(
            "customerCode", "客户ABC",
            "customerName", "test", "contactName", "John",
            "email", "j@x.com", "password", "abcdef")))
            .isInstanceOf(ApiException.class).hasMessageContaining("不能包含中文");
    }

    @Test
    void register_invalidEmail_throws() {
        assertThatThrownBy(() -> controller().register(Map.of(
            "customerCode", "ACME001",
            "customerName", "Acme", "contactName", "John",
            "email", "not-an-email", "password", "abcdef")))
            .isInstanceOf(ApiException.class).hasMessageContaining("邮箱格式");
    }

    @Test
    void register_shortPassword_throws() {
        assertThatThrownBy(() -> controller().register(Map.of(
            "customerCode", "ACME001",
            "customerName", "Acme", "contactName", "John",
            "email", "j@x.com", "password", "abc")))
            .isInstanceOf(ApiException.class).hasMessageContaining("密码长度");
    }

    @Test
    void register_missingContactName_throws() {
        assertThatThrownBy(() -> controller().register(Map.of(
            "customerCode", "ACME001",
            "customerName", "Acme", "contactName", "",
            "email", "j@x.com", "password", "abcdef")))
            .isInstanceOf(ApiException.class).hasMessageContaining("联系人");
    }

    @Test
    void register_invalidPhone_throws() {
        assertThatThrownBy(() -> controller().register(Map.of(
            "customerCode", "ACME001",
            "customerName", "Acme", "contactName", "John",
            "email", "j@x.com", "password", "abcdef",
            "phone", "abc@@##")))
            .isInstanceOf(ApiException.class).hasMessageContaining("电话格式");
    }
}
