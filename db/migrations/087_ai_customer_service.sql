-- ═════════════════════════════════════════════════════════════════════════
-- 087_ai_customer_service
--
-- 智能客服 (AI Customer Service Agent) 4 张表 + pgvector 扩展
-- 架构: Spring Boot --HTTP--> Python FastAPI (DeepSeek + LangGraph + tool-use)
--                              ↓ RAG
--                          pgvector (复用 PG)
--                              ↓ tool-call
--                          PG 业务表 (orders / shipments / tracking_events / charges)
--
-- 设计原则 (基于幻觉控制):
-- 1. tool-call 硬绑业务表 — bot 不能凭记忆生成包裹位置, 必须调函数读 PG
-- 2. 所有问答记录可溯源 - ai_messages.tool_calls + ai_messages.sources
-- 3. 低 confidence 自动转人工 - 触发器: 创建 acc_asks (status=OPEN) 工单
-- 4. 多租户隔离 - tenant_id 强制
-- ═════════════════════════════════════════════════════════════════════════

-- 启用 pgvector (>=v0.5 自带 HNSW 索引)
CREATE EXTENSION IF NOT EXISTS vector;

-- ───────────────────────────────────────────────────────────────────────────
-- 表 1: ai_chat_sessions — 会话(一个客户的一段对话, 关闭后归档)
-- ───────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ai_chat_sessions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL DEFAULT current_setting('app.current_tenant_id')::uuid,
    customer_id     uuid REFERENCES customers(id),
    -- 来源: customer-portal(客户自助) / staff(内部客服上手测试) / api(对外 SDK 调用)
    source          text NOT NULL DEFAULT 'staff' CHECK (source IN ('customer-portal','staff','api')),
    -- 关联: 如果会话最终转工单, 记 ask_id 关联到 acc_asks
    escalated_ask_id uuid REFERENCES acc_asks(id),
    -- 会话总轮数 + 累计 token 用量 (deepseek 计费用)
    turn_count      int NOT NULL DEFAULT 0,
    total_tokens    int NOT NULL DEFAULT 0,
    -- closed_reason: resolved(用户满意) / escalated(转工单) / abandoned(客户离线) / timeout
    status          text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RESOLVED','ESCALATED','ABANDONED')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    closed_at       timestamptz,
    last_active_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ai_chat_sessions_tenant_status
    ON ai_chat_sessions (tenant_id, status, last_active_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_chat_sessions_customer
    ON ai_chat_sessions (tenant_id, customer_id, created_at DESC) WHERE customer_id IS NOT NULL;

-- ───────────────────────────────────────────────────────────────────────────
-- 表 2: ai_chat_messages — 每条消息(user / assistant / tool)
-- ───────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ai_chat_messages (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL DEFAULT current_setting('app.current_tenant_id')::uuid,
    session_id      uuid NOT NULL REFERENCES ai_chat_sessions(id) ON DELETE CASCADE,
    -- role: user(客户提问) / assistant(bot 回复) / tool(工具调用结果) / system(系统提示)
    role            text NOT NULL CHECK (role IN ('user','assistant','tool','system')),
    content         text NOT NULL,
    -- tool_calls: assistant 调用了哪些工具 (json: [{tool_name, arguments, result}])
    tool_calls      jsonb,
    -- sources: RAG 命中的知识库片段 ([{kb_id, score, snippet}])
    sources         jsonb,
    -- confidence: bot 自评 0.0-1.0, 低于阈值 → 转工单
    confidence      numeric(3,2),
    -- 用量
    tokens_used     int,
    latency_ms      int,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ai_chat_messages_session
    ON ai_chat_messages (session_id, created_at);

-- ───────────────────────────────────────────────────────────────────────────
-- 表 3: ai_knowledge_base — RAG 知识库(政策/FAQ/常见问题)
-- 用 BGE-M3 嵌入 (1024 dim) 存 PG vector 列
-- ───────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ai_knowledge_base (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL DEFAULT current_setting('app.current_tenant_id')::uuid,
    -- 分类: faq(常见问答) / policy(运营政策) / tracking-status(轨迹解释) / claim-guide(赔偿引导)
    category        text NOT NULL,
    title           text NOT NULL,
    -- 完整正文 (markdown 格式, 客服可后台维护)
    body            text NOT NULL,
    -- 短摘要(检索时优先匹配, 默认取 body 前 200 字)
    summary         text,
    embedding       vector(1024),
    -- 元信息: 语言/适用渠道/有效期等
    metadata        jsonb DEFAULT '{}',
    -- 软删除 (而非物理删除, 避免历史会话引用断链)
    is_active       boolean NOT NULL DEFAULT true,
    hit_count       int NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
-- HNSW 索引 (PG vector >=0.5 内置, 比 IVFFlat 召回更高)
-- m=16/ef_construction=64 是 1024 dim 的合理默认
CREATE INDEX IF NOT EXISTS idx_ai_kb_embedding_hnsw
    ON ai_knowledge_base USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64)
    WHERE is_active = true;
CREATE INDEX IF NOT EXISTS idx_ai_kb_tenant_category
    ON ai_knowledge_base (tenant_id, category) WHERE is_active = true;

-- ───────────────────────────────────────────────────────────────────────────
-- 表 4: ai_tool_call_log — 工具调用流水(用于审计 + 性能监控)
-- 每次 bot 调函数(查 tracking / 查账单 / 申赔等)都记一条
-- ───────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ai_tool_call_log (
    id              bigserial PRIMARY KEY,
    tenant_id       uuid NOT NULL DEFAULT current_setting('app.current_tenant_id')::uuid,
    session_id      uuid REFERENCES ai_chat_sessions(id),
    message_id      uuid REFERENCES ai_chat_messages(id),
    tool_name       text NOT NULL,
    arguments       jsonb,
    result          jsonb,
    error           text,
    latency_ms      int,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ai_tool_log_tenant_tool
    ON ai_tool_call_log (tenant_id, tool_name, created_at DESC);

COMMENT ON TABLE ai_chat_sessions IS '智能客服会话主表 - 一个客户连续提问归一个 session';
COMMENT ON TABLE ai_chat_messages IS '会话消息流水 - role=user/assistant/tool/system';
COMMENT ON TABLE ai_knowledge_base IS 'RAG 知识库 - BGE-M3 1024 dim 嵌入, HNSW 索引';
COMMENT ON TABLE ai_tool_call_log IS '工具调用审计日志 - 监控幻觉率 + 性能';
