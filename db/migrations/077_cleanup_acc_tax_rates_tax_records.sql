-- 077: 把 AccTaxController.ensureTable() 移到迁移 + 建 tax_records 表

BEGIN;

CREATE TABLE IF NOT EXISTS acc_tax_rates (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid NOT NULL DEFAULT '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid,
    code          text NOT NULL,
    name          text NOT NULL,
    rate          numeric(7,4) NOT NULL,
    tax_type      text NOT NULL,
    country_code  text,
    effective_from date NOT NULL DEFAULT current_date,
    effective_to   date,
    is_active     boolean NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT acc_tax_rates_tenant_code_unique UNIQUE (tenant_id, code),
    CONSTRAINT acc_tax_rates_tax_type_check
        CHECK (tax_type IN ('VAT','SALES_TAX','GST','CUSTOMS_DUTY','INCOME_TAX','WITHHOLDING')),
    CONSTRAINT acc_tax_rates_rate_check CHECK (rate >= 0 AND rate <= 1)
);

CREATE INDEX IF NOT EXISTS idx_acc_tax_rates_active
    ON acc_tax_rates (tenant_id, is_active, country_code);

-- 种子常见税率
INSERT INTO acc_tax_rates (code, name, rate, tax_type, country_code) VALUES
    ('CN_VAT_13',  '中国增值税 13%', 0.13,  'VAT',         'CN'),
    ('CN_VAT_9',   '中国增值税 9%',  0.09,  'VAT',         'CN'),
    ('CN_VAT_6',   '中国增值税 6%',  0.06,  'VAT',         'CN'),
    ('US_NY_SALES','美国NY销售税',   0.0875,'SALES_TAX',   'US'),
    ('US_CA_SALES','美国CA销售税',   0.0975,'SALES_TAX',   'US'),
    ('AU_GST',     '澳洲 GST 10%',  0.10,  'GST',         'AU'),
    ('UK_VAT',     '英国 VAT 20%',  0.20,  'VAT',         'GB'),
    ('DE_VAT',     '德国 VAT 19%',  0.19,  'VAT',         'DE')
ON CONFLICT (tenant_id, code) DO NOTHING;

-- tax_records: 业务侧落账时记录每笔税额
CREATE TABLE IF NOT EXISTS acc_tax_records (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid NOT NULL DEFAULT '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid,
    source_type   text NOT NULL,                  -- customer_invoices/charges/...
    source_id     uuid NOT NULL,
    tax_code      text NOT NULL,
    taxable_amount numeric(14,2) NOT NULL,
    tax_amount    numeric(14,2) NOT NULL,
    total_amount  numeric(14,2) NOT NULL,
    currency      char(3) NOT NULL DEFAULT 'CNY',
    the_date      date NOT NULL DEFAULT current_date,
    direction     text NOT NULL,                  -- AR / AP
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT acc_tax_records_direction_check CHECK (direction IN ('AR','AP'))
);

CREATE INDEX IF NOT EXISTS idx_acc_tax_records_period
    ON acc_tax_records (tenant_id, the_date);
CREATE INDEX IF NOT EXISTS idx_acc_tax_records_source
    ON acc_tax_records (source_type, source_id);

COMMIT;
