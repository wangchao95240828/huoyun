-- ═════════════════════════════════════════════════════════════════════════
-- 086_customer_logins
--
-- 客户登陆号管理 (ACC CustomerLogin.php 对齐).
-- 客户用 username + password 登录 customer-portal.html, 自己查快件 / 看账单.
-- 跟 admin users 不同 — 这表只给客户用, scope 仅限 ta 自己的资源.
--
-- 创建/重置密码时生成明文一次性返回, 库里只存 BCrypt hash.
-- ═════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS customer_logins (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL DEFAULT current_setting('app.current_tenant_id')::uuid,
    customer_id     uuid NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    username        text NOT NULL,
    password_hash   text NOT NULL,
    status          text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','LOCKED','DISABLED')),
    last_login_at   timestamptz,
    last_login_ip   text,
    remark          text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    audit_status    text NOT NULL DEFAULT 'PENDING' CHECK (audit_status IN ('PENDING','UNAUDITED','AUDITED')),
    audited_at      timestamptz,
    audit_name      text,
    UNIQUE (tenant_id, username)
);

CREATE INDEX IF NOT EXISTS idx_customer_logins_customer
    ON customer_logins (tenant_id, customer_id);

CREATE INDEX IF NOT EXISTS idx_customer_logins_status
    ON customer_logins (tenant_id, status) WHERE status = 'ACTIVE';

COMMENT ON TABLE customer_logins IS 'ACC CustomerLogin.php — 客户自助门户登录账号';
COMMENT ON COLUMN customer_logins.password_hash IS 'BCrypt hash, 明文一次性返回不入库';
