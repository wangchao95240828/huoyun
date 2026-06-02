-- ACC vs 现有系统差异第三批（对照工作簿1777.xlsx）
--
-- 1) orders 加 channel_account_id (制单账号选择下拉)
--                  freight_amount + insurance_amount (发票信息 运费/保险)
--                  importer_template_id (进口商预设模板)
-- 2) acc_borrowings 加 due_date + repayment_status + repaid_amount (资金借贷)
-- 3) customers 加 invoice_title + invoice_tax_no + invoice_address (客户发票信息)
-- 4) partners (物流商) 加 invoice_title + invoice_tax_no + bank_info_text (功能差异)

-- 制单订单
ALTER TABLE orders
  ADD COLUMN IF NOT EXISTS channel_account_id    uuid,
  ADD COLUMN IF NOT EXISTS freight_amount        numeric(14,2),
  ADD COLUMN IF NOT EXISTS insurance_amount      numeric(14,2),
  ADD COLUMN IF NOT EXISTS importer_template_id  uuid;
CREATE INDEX IF NOT EXISTS idx_orders_channel_account
  ON orders(channel_account_id) WHERE channel_account_id IS NOT NULL;
COMMENT ON COLUMN orders.channel_account_id    IS 'ACC: 制单账号下拉绑定';
COMMENT ON COLUMN orders.freight_amount        IS '运费 (ACC 发票信息)';
COMMENT ON COLUMN orders.insurance_amount      IS '保险费 (ACC 发票信息)';
COMMENT ON COLUMN orders.importer_template_id  IS 'ACC 进口商预设模板 id';

-- 进口商预设模板表（独立维护，给 orders.importer_template_id 用）
CREATE TABLE IF NOT EXISTS acc_importer_templates (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid NOT NULL,
    name         text NOT NULL,
    country      char(2),
    tax_id       text,
    address      text,
    contact_name text,
    contact_phone text,
    customer_id  uuid,             -- 可绑定特定客户的模板
    metadata     jsonb DEFAULT '{}',
    audit_status text DEFAULT 'PENDING',
    audited_at   timestamptz,
    audit_name   text,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_acc_importer_templates_customer
  ON acc_importer_templates(tenant_id, customer_id);
COMMENT ON TABLE acc_importer_templates IS 'ACC 进口商预设模板（制单时下拉选择）';

-- 资金借贷
ALTER TABLE acc_borrowings
  ADD COLUMN IF NOT EXISTS due_date          date,
  ADD COLUMN IF NOT EXISTS repayment_status  text NOT NULL DEFAULT 'PENDING',
  ADD COLUMN IF NOT EXISTS repaid_amount     numeric(14,2) NOT NULL DEFAULT 0;
ALTER TABLE acc_borrowings
  DROP CONSTRAINT IF EXISTS acc_borrowings_repayment_status_check;
ALTER TABLE acc_borrowings
  ADD CONSTRAINT acc_borrowings_repayment_status_check CHECK
    (repayment_status = ANY (ARRAY['PENDING','PARTIAL','REPAID','OVERDUE']));
COMMENT ON COLUMN acc_borrowings.due_date         IS '到期日';
COMMENT ON COLUMN acc_borrowings.repayment_status IS '还款状态: PENDING/PARTIAL/REPAID/OVERDUE';
COMMENT ON COLUMN acc_borrowings.repaid_amount    IS '已还金额';

-- 客户发票信息
ALTER TABLE customers
  ADD COLUMN IF NOT EXISTS invoice_title    text,
  ADD COLUMN IF NOT EXISTS invoice_tax_no   text,
  ADD COLUMN IF NOT EXISTS invoice_address  text;
COMMENT ON COLUMN customers.invoice_title   IS '开票抬头';
COMMENT ON COLUMN customers.invoice_tax_no  IS '税号';
COMMENT ON COLUMN customers.invoice_address IS '开票地址';

-- 物流商发票信息
ALTER TABLE partners
  ADD COLUMN IF NOT EXISTS invoice_title    text,
  ADD COLUMN IF NOT EXISTS invoice_tax_no   text,
  ADD COLUMN IF NOT EXISTS bank_info_text   text;
COMMENT ON COLUMN partners.invoice_title  IS '开票抬头';
COMMENT ON COLUMN partners.invoice_tax_no IS '税号';
COMMENT ON COLUMN partners.bank_info_text IS '银行信息（free text，便于打印）';
