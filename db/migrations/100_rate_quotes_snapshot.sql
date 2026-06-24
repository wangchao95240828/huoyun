-- ═════════════════════════════════════════════════════════════════════════
-- 100_rate_quotes_snapshot
--
-- W3 deep-research 落地 — EasyPost 模式:
--   "Rate 会过期, RateEngine 不是实时引擎, 是快照引擎"
--   "提供 rerate endpoint 而不是把缓存当实时"
--
-- 设计:
--   rate_quotes: 每次报价存快照 (含 charges[] / fuel_pct / rate_set_id)
--   TTL: min(下次 fuel 调整时间 = 周一, 24h)
--   rerate: POST /rate-quotes/{id}/rerate 刷新, 用同 request 重算
--   月度对账 cron: 拉本月 quote vs charges 真实金额, delta > 1% 告警
-- ═════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS rate_quotes (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL DEFAULT current_setting('app.current_tenant_id')::uuid,
    -- 关联订单 (可空, 独立报价不绑订单)
    order_id        uuid,
    customer_id     uuid,
    -- request 入参快照 (jsonb)
    request_json    jsonb NOT NULL,
    -- 响应快照 (含 charges[] / matched evidence)
    response_json   jsonb NOT NULL,
    -- 关键路由字段提取 (查询/对账方便)
    channel_code    text NOT NULL,
    currency        char(3) NOT NULL,
    total_amount    numeric(14,2) NOT NULL,
    -- 时间窗
    quoted_at       timestamptz NOT NULL DEFAULT now(),
    expires_at      timestamptz NOT NULL,         -- min(下次 fuel 调整, 24h)
    -- 使用状态
    -- ACTIVE   未过期未用
    -- USED     已被订单引用 (orders.metadata 关联)
    -- EXPIRED  过期未用
    -- RE_RATED 已被 rerate 替换 (rerate_to_id 指向新 quote)
    status          text NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','USED','EXPIRED','RE_RATED')),
    rerate_from_id  uuid REFERENCES rate_quotes(id),   -- 这条 quote 是从哪个 quote rerate 来
    rerate_to_id    uuid,                              -- 这条被 rerate 成哪个新 quote
    -- 燃油快照 (审计用)
    fuel_pct        numeric(8,6),
    rate_set_id     uuid,
    -- 对账状态 (W3 月度对账 cron 用)
    -- PENDING        未对账
    -- MATCHED        跟真实 charges 一致
    -- DRIFT_MINOR    delta <= 1%
    -- DRIFT_MAJOR    delta > 1% (告警)
    -- NOT_USED       quote 没被使用, 无需对账
    reconcile_status text DEFAULT 'PENDING'
                    CHECK (reconcile_status IN ('PENDING','MATCHED','DRIFT_MINOR','DRIFT_MAJOR','NOT_USED')),
    reconcile_at    timestamptz,
    reconcile_actual numeric(14,2),                   -- 真实账单金额
    reconcile_delta  numeric(14,2),                   -- delta = quote - actual
    reconcile_delta_pct numeric(8,4),
    reconcile_note  text,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rate_quotes_customer ON rate_quotes (tenant_id, customer_id, quoted_at DESC);
CREATE INDEX IF NOT EXISTS idx_rate_quotes_order ON rate_quotes (order_id) WHERE order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_rate_quotes_expires ON rate_quotes (status, expires_at) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_rate_quotes_reconcile ON rate_quotes (reconcile_status, quoted_at) WHERE reconcile_status IN ('PENDING','DRIFT_MAJOR');

COMMENT ON TABLE rate_quotes IS 'W3: 报价快照 (EasyPost 模式), 含 rerate + 月度对账';
COMMENT ON COLUMN rate_quotes.expires_at IS 'TTL: min(下次 fuel 调整 (周一 0:00), 24h)';
COMMENT ON COLUMN rate_quotes.reconcile_delta_pct IS '(quote - actual) / actual × 100, > 1 触发告警';
