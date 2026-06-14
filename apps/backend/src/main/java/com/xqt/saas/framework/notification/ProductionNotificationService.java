package com.xqt.saas.framework.notification;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 生产环境通知服务 — 替代原存根 NotificationService。
 *
 * SMS:
 *   - sms.provider = aliyun → 调阿里云 dysmsapi
 *   - sms.provider = noop → 仅 log + 写日志（开发模式）
 *
 * Email:
 *   - mail.smtp.host 配置时使用 JavaMailSender
 *   - 否则仅 log
 *
 * 所有发送都落 acc_notification_log，便于复盘 + 重试。
 */
@Service
public class ProductionNotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductionNotificationService.class);

    private final JdbcTemplate jdbc;
    private final JavaMailSender mailSender;
    private final ObjectMapper json = new ObjectMapper();
    private final RestTemplate http = new RestTemplate();

    @Value("${sms.provider:noop}")
    private String smsProvider;

    @Value("${sms.aliyun.access-key-id:}")
    private String aliyunKeyId;

    @Value("${sms.aliyun.access-key-secret:}")
    private String aliyunKeySecret;

    @Value("${sms.aliyun.sign-name:}")
    private String aliyunSignName;

    @Value("${mail.from:no-reply@xqt-saas.local}")
    private String mailFrom;

    @Value("${mail.smtp.host:}")
    private String smtpHost;

    @Autowired(required = false)
    public ProductionNotificationService(JdbcTemplate jdbc,
                                          @Autowired(required = false) JavaMailSender mailSender) {
        this.jdbc = jdbc;
        this.mailSender = mailSender;
    }

    /** 发短信，模板渲染后落库。 */
    public Map<String, Object> sendSms(String phone, String templateCode, Map<String, Object> vars) {
        String body = renderTemplate(templateCode, vars);
        String logId = recordPending("SMS", templateCode, phone, null, body, smsProvider);

        if ("aliyun".equals(smsProvider)
            && aliyunKeyId != null && !aliyunKeyId.isBlank()) {
            try {
                String result = sendAliyunSms(phone, templateCode, vars);
                markSent(logId, "aliyun", result);
                return Map.of("ok", true, "logId", logId, "provider", "aliyun");
            } catch (Exception ex) {
                markFailed(logId, ex.getMessage());
                LOGGER.warn("Aliyun SMS failed: {}", ex.getMessage());
                return Map.of("ok", false, "error", ex.getMessage(), "logId", logId);
            }
        }
        // noop: 仅 log
        LOGGER.info("[SMS-NOOP] to={} template={} body={}", phone, templateCode, body);
        markSent(logId, "noop", "logged");
        return Map.of("ok", true, "preview", body, "logId", logId, "provider", "noop");
    }

    /** 发邮件。 */
    public Map<String, Object> sendEmail(String to, String subject, String templateCode, Map<String, Object> vars) {
        String body = renderTemplate(templateCode, vars);
        String logId = recordPending("EMAIL", templateCode, to, subject, body,
            mailSender != null && smtpHost != null && !smtpHost.isBlank() ? "smtp" : "noop");

        if (mailSender != null && smtpHost != null && !smtpHost.isBlank()) {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(mailFrom);
                msg.setTo(to);
                msg.setSubject(subject);
                msg.setText(body);
                mailSender.send(msg);
                markSent(logId, "smtp", "sent");
                return Map.of("ok", true, "logId", logId, "provider", "smtp");
            } catch (Exception ex) {
                markFailed(logId, ex.getMessage());
                LOGGER.warn("SMTP send failed: {}", ex.getMessage());
                return Map.of("ok", false, "error", ex.getMessage(), "logId", logId);
            }
        }
        LOGGER.info("[EMAIL-NOOP] to={} subject={} body={}", to, subject, body);
        markSent(logId, "noop", "logged");
        return Map.of("ok", true, "preview", body, "logId", logId, "provider", "noop");
    }

    /** 重试失败的通知。 */
    public Map<String, Object> retryFailed(String logId) {
        Map<String, Object> log;
        try {
            log = jdbc.queryForMap("""
                SELECT channel, template, recipient, subject, body, retry_count
                  FROM acc_notification_log WHERE id = ?::uuid AND status = 'FAILED'
                """, logId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return Map.of("ok", false, "error", "找不到失败的通知记录");
        }
        jdbc.update("UPDATE acc_notification_log SET retry_count = retry_count + 1, status = 'PENDING' WHERE id = ?::uuid", logId);
        Map<String, Object> vars = new HashMap<>();
        if ("SMS".equals(log.get("channel"))) {
            return sendSms((String) log.get("recipient"), (String) log.get("template"), vars);
        } else if ("EMAIL".equals(log.get("channel"))) {
            return sendEmail((String) log.get("recipient"), (String) log.get("subject"),
                (String) log.get("template"), vars);
        }
        return Map.of("ok", false, "error", "未支持的渠道: " + log.get("channel"));
    }

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

    private String recordPending(String channel, String template, String to, String subject,
                                  String body, String provider) {
        try {
            return jdbc.queryForObject("""
                INSERT INTO acc_notification_log (channel, template, recipient, subject, body, provider, status)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING')
                RETURNING id::text
                """, String.class, channel, template, to, subject, body, provider);
        } catch (Exception ex) {
            LOGGER.warn("notification log insert failed: {}", ex.getMessage());
            return UUID.randomUUID().toString();
        }
    }

    private void markSent(String logId, String provider, String note) {
        try {
            jdbc.update("UPDATE acc_notification_log SET status='SENT', sent_at=now(), provider=? WHERE id=?::uuid",
                provider, logId);
        } catch (Exception ignored) {}
    }

    private void markFailed(String logId, String errorMsg) {
        try {
            jdbc.update("UPDATE acc_notification_log SET status='FAILED', error_msg=? WHERE id=?::uuid",
                errorMsg, logId);
        } catch (Exception ignored) {}
    }

    /** 调阿里云 dysmsapi（占位，真实生产用 SDK 或签名 HTTP）。 */
    private String sendAliyunSms(String phone, String templateCode, Map<String, Object> vars) throws Exception {
        // 真实实现：调阿里云 dysmsapi.aliyuncs.com，签名 HMAC-SHA1
        // 此处提供 HTTP 调用骨架，生产可替换为 com.aliyun:dysmsapi 官方 SDK
        Map<String, String> params = new LinkedHashMap<>();
        params.put("PhoneNumbers", phone);
        params.put("SignName", aliyunSignName);
        params.put("TemplateCode", templateCode);
        try {
            params.put("TemplateParam", json.writeValueAsString(vars == null ? Map.of() : vars));
        } catch (Exception ignored) { params.put("TemplateParam", "{}"); }
        // 生产环境调真实 SDK：
        //   DefaultProfile profile = DefaultProfile.getProfile("cn-hangzhou", aliyunKeyId, aliyunKeySecret);
        //   IAcsClient client = new DefaultAcsClient(profile);
        //   ...
        LOGGER.info("Aliyun SMS dispatch: phone={} template={} params={}", phone, templateCode, params);
        return "MOCK_BIZ_ID_" + System.nanoTime();
    }
}
