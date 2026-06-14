-- 074: 期间锁定 + 凭证科目（GL）框架 — 对齐 ACC

BEGIN;

-- ═══ 1. 期间锁 (acc_period_locks) ═══
-- 月结/年结后锁定该期间，不允许追溯修改交易
CREATE TABLE IF NOT EXISTS acc_period_locks (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL DEFAULT '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid,
    period      text NOT NULL,                    -- 'YYYY-MM' 或 'YYYY' 年结
    period_type text NOT NULL DEFAULT 'MONTH',    -- MONTH / QUARTER / YEAR
    locked_at   timestamptz NOT NULL DEFAULT now(),
    locked_by   uuid,
    reason      text,
    CONSTRAINT acc_period_locks_period_type_check CHECK (period_type IN ('MONTH','QUARTER','YEAR')),
    CONSTRAINT acc_period_locks_tenant_period_unique UNIQUE (tenant_id, period)
);

CREATE INDEX IF NOT EXISTS idx_acc_period_locks_tenant ON acc_period_locks (tenant_id, period);

-- 函数：检查给定日期是否在已锁定期间内
CREATE OR REPLACE FUNCTION acc_is_period_locked(check_date date) RETURNS boolean AS $$
DECLARE
    month_str text;
    year_str text;
BEGIN
    IF check_date IS NULL THEN RETURN false; END IF;
    month_str := to_char(check_date, 'YYYY-MM');
    year_str  := to_char(check_date, 'YYYY');
    RETURN EXISTS (
        SELECT 1 FROM acc_period_locks
         WHERE period = month_str OR period = year_str
    );
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- ═══ 2. 凭证科目 (acc_gl_subjects) ═══
CREATE TABLE IF NOT EXISTS acc_gl_subjects (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL DEFAULT '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid,
    code        text NOT NULL,                    -- 科目码 e.g. '1001'
    name        text NOT NULL,                    -- 科目名 e.g. '库存现金'
    parent_id   uuid REFERENCES acc_gl_subjects(id),
    level       integer NOT NULL DEFAULT 1,       -- 1=一级 2=二级 3=明细
    category    text NOT NULL,                    -- ASSET/LIABILITY/EQUITY/REVENUE/EXPENSE
    is_balance_sheet boolean NOT NULL DEFAULT true,
    is_active   boolean NOT NULL DEFAULT true,
    remark      text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT acc_gl_subjects_tenant_code_unique UNIQUE (tenant_id, code),
    CONSTRAINT acc_gl_subjects_category_check CHECK (category IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE'))
);

-- 种子标准会计科目
INSERT INTO acc_gl_subjects (code, name, category, level, is_balance_sheet) VALUES
    ('1001', '库存现金',     'ASSET',     1, true),
    ('1002', '银行存款',     'ASSET',     1, true),
    ('1121', '应收账款',     'ASSET',     1, true),
    ('1122', '其他应收款',   'ASSET',     1, true),
    ('1601', '固定资产',     'ASSET',     1, true),
    ('1602', '累计折旧',     'ASSET',     1, true),
    ('2001', '短期借款',     'LIABILITY', 1, true),
    ('2202', '应付账款',     'LIABILITY', 1, true),
    ('2211', '应付职工薪酬', 'LIABILITY', 1, true),
    ('2221', '应交税费',     'LIABILITY', 1, true),
    ('3001', '实收资本',     'EQUITY',    1, true),
    ('3002', '资本公积',     'EQUITY',    1, true),
    ('3101', '盈余公积',     'EQUITY',    1, true),
    ('3103', '本年利润',     'EQUITY',    1, true),
    ('5001', '主营业务收入', 'REVENUE',   1, false),
    ('5051', '其他业务收入', 'REVENUE',   1, false),
    ('5301', '营业外收入',   'REVENUE',   1, false),
    ('5401', '主营业务成本', 'EXPENSE',   1, false),
    ('5402', '其他业务支出', 'EXPENSE',   1, false),
    ('5601', '销售费用',     'EXPENSE',   1, false),
    ('5602', '管理费用',     'EXPENSE',   1, false),
    ('5603', '财务费用',     'EXPENSE',   1, false),
    ('5701', '所得税费用',   'EXPENSE',   1, false)
ON CONFLICT (tenant_id, code) DO NOTHING;

-- ═══ 3. 凭证 (acc_gl_vouchers) ═══
CREATE TABLE IF NOT EXISTS acc_gl_vouchers (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL DEFAULT '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid,
    voucher_no  text NOT NULL,
    the_date    date NOT NULL,
    description text,
    source_type text,                              -- 来源单据表 charges/payments etc
    source_id   uuid,                              -- 来源单据 id
    status      text NOT NULL DEFAULT 'DRAFT',
    audit_status text NOT NULL DEFAULT 'PENDING',
    audited_at  timestamptz,
    audit_name  text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT acc_gl_vouchers_tenant_no_unique UNIQUE (tenant_id, voucher_no),
    CONSTRAINT acc_gl_vouchers_status_check CHECK (status IN ('DRAFT','POSTED','VOID'))
);

CREATE INDEX IF NOT EXISTS idx_acc_gl_vouchers_date ON acc_gl_vouchers (tenant_id, the_date);

-- ═══ 4. 凭证分录 (acc_gl_voucher_lines) ═══
CREATE TABLE IF NOT EXISTS acc_gl_voucher_lines (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    voucher_id  uuid NOT NULL REFERENCES acc_gl_vouchers(id) ON DELETE CASCADE,
    subject_id  uuid NOT NULL REFERENCES acc_gl_subjects(id),
    direction   text NOT NULL,                    -- DEBIT / CREDIT
    amount      numeric(14,2) NOT NULL,
    currency    char(3) NOT NULL DEFAULT 'CNY',
    remark      text,
    line_no     integer NOT NULL DEFAULT 1,
    CONSTRAINT acc_gl_voucher_lines_direction_check CHECK (direction IN ('DEBIT','CREDIT')),
    CONSTRAINT acc_gl_voucher_lines_amount_positive CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_acc_gl_voucher_lines_voucher ON acc_gl_voucher_lines (voucher_id);
CREATE INDEX IF NOT EXISTS idx_acc_gl_voucher_lines_subject ON acc_gl_voucher_lines (subject_id);

-- 视图：科目余额表
CREATE OR REPLACE VIEW v_gl_subject_balance AS
    SELECT s.id::text AS subject_id, s.code, s.name, s.category,
           coalesce(sum(CASE WHEN l.direction='DEBIT' THEN l.amount ELSE 0 END), 0) AS debit_total,
           coalesce(sum(CASE WHEN l.direction='CREDIT' THEN l.amount ELSE 0 END), 0) AS credit_total,
           coalesce(sum(CASE WHEN l.direction='DEBIT' THEN l.amount ELSE -l.amount END), 0) AS net_balance
      FROM acc_gl_subjects s
      LEFT JOIN acc_gl_voucher_lines l ON l.subject_id = s.id
      LEFT JOIN acc_gl_vouchers v ON v.id = l.voucher_id AND v.status = 'POSTED'
     GROUP BY s.id, s.code, s.name, s.category;

COMMIT;
