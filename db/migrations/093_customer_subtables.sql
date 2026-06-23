-- ═════════════════════════════════════════════════════════════════════════
-- 093_customer_subtables
--
-- 修 P0-D2 + P0-D3:
--   D2 (Customer 子表): ACC Customer_Site (多网址) / Customer_Contacts (多联系人)
--   D3 (多业务员桥表): ACC Customer_Commission (一客户多业务员 + 时间窗 + isDefault)
-- xqt-saas 之前 customers.salesman_user_id 单值, 做不到一个客户多业务员.
-- ═════════════════════════════════════════════════════════════════════════

-- D2.1 客户多网址 (e.g. 亚马逊店铺/官网/独立站)
CREATE TABLE IF NOT EXISTS customer_sites (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL DEFAULT current_setting('app.current_tenant_id')::uuid,
    customer_id uuid NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    site_type   text NOT NULL CHECK (site_type IN ('AMAZON','SHOPIFY','EBAY','OFFICIAL','OTHER')),
    site_name   text,
    url         text NOT NULL,
    region      text,
    remark      text,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_customer_sites_customer
    ON customer_sites (tenant_id, customer_id);

-- D2.2 客户多联系人 (主联系人/财务/技术/对接人...)
CREATE TABLE IF NOT EXISTS customer_contacts (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL DEFAULT current_setting('app.current_tenant_id')::uuid,
    customer_id uuid NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    role        text NOT NULL CHECK (role IN ('PRIMARY','FINANCE','TECH','OPERATION','OTHER')),
    name        text NOT NULL,
    mobile      text,
    phone       text,
    email       text,
    wechat      text,
    qq          text,
    whatsapp    text,
    skype       text,
    is_default  boolean NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_customer_contacts_customer
    ON customer_contacts (tenant_id, customer_id);

-- D3 多业务员桥表 (一客户对应多业务员, 时间窗 + isDefault)
-- 对齐 ACC Customer_Commission (CustomerId/UserId/StartDate/EndDate/isDefault).
CREATE TABLE IF NOT EXISTS customer_salesmen (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL DEFAULT current_setting('app.current_tenant_id')::uuid,
    customer_id uuid NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    -- 业务员可以是 users 表 (内部) 或 acc_employees (HR 维度)
    user_id     uuid REFERENCES users(id),
    employee_id uuid REFERENCES acc_employees(id),
    role        text NOT NULL DEFAULT 'SALESMAN'
        CHECK (role IN ('SALESMAN','SERVICER','KAM','OTHER')),
    is_default  boolean NOT NULL DEFAULT false,
    start_date  date,
    end_date    date,
    commission_share numeric(5,2) DEFAULT 100.0
        CHECK (commission_share >= 0 AND commission_share <= 100),
    remark      text,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_customer_salesmen_customer
    ON customer_salesmen (tenant_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_customer_salesmen_employee
    ON customer_salesmen (tenant_id, employee_id) WHERE employee_id IS NOT NULL;
-- 同一客户同一员工同一 role 同一时间窗只能 1 条 (避免重复绑定)
CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_salesmen_employee
    ON customer_salesmen (tenant_id, customer_id, employee_id, role)
    WHERE employee_id IS NOT NULL;

COMMENT ON TABLE customer_sites IS 'D2: 客户多网址 (店铺/官网/独立站)';
COMMENT ON TABLE customer_contacts IS 'D2: 客户多联系人 (主/财务/技术/对接)';
COMMENT ON TABLE customer_salesmen IS 'D3: 客户↔业务员多对多桥表, 含时间窗 + isDefault + 提成份额';
COMMENT ON COLUMN customer_salesmen.commission_share IS '多业务员分润比 0-100, 同客户 sum 不强校验 (留运维灵活)';
