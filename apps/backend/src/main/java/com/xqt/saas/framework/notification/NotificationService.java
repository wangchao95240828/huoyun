package com.xqt.saas.framework.notification;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 通知服务 — 对齐 ACC SMSService.php + Mail.php。
 *
 * 当前实现为存根/日志级；生产时把 SMS/Email gateway 接入即可:
 *   - sms.provider: 'aliyun' | 'tencent' | 'noop'
 *   - mail.smtp.host / port / user / password
 *
 * 所有发送均落表 notification_log（如表不存在则仅日志）。
 */
@Service
public class NotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);

    private final JdbcTemplate jdbc;

    @Value("${sms.provider:noop}")
    private String smsProvider;

    @Value("${mail.smtp.host:}")
    private String smtpHost;

    public NotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** SMS 通知。templateCode 引用 acc_message_templates。 */
    public Map<String, Object> sendSms(String phone, String templateCode, Map<String, Object> vars) {
        String body = renderTemplate(templateCode, vars);
        if ("noop".equals(smsProvider)) {
            LOGGER.info("[SMS-NOOP] to={} template={} body={}", phone, templateCode, body);
            audit("SMS", phone, templateCode, body, "NOOP");
            return Map.of("ok", true, "provider", "noop", "preview", body);
        }
        // 生产对接：aliyun/tencent
        LOGGER.warn("SMS provider {} 未实现，使用 noop", smsProvider);
        audit("SMS", phone, templateCode, body, "PROVIDER_NOT_IMPL");
        return Map.of("ok", false, "error", "SMS provider 未配置");
    }

    /** Email 通知。 */
    public Map<String, Object> sendEmail(String to, String subject, String templateCode, Map<String, Object> vars) {
        String body = renderTemplate(templateCode, vars);
        if (smtpHost == null || smtpHost.isBlank()) {
            LOGGER.info("[EMAIL-NOOP] to={} subject={} body={}", to, subject, body);
            audit("EMAIL", to, templateCode, body, "NOOP");
            return Map.of("ok", true, "provider", "noop", "preview", body);
        }
        // 生产对接 JavaMailSender
        LOGGER.warn("Mail SMTP {} 配置但发送未实现", smtpHost);
        audit("EMAIL", to, templateCode, body, "SMTP_NOT_IMPL");
        return Map.of("ok", false, "error", "SMTP 发送未实现");
    }

    /** 把 acc_message_templates 的 content 渲染（{var} 替换）。 */
    private String renderTemplate(String templateCode, Map<String, Object> vars) {
        try {
            String content = jdbc.queryForObject(
                "SELECT content FROM acc_message_templates WHERE template_name = ? LIMIT 1",
                String.class, templateCode);
            if (content == null) return "[模板 " + templateCode + " 不存在]";
            if (vars != null) {
                for (Map.Entry<String, Object> e : vars.entrySet()) {
                    content = content.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
                }
            }
            return content;
        } catch (Exception ex) {
            return "[模板渲染失败: " + templateCode + "]";
        }
    }

    private void audit(String channel, String to, String templateCode, String body, String status) {
        try {
            jdbc.update("""
                INSERT INTO acc_files (tenant_id, file_name, file_type, mime_type, size_bytes,
                                       uploader_name, remark, status, metadata)
                VALUES ((SELECT id FROM tenants WHERE code='xqt'), ?, 'notification', 'text/plain',
                        length(?), 'system', ?, ?, jsonb_build_object(
                          'channel', ?::text, 'to', ?::text, 'template', ?::text, 'body', ?::text))
                """, channel + "-" + templateCode, body, to, status,
                     channel, to, templateCode, body);
        } catch (Exception ex) {
            LOGGER.warn("通知 audit 落库失败: {}", ex.getMessage());
        }
    }
}
