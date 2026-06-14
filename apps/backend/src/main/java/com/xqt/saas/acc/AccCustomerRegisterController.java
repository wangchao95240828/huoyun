package com.xqt.saas.acc;

import java.util.Map;
import java.util.UUID;

import com.xqt.saas.auth.PasswordHasher;
import com.xqt.saas.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户自助注册 — 对齐 ACC 客户portal 入口。
 *
 *   POST /api/customer-register   body: {customerCode, customerName, contactName, email, phone, password}
 *
 * 流程：
 *   1. 校验 customer code/name 唯一
 *   2. 校验邮箱格式 + 密码 >=5
 *   3. 建 customers + 建 users(bound_customer_id) + assignedRole OPERATOR
 *   4. 返回 customer_code + user_id（待 admin 后台审核激活）
 *
 * 客户注册默认 status=DISABLED，admin 审核后激活。
 */
@RestController
@RequestMapping("/api/customer-register")
public class AccCustomerRegisterController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccCustomerRegisterController.class);
    private final JdbcTemplate jdbc;
    private final PasswordHasher passwordHasher;

    public AccCustomerRegisterController(JdbcTemplate jdbc, PasswordHasher passwordHasher) {
        this.jdbc = jdbc;
        this.passwordHasher = passwordHasher;
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> register(@RequestBody Map<String, Object> body) {
        String customerCode = (String) body.get("customerCode");
        String customerName = (String) body.get("customerName");
        String contactName = (String) body.get("contactName");
        String email = (String) body.get("email");
        String phone = (String) body.get("phone");
        String password = (String) body.get("password");

        if (customerCode == null || customerCode.isBlank())
            throw ApiException.badRequest("客户编号必填");
        if (!customerCode.matches("[\\x00-\\x7F]+"))
            throw ApiException.badRequest("客户编号不能包含中文");
        if (customerName == null || customerName.isBlank())
            throw ApiException.badRequest("公司名称必填");
        if (contactName == null || contactName.isBlank())
            throw ApiException.badRequest("联系人姓名必填");
        if (email == null || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))
            throw ApiException.badRequest("邮箱格式不正确");
        if (password == null || password.length() < 5)
            throw ApiException.badRequest("密码长度要大于 5");
        if (phone != null && !phone.isBlank() && !phone.matches("\\+?[0-9\\-\\s]{6,20}"))
            throw ApiException.badRequest("电话格式不正确");

        // 唯一性校验
        Integer dupCustomer = jdbc.queryForObject(
            "SELECT count(*) FROM customers WHERE code = ?", Integer.class, customerCode);
        if (dupCustomer != null && dupCustomer > 0)
            throw ApiException.badRequest("客户编号已存在: " + customerCode);

        Integer dupEmail = jdbc.queryForObject(
            "SELECT count(*) FROM users WHERE email = ?", Integer.class, email);
        if (dupEmail != null && dupEmail > 0)
            throw ApiException.badRequest("该邮箱已注册: " + email);

        // 设 tenant 上下文
        jdbc.execute("SELECT set_config('app.current_tenant_id', '2bda8c16-7b19-4ce6-ab71-9584f5a140ed', true)");

        // 建客户（pending 审核）
        String customerId = jdbc.queryForObject("""
            INSERT INTO customers (tenant_id, code, name, contacts, email, mobile, audit_status)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?, ?, ?, ?, 'PENDING')
            RETURNING id::text
            """, String.class, customerCode, customerName, contactName, email, phone);

        // 建账号（status=DISABLED 待 admin 激活）
        String username = customerCode.toLowerCase();
        String userId = jdbc.queryForObject("""
            INSERT INTO users (
              tenant_id, username, email, display_name, role_code, password_hash, status,
              bound_customer_id, user_grade
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid, ?, ?, ?, 'CUSTOMER_PORTAL', ?, 'DISABLED',
              ?::uuid, '1'
            )
            RETURNING id::text
            """, String.class, username, email, contactName,
                 passwordHasher.hash(password), customerId);

        // 给客户portal 默认 OPERATOR role
        try {
            jdbc.update("""
                INSERT INTO user_roles (tenant_id, user_id, role_id)
                SELECT u.tenant_id, u.id, r.id FROM users u
                  JOIN roles r ON r.tenant_id = u.tenant_id AND r.code = 'OPERATOR'
                 WHERE u.id = ?::uuid
                """, userId);
        } catch (Exception ex) {
            LOGGER.warn("OPERATOR role 关联失败: {}", ex.getMessage());
        }

        return Map.of(
            "customerId", customerId,
            "customerCode", customerCode,
            "userId", userId,
            "username", username,
            "status", "PENDING_APPROVAL",
            "message", "注册成功，等待管理员审核激活账号"
        );
    }
}
