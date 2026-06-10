package com.xqt.saas.acc;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * ACC 风格单号生成器。
 *
 * 算法参考 ACC inc/AdminClass.php:3724 getCompanyNo：
 *   No = date('Ymd') + 3 位大写字母（A-Z 去 O，共 25 字母）
 *
 * 并发安全：用 order_no_registry 表主键唯一约束抢占，INSERT 成功即占有；
 * 50 次抢占失败抛异常（25^3 = 15625 个槽位/天，正常永远不应该撞 50 次）。
 *
 * 不直接依赖 orders 表 — 通用化为"单号注册中心"，业务方决定 used_for（ORDER / SHIPMENT / BILL）。
 */
@Component
public class OrderNoGenerator {
    private static final String SINGLE_TENANT = "2bda8c16-7b19-4ce6-ab71-9584f5a140ed";
    private static final String LETTERS = "ABCDEFGHIJKLMNPQRSTUVWXYZ"; // 25 letters (no O)
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_ATTEMPTS = 50;

    private final JdbcTemplate jdbc;
    private final SecureRandom rng = new SecureRandom();

    public OrderNoGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 默认用法：生成 ORDER 用途、当天的单号。 */
    public String generate() {
        return generate("ORDER", LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }

    /** 指定用途（对应 ACC _No.Name：ORDER / SHIPMENT / BILL / MAIN）。 */
    public String generate(String usedFor) {
        return generate(usedFor, LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }

    public String generate(String usedFor, LocalDate date) {
        String datePrefix = date.format(DATE_FMT);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = datePrefix + randomThreeLetters();
            try {
                jdbc.update("""
                    INSERT INTO order_no_registry (order_no, tenant_id, the_date, used_for)
                    VALUES (?, ?::uuid, ?, ?)
                    """, candidate, SINGLE_TENANT, java.sql.Date.valueOf(date), usedFor);
                return candidate;
            } catch (DataAccessException dup) {
                // 主键冲突 → 重摇
            }
        }
        throw new IllegalStateException("OrderNoGenerator: 50 次抢占都失败，注册表当天可能已满（异常）");
    }

    private String randomThreeLetters() {
        char[] buf = new char[3];
        for (int i = 0; i < 3; i++) {
            buf[i] = LETTERS.charAt(rng.nextInt(LETTERS.length()));
        }
        return new String(buf);
    }
}
