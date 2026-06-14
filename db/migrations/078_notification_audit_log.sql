-- 078: 通知发送日志 + 全写操作审计表 + 健康检查支持

BEGIN;

-- ═══ 通知发送日志 ═══
CREATE TABLE IF NOT EXISTS acc_notification_log (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid NOT NULL DEFAULT '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid,
    channel      text NOT NULL,                  -- SMS / EMAIL / WEBHOOK
    template     text,                            -- 模板代码
    recipient    text NOT NULL,
    subject      text,
    body         text,
    provider     text,                            -- aliyun / smtp / noop
    status       text NOT NULL DEFAULT 'PENDING', -- PENDING / SENT / FAILED
    error_msg    text,
    retry_count  integer NOT NULL DEFAULT 0,
    sent_at      timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT acc_notification_log_status_check
        CHECK (status IN ('PENDING','SENT','FAILED','SKIPPED')),
    CONSTRAINT acc_notification_log_channel_check
        CHECK (channel IN ('SMS','EMAIL','WEBHOOK','WECHAT'))
);

CREATE INDEX IF NOT EXISTS idx_acc_notification_log_status
    ON acc_notification_log (tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_acc_notification_log_recipient
    ON acc_notification_log (recipient, created_at DESC);

-- ═══ 写操作全局审计（补充 audit_events）═══
-- 用于 compliance：每个 INSERT/UPDATE/DELETE 都留痕
CREATE TABLE IF NOT EXISTS acc_write_audit (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid NOT NULL DEFAULT '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid,
    user_id      uuid,
    user_name    text,
    method       text NOT NULL,                   -- POST / PUT / DELETE
    path         text NOT NULL,
    request_body jsonb,
    response_status integer,
    duration_ms  integer,
    ip           inet,
    user_agent   text,
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_acc_write_audit_user
    ON acc_write_audit (tenant_id, user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_acc_write_audit_path
    ON acc_write_audit (path, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_acc_write_audit_recent
    ON acc_write_audit (tenant_id, created_at DESC);

-- 90 天后自动归档
-- 生产可加 pg_partman 月分区

COMMIT;
