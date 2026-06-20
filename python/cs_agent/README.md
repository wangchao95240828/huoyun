# xqt-saas 智能客服 Agent

跨境物流场景智能客服 — FastAPI + DeepSeek + tool-use + pgvector RAG (规划中)

## 架构

```
Spring Boot ──HTTP──> FastAPI (本服务) ─┬─> DeepSeek API (chat 生成 + tool 路由)
                                         ├─> PostgreSQL (业务 tool-call)
                                         │     ↓
                                         │  query_tracking / query_bill / create_ask
                                         └─> pgvector (RAG: ai_knowledge_base) 
                                              ↓
                                           BGE-M3 嵌入 (phase 2)
```

## 部署

```bash
docker build -t cs-agent:latest .
docker run -d --name cs-agent --network host \
  -e DEEPSEEK_API_KEY=sk-... \
  -e PG_DSN="postgresql://xqt:pwd@localhost:15432/xqt_saas" \
  -e CS_AGENT_TOKEN=<生成一个> \
  -p 127.0.0.1:8101:8101 \
  cs-agent:latest
```

## API

POST /chat (X-Cs-Token: ...)

```json
{
  "session_id": "uuid",
  "messages": [
    {"role": "user", "content": "我的订单 DEMO-US-1234 到哪了?"}
  ],
  "customer_code": "DOC-DEMO",
  "customer_name": "制单流程样例客户"
}
```

返回:

```json
{
  "content": "您的订单 DEMO-US-1234 已到美国 Memphis 中转, ...",
  "tool_calls": [{"tool": "query_tracking", "args": {...}, "result": {...}}],
  "confidence": 0.95,
  "escalate": false,
  "tokens_used": 1247,
  "latency_ms": 2100
}
```

## 幻觉控制

1. **硬绑 tool-use**: tracking/账单等具体数字必须调函数, 不允许编造
2. **confidence < 0.6 自动 escalate**: 转人工 (写 acc_asks 工单 OPEN)
3. **temperature=0.3**: 防 LLM 想象
4. **system prompt 铁律**: 不知道就说不知道

## Phase 2 待办

- BGE-M3 嵌入接入 (替换 search_kb 的 keyword 兜底为语义检索)
- LangGraph 状态机 (multi-turn 状态保持 + 复杂工作流)
- Streaming response (SSE)
- 用量计费 (按 session 累计 total_tokens)
