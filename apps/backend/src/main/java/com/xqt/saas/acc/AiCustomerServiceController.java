package com.xqt.saas.acc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/acc/ai-cs — 智能客服 Spring Boot 代理
 *
 * 工作流:
 *  1. 前端 POST /chat → 创建/复用 ai_chat_sessions 会话
 *  2. 持久化 user 消息到 ai_chat_messages
 *  3. 调用 Python cs-agent (http://localhost:8101/chat) 跑 LLM + tool-use
 *  4. 持久化 assistant 回复(含 tool_calls + confidence + tokens)
 *  5. escalate=true 时, 关联 acc_asks 工单
 *  6. 返回回复给前端
 *
 * 会话列表 / 历史查询走另外的 GET endpoint, 给客服后台监控用.
 */
@RestController
@RequestMapping("/api/acc/ai-cs")
public class AiCustomerServiceController {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    @Value("${ai.cs.agent.url:http://localhost:8101}")
    private String agentUrl;

    @Value("${ai.cs.agent.token:}")
    private String agentToken;

    public AiCustomerServiceController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** POST /chat — 一次对话. body: { session_id?, message, customer_code?, customer_name? } */
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, Object> body) {
        String userMsg = (String) body.get("message");
        if (userMsg == null || userMsg.isBlank()) {
            throw ApiException.badRequest("message 必填");
        }
        String customerCode = (String) body.get("customer_code");
        String customerName = (String) body.get("customer_name");
        // 1. 找/建会话
        String sessionId = (String) body.get("session_id");
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = jdbc.queryForObject("""
                INSERT INTO ai_chat_sessions (tenant_id, customer_id, source)
                VALUES (current_setting('app.current_tenant_id')::uuid,
                        (SELECT id FROM customers WHERE code = ? LIMIT 1),
                        'staff')
                RETURNING id::text
                """, String.class, customerCode);
        }
        // 2. 持久化 user 消息
        jdbc.update("""
            INSERT INTO ai_chat_messages (tenant_id, session_id, role, content)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, 'user', ?)
            """, sessionId, userMsg);

        // 3. 取最近 20 条历史发给 agent (multi-turn)
        List<Map<String, Object>> history = jdbc.queryForList("""
            SELECT role, content FROM ai_chat_messages
            WHERE session_id = ?::uuid AND role IN ('user','assistant')
            ORDER BY created_at DESC LIMIT 20
            """, sessionId);
        // reverse to chronological order
        java.util.Collections.reverse(history);
        List<Map<String, Object>> messages = history.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", r.get("role"));
            m.put("content", r.get("content"));
            return m;
        }).toList();

        // 4. 调 Python agent
        Map<String, Object> agentReq = new LinkedHashMap<>();
        agentReq.put("session_id", sessionId);
        agentReq.put("messages", messages);
        if (customerCode != null) agentReq.put("customer_code", customerCode);
        if (customerName != null) agentReq.put("customer_name", customerName);
        Map<String, Object> agentResp;
        try {
            String reqJson = mapper.writeValueAsString(agentReq);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(agentUrl + "/chat"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("X-Cs-Token", agentToken)
                .POST(HttpRequest.BodyPublishers.ofString(reqJson))
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw ApiException.badRequest("agent 异常 " + resp.statusCode() + ": " + resp.body().substring(0, Math.min(200, resp.body().length())));
            }
            agentResp = mapper.readValue(resp.body(), Map.class);
        } catch (Exception e) {
            throw ApiException.badRequest("agent 调用失败: " + e.getMessage());
        }

        // 5. 持久化 assistant 回复
        String content = (String) agentResp.getOrDefault("content", "");
        Object toolCalls = agentResp.get("tool_calls");
        Number confidence = (Number) agentResp.getOrDefault("confidence", 1.0);
        Boolean escalate = (Boolean) agentResp.getOrDefault("escalate", false);
        Number tokens = (Number) agentResp.getOrDefault("tokens_used", 0);
        Number latency = (Number) agentResp.getOrDefault("latency_ms", 0);

        String toolCallsJson = null;
        try { toolCallsJson = mapper.writeValueAsString(toolCalls); } catch (Exception ignored) {}

        jdbc.update("""
            INSERT INTO ai_chat_messages
              (tenant_id, session_id, role, content, tool_calls, confidence, tokens_used, latency_ms)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, 'assistant', ?, ?::jsonb, ?, ?, ?)
            """, sessionId, content, toolCallsJson, confidence.doubleValue(),
            tokens.intValue(), latency.intValue());

        // 6. session 累计 + escalate 状态
        jdbc.update("""
            UPDATE ai_chat_sessions SET
              turn_count = turn_count + 1,
              total_tokens = total_tokens + ?,
              last_active_at = now(),
              status = CASE WHEN ?::boolean = true THEN 'ESCALATED' ELSE status END
            WHERE id = ?::uuid
            """, tokens.intValue(), escalate != null && escalate, sessionId);

        // 返回前端
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("session_id", sessionId);
        out.put("content", content);
        out.put("tool_calls", toolCalls);
        out.put("confidence", confidence);
        out.put("escalate", escalate);
        out.put("tokens_used", tokens);
        out.put("latency_ms", latency);
        return out;
    }

    /** GET /sessions — 会话列表(给客服后台监控) */
    @GetMapping("/sessions")
    public Map<String, Object> sessions(
        @RequestParam(required = false, defaultValue = "1") Integer page,
        @RequestParam(required = false, defaultValue = "20") Integer pageSize,
        @RequestParam(required = false) String status
    ) {
        int limit = Math.max(1, Math.min(100, pageSize));
        int offset = Math.max(0, (page - 1) * limit);
        String statusFilter = status == null || status.isBlank() ? null : status;
        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM ai_chat_sessions
            WHERE (?::text IS NULL OR status = ?)
            """, Long.class, statusFilter, statusFilter);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT s.id::text, s.customer_id::text, c.code AS customer_code, c.name AS customer_name,
                   s.source, s.escalated_ask_id::text, s.status, s.turn_count, s.total_tokens,
                   s.created_at, s.last_active_at, s.closed_at
            FROM ai_chat_sessions s
            LEFT JOIN customers c ON c.id = s.customer_id
            WHERE (?::text IS NULL OR s.status = ?)
            ORDER BY s.last_active_at DESC
            LIMIT ? OFFSET ?
            """, statusFilter, statusFilter, limit, offset);
        return Map.of("data", rows.stream().map(this::projectSession).toList(),
                      "total", total == null ? 0 : total);
    }

    /** GET /sessions/{id}/messages — 会话历史(给客服回放) */
    @GetMapping("/sessions/{id}/messages")
    public Map<String, Object> sessionMessages(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text, role, content, tool_calls, confidence,
                   tokens_used, latency_ms, created_at
            FROM ai_chat_messages
            WHERE session_id = ?::uuid
            ORDER BY created_at
            """, id);
        return Map.of("data", rows.stream().map(this::projectMessage).toList());
    }

    /** GET /kb — RAG 知识库列表 (内容运维) */
    @GetMapping("/kb")
    public Map<String, Object> kbList(
        @RequestParam(required = false, defaultValue = "1") Integer page,
        @RequestParam(required = false, defaultValue = "50") Integer pageSize,
        @RequestParam(required = false) String category
    ) {
        int limit = Math.max(1, Math.min(100, pageSize));
        int offset = Math.max(0, (page - 1) * limit);
        String catFilter = category == null || category.isBlank() ? null : category;
        Long total = jdbc.queryForObject(
            "SELECT count(*) FROM ai_knowledge_base WHERE is_active = true AND (?::text IS NULL OR category = ?)",
            Long.class, catFilter, catFilter);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text, category, title, summary, hit_count, created_at
            FROM ai_knowledge_base
            WHERE is_active = true AND (?::text IS NULL OR category = ?)
            ORDER BY hit_count DESC, created_at DESC
            LIMIT ? OFFSET ?
            """, catFilter, catFilter, limit, offset);
        return Map.of("data", rows.stream().map(this::projectKb).toList(),
                      "total", total == null ? 0 : total);
    }

    /** POST /kb — 新增 KB 条目 (内容运维) */
    @PostMapping("/kb")
    public Map<String, Object> kbCreate(@RequestBody Map<String, Object> body) {
        String category = (String) body.get("category");
        String title = (String) body.get("title");
        String bodyText = (String) body.get("body");
        if (category == null || title == null || bodyText == null) {
            throw ApiException.badRequest("category/title/body 必填");
        }
        String summary = (String) body.getOrDefault("summary", bodyText.length() > 200 ? bodyText.substring(0, 200) : bodyText);
        String id = jdbc.queryForObject("""
            INSERT INTO ai_knowledge_base (tenant_id, category, title, summary, body)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?, ?, ?)
            RETURNING id::text
            """, String.class, category, title, summary, bodyText);
        return Map.of("id", id);
    }

    private Map<String, Object> projectSession(Map<String, Object> r) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", r.get("id"));
        o.put("customerId", r.get("customer_id"));
        o.put("customerCode", r.get("customer_code"));
        o.put("customerName", r.get("customer_name"));
        o.put("source", r.get("source"));
        o.put("escalatedAskId", r.get("escalated_ask_id"));
        o.put("status", r.get("status"));
        o.put("turnCount", r.get("turn_count"));
        o.put("totalTokens", r.get("total_tokens"));
        o.put("createdAt", json.value(r.get("created_at")));
        o.put("lastActiveAt", json.value(r.get("last_active_at")));
        o.put("closedAt", json.value(r.get("closed_at")));
        return o;
    }

    private Map<String, Object> projectMessage(Map<String, Object> r) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", r.get("id"));
        o.put("role", r.get("role"));
        o.put("content", r.get("content"));
        o.put("toolCalls", r.get("tool_calls"));
        o.put("confidence", r.get("confidence"));
        o.put("tokensUsed", r.get("tokens_used"));
        o.put("latencyMs", r.get("latency_ms"));
        o.put("createdAt", json.value(r.get("created_at")));
        return o;
    }

    private Map<String, Object> projectKb(Map<String, Object> r) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", r.get("id"));
        o.put("category", r.get("category"));
        o.put("title", r.get("title"));
        o.put("summary", r.get("summary"));
        o.put("hitCount", r.get("hit_count"));
        o.put("createdAt", json.value(r.get("created_at")));
        return o;
    }
}
