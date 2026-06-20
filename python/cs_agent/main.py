"""
xqt-saas 智能客服 Agent (FastAPI)

架构:
  Spring Boot ──HTTP──> FastAPI ─┬─> DeepSeek (chat 生成 + tool-use 路由)
                                  ├─> PostgreSQL (业务 tool-call: tracking / 账单 / 赔偿)
                                  └─> pgvector (RAG: FAQ / 政策)

启动:
  uvicorn main:app --host=0.0.0.0 --port=8101

ENV:
  DEEPSEEK_API_KEY=sk-...
  DEEPSEEK_MODEL=deepseek-chat (默认)
  DEEPSEEK_BASE_URL=https://api.deepseek.com (默认)
  PG_DSN=postgresql://xqt:pwd@host:port/xqt_saas
  CS_AGENT_TOKEN=...   (Spring Boot 调用本服务时校验)

核心约束 (幻觉控制):
  1. tracking / 账单 / 订单状态等具体业务问题, 必须走 tool-call 读 PG
     bot 不能凭记忆生成包裹位置
  2. confidence < 0.6 自动转人工 (返回 escalate=true)
  3. 每条 tool-call 记 ai_tool_call_log
"""
import os
import time
import json
import logging
from typing import Optional
from contextlib import contextmanager

from fastapi import FastAPI, HTTPException, Depends, Header
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
import httpx
import psycopg

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("cs-agent")

DEEPSEEK_API_KEY = os.environ.get("DEEPSEEK_API_KEY", "")
DEEPSEEK_MODEL = os.environ.get("DEEPSEEK_MODEL", "deepseek-chat")
DEEPSEEK_BASE_URL = os.environ.get("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
PG_DSN = os.environ.get("PG_DSN", "")
CS_AGENT_TOKEN = os.environ.get("CS_AGENT_TOKEN", "")
TENANT_ID = os.environ.get("DEFAULT_TENANT_ID", "2bda8c16-7b19-4ce6-ab71-9584f5a140ed")
CONFIDENCE_THRESHOLD = float(os.environ.get("CONFIDENCE_THRESHOLD", "0.6"))

app = FastAPI(
    title="xqt-saas 智能客服 Agent",
    version="0.1.0",
    description="跨境物流智能客服 - DeepSeek + tool-use + pgvector RAG",
)


# ════════ Auth ════════
def check_token(x_cs_token: Optional[str] = Header(None)):
    if CS_AGENT_TOKEN and x_cs_token != CS_AGENT_TOKEN:
        raise HTTPException(status_code=401, detail="invalid X-Cs-Token")


# ════════ PG helper ════════
@contextmanager
def pg_conn():
    conn = psycopg.connect(PG_DSN)
    try:
        with conn.cursor() as cur:
            # SET 不接受参数, 用 set_config(name, value, is_local)
            cur.execute("SELECT set_config('app.current_tenant_id', %s, false)", (TENANT_ID,))
        yield conn
    finally:
        conn.close()


# ════════ Tools (硬绑业务表) ════════
def tool_query_tracking(order_no: str) -> dict:
    """查询订单转单号/轨迹/最新状态. order_no 是客户单号"""
    with pg_conn() as c, c.cursor() as cur:
        cur.execute("""
            SELECT o.id::text, o.order_no, o.customer_ref, o.status, o.created_at,
                   s.shipment_no, s.status AS shipment_status, s.destination_country
            FROM orders o
            LEFT JOIN shipment_order_links sol ON sol.order_id = o.id
            LEFT JOIN shipments s ON s.id = sol.shipment_id
            WHERE o.order_no = %s OR o.customer_ref = %s
            LIMIT 1
        """, (order_no, order_no))
        row = cur.fetchone()
        if not row:
            return {"found": False, "message": f"未找到订单 {order_no}"}
        cur.execute("""
            SELECT event_code, event_time, location, description
            FROM tracking_events
            WHERE order_id = %s
            ORDER BY event_time DESC LIMIT 10
        """, (row[0],))
        events = [{"code": r[0], "time": str(r[1]), "location": r[2], "desc": r[3]} for r in cur.fetchall()]
        return {
            "found": True,
            "order_no": row[1],
            "tracking_no": row[2],
            "order_status": row[3],
            "shipment_no": row[5],
            "shipment_status": row[6],
            "destination": row[7],
            "events": events,
        }


def tool_query_bill(customer_code: str, period: Optional[str] = None) -> dict:
    """查询客户账单. customer_code 是客户编号(SELLER-DEMO 等)"""
    with pg_conn() as c, c.cursor() as cur:
        cur.execute("SELECT id::text, name FROM customers WHERE code = %s LIMIT 1", (customer_code,))
        cust = cur.fetchone()
        if not cust:
            return {"found": False, "message": f"未找到客户 {customer_code}"}
        cur.execute("""
            SELECT invoice_no, invoice_date, total_amount, paid_amount, unpaid_amount, status, due_date
            FROM customer_invoices WHERE customer_id = %s
            ORDER BY invoice_date DESC LIMIT 5
        """, (cust[0],))
        bills = [{"no": r[0], "invoice_date": str(r[1]), "amount": float(r[2] or 0),
                  "paid": float(r[3] or 0), "unpaid": float(r[4] or 0),
                  "status": r[5], "due_date": str(r[6])} for r in cur.fetchall()]
        return {"found": True, "customer_code": customer_code, "customer_name": cust[1], "bills": bills}


def tool_search_kb(query: str, category: Optional[str] = None, top_k: int = 3) -> dict:
    """RAG: 检索知识库 FAQ/政策. phase 1 用关键词分词 OR 匹配 + 打分; phase 2 接 BGE-M3 嵌入"""
    # 中文分词: 空格切 + 单字过滤 (LLM 常传 "改地址 流程" 这种)
    terms = [t.strip() for t in query.split() if len(t.strip()) >= 2]
    if not terms:
        terms = [query]
    with pg_conn() as c, c.cursor() as cur:
        # 每个 term ILIKE OR (任一命中算召回), 命中数最多的排第一
        score_clause = " + ".join([f"(CASE WHEN title ILIKE %s OR body ILIKE %s OR summary ILIKE %s THEN 1 ELSE 0 END)"
                                   for _ in terms])
        match_clause = " OR ".join([f"(title ILIKE %s OR body ILIKE %s OR summary ILIKE %s)" for _ in terms])
        sql = f"""
            SELECT id::text, title, summary, body, category, ({score_clause}) AS score
            FROM ai_knowledge_base
            WHERE is_active = true AND ({match_clause})
        """
        params = []
        for t in terms:
            params.extend([f"%{t}%"] * 3)  # score
        for t in terms:
            params.extend([f"%{t}%"] * 3)  # match
        if category:
            sql += " AND category = %s"
            params.append(category)
        sql += " ORDER BY score DESC LIMIT %s"
        params.append(top_k)
        cur.execute(sql, params)
        # 返回完整 body 给 LLM, 不能只给 summary 否则 LLM 觉得信息不够会反复查
        hits = [{"id": r[0], "title": r[1], "summary": r[2] or "",
                 "body": r[3] or "", "category": r[4], "score": r[5]} for r in cur.fetchall()]
        return {"hits": hits, "count": len(hits), "query_terms": terms}


def tool_create_ask(shipment_ref: str, content: str, source: str = "ai-escalation") -> dict:
    """转人工: 在 acc_asks 表建一条 OPEN 状态工单"""
    with pg_conn() as c, c.cursor() as cur:
        # 找 shipment_id (按 shipment_no 或 customer_ref)
        cur.execute("""
            SELECT id::text FROM shipments
            WHERE shipment_no = %s OR customer_ref = %s
            LIMIT 1
        """, (shipment_ref, shipment_ref))
        ship = cur.fetchone()
        ship_id = ship[0] if ship else None
        cur.execute("""
            INSERT INTO acc_asks (tenant_id, shipment_id, customer_ref, content, source, ask_type, status, add_name)
            VALUES (current_setting('app.current_tenant_id')::uuid, %s, %s, %s, %s, 'AI_ESCALATED', 'OPEN', 'ai-agent')
            RETURNING id::text
        """, (ship_id, shipment_ref, content, source))
        ask_id = cur.fetchone()[0]
        c.commit()
        return {"escalated": True, "ask_id": ask_id, "message": "已转人工客服, 工单已创建"}


# Tool schema (OpenAI/DeepSeek 兼容格式)
TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "query_tracking",
            "description": "查询客户订单的轨迹/最新状态. 当用户问「我的包裹到哪了」「订单 XYZ 状态」时调用",
            "parameters": {
                "type": "object",
                "properties": {"order_no": {"type": "string", "description": "客户订单号或追踪号"}},
                "required": ["order_no"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "query_bill",
            "description": "查询客户账单/欠款. 当用户问「我有几笔账单」「这个月欠多少」时调用",
            "parameters": {
                "type": "object",
                "properties": {
                    "customer_code": {"type": "string", "description": "客户编号"},
                    "period": {"type": "string", "description": "可选, 期间 YYYY-MM"},
                },
                "required": ["customer_code"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_kb",
            "description": "搜索 FAQ/政策知识库. 当用户问通用问题(怎么改地址/赔偿流程/电池货物规定)时调用",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {"type": "string"},
                    "category": {"type": "string", "enum": ["faq", "policy", "tracking-status", "claim-guide"]},
                },
                "required": ["query"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "create_ask",
            "description": "转人工客服: 当无法回答 / 客户要求人工 / 涉及理赔金额 / confidence 低时调用. 创建 acc_asks 工单",
            "parameters": {
                "type": "object",
                "properties": {
                    "shipment_ref": {"type": "string", "description": "相关订单号/快件号(没有填 unknown)"},
                    "content": {"type": "string", "description": "问题描述(给客服看)"},
                },
                "required": ["shipment_ref", "content"],
            },
        },
    },
]

TOOL_DISPATCHER = {
    "query_tracking": tool_query_tracking,
    "query_bill": tool_query_bill,
    "search_kb": tool_search_kb,
    "create_ask": tool_create_ask,
}


# ════════ DeepSeek 调用 ════════
SYSTEM_PROMPT = """你是 xqt-saas 跨境物流的智能客服 Agent. 用户是国内出口商, 主要问题集中在:
1. 查件 - 我的包裹到哪了, 为什么显示 Exception
2. 账单 - 这个月欠多少, 账单怎么不对
3. 改地址 / 申请赔偿 / 电池规定 - 走 FAQ 政策
4. 投诉 / 复杂业务 - 转人工

**铁律 (违反就被开除)**:
- 涉及具体订单状态/账单数字/轨迹位置, **必须**调 query_tracking/query_bill 真实查表
- **禁止**凭记忆编造包裹位置, 不知道就说不知道
- 通用问题(电池规定/改地址流程) 用 search_kb 找 FAQ, 不要瞎答
- 用户要求人工 / 涉及赔偿金额 / 你不确定 → create_ask 转人工

**回复风格**: 中文, 简短, 数字精确, 引用 source(知识库 id 或 tool 结果)

每次回复末尾以 JSON 评估自己: {"confidence": 0.0-1.0, "escalate": false/true}
confidence < 0.6 必须 escalate=true."""


class ChatRequest(BaseModel):
    session_id: str
    messages: list[dict] = Field(..., description="完整会话历史 [{role, content}, ...]")
    customer_code: Optional[str] = None
    customer_name: Optional[str] = None


class ChatResponse(BaseModel):
    content: str
    tool_calls: list = []
    confidence: float = 1.0
    escalate: bool = False
    tokens_used: int = 0
    latency_ms: int = 0


async def call_deepseek(messages: list, tools: list = None) -> dict:
    """调 DeepSeek chat completion"""
    if not DEEPSEEK_API_KEY:
        raise HTTPException(status_code=500, detail="DEEPSEEK_API_KEY 未配置")
    payload = {
        "model": DEEPSEEK_MODEL,
        "messages": messages,
        "temperature": 0.3,  # 低温度防瞎编
    }
    if tools:
        payload["tools"] = tools
        payload["tool_choice"] = "auto"
    async with httpx.AsyncClient(timeout=60) as client:
        r = await client.post(
            f"{DEEPSEEK_BASE_URL}/chat/completions",
            headers={"Authorization": f"Bearer {DEEPSEEK_API_KEY}"},
            json=payload,
        )
        r.raise_for_status()
        return r.json()


@app.get("/health")
def health():
    return {"ok": True, "service": "cs-agent", "version": "0.1.0", "model": DEEPSEEK_MODEL}


@app.post("/chat", response_model=ChatResponse, dependencies=[Depends(check_token)])
async def chat(req: ChatRequest):
    """一次对话: 接受 user 消息 + 历史, 返回 assistant 回复.
    自动执行 tool-call 循环 (最多 3 轮防死循环)"""
    t0 = time.time()
    msgs = [{"role": "system", "content": SYSTEM_PROMPT}]
    # 注入客户上下文
    if req.customer_code:
        msgs.append({"role": "system", "content": f"当前客户: code={req.customer_code} name={req.customer_name or ''}"})
    msgs.extend(req.messages)
    total_tokens = 0
    tool_call_log = []
    for round_idx in range(3):
        resp = await call_deepseek(msgs, TOOLS)
        total_tokens += resp.get("usage", {}).get("total_tokens", 0)
        choice = resp["choices"][0]
        msg = choice["message"]
        # 模型决定调工具
        tool_calls = msg.get("tool_calls", []) or []
        if not tool_calls:
            content = msg.get("content", "")
            # 解析末尾 JSON 自评
            confidence, escalate = 1.0, False
            try:
                last_brace = content.rfind("{")
                if last_brace > 0:
                    tail = content[last_brace:]
                    eval_obj = json.loads(tail)
                    confidence = float(eval_obj.get("confidence", 1.0))
                    escalate = bool(eval_obj.get("escalate", False))
                    content = content[:last_brace].strip()
            except Exception:
                pass
            # 低 confidence 强制转人工
            if confidence < CONFIDENCE_THRESHOLD and not escalate:
                escalate = True
            return ChatResponse(
                content=content, tool_calls=tool_call_log,
                confidence=confidence, escalate=escalate,
                tokens_used=total_tokens, latency_ms=int((time.time() - t0) * 1000)
            )
        # 执行工具
        msgs.append(msg)  # assistant 的 tool_calls 消息
        for tc in tool_calls:
            fn_name = tc["function"]["name"]
            fn = TOOL_DISPATCHER.get(fn_name)
            if not fn:
                result = {"error": f"unknown tool {fn_name}"}
            else:
                try:
                    args = json.loads(tc["function"]["arguments"])
                    log.info(f"tool {fn_name} args={args}")
                    t_tool = time.time()
                    result = fn(**args)
                    log.info(f"tool {fn_name} result_summary={str(result)[:200]}")
                    tool_call_log.append({
                        "tool": fn_name, "args": args, "result": result,
                        "latency_ms": int((time.time() - t_tool) * 1000),
                    })
                except Exception as e:
                    log.exception("tool error")
                    result = {"error": str(e)}
                    tool_call_log.append({"tool": fn_name, "error": str(e)})
            msgs.append({
                "role": "tool",
                "tool_call_id": tc["id"],
                "content": json.dumps(result, ensure_ascii=False),
            })
    # 3 轮兜底
    return ChatResponse(
        content="抱歉, 处理超时了. 已为您转人工客服.",
        tool_calls=tool_call_log, confidence=0.0, escalate=True,
        tokens_used=total_tokens, latency_ms=int((time.time() - t0) * 1000)
    )
