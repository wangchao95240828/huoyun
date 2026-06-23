-- ═════════════════════════════════════════════════════════════════════════
-- 092_commission_engine
--
-- 修 P0-B3: 提成引擎 MVP. ACC Commission.php 1111 行规则:
--   method=1 按销售额千分比 → amount = sales * percent / 1000
--   method=2 按利润额千分比 → amount = profit * percent / 1000
--   method=3 按销售件数        → amount = piece_count * percent
-- 客户级覆盖 Customer_Commission (start/end_date 时间窗优先于员工默认规则).
-- ═════════════════════════════════════════════════════════════════════════

-- 1. commission_rules 加 method (语义化, 与 rule_type 共存) + start/end_date
ALTER TABLE acc_commission_rules
  ADD COLUMN IF NOT EXISTS method     text CHECK (method IN ('SALES_PER_MILLE','PROFIT_PER_MILLE','PER_COUNT')),
  ADD COLUMN IF NOT EXISTS employee_id uuid REFERENCES acc_employees(id),
  ADD COLUMN IF NOT EXISTS customer_id uuid REFERENCES customers(id),
  ADD COLUMN IF NOT EXISTS start_date date,
  ADD COLUMN IF NOT EXISTS end_date   date,
  ADD COLUMN IF NOT EXISTS active     boolean NOT NULL DEFAULT true;

CREATE INDEX IF NOT EXISTS idx_commission_rules_employee
  ON acc_commission_rules (tenant_id, employee_id, active);
CREATE INDEX IF NOT EXISTS idx_commission_rules_customer
  ON acc_commission_rules (tenant_id, customer_id, active) WHERE customer_id IS NOT NULL;

-- 2. commissions 加 piece_count + applied_rule 便于审计
ALTER TABLE acc_commissions
  ADD COLUMN IF NOT EXISTS piece_count int DEFAULT 0,
  ADD COLUMN IF NOT EXISTS rule_method text,
  ADD COLUMN IF NOT EXISTS rule_percent numeric(8,4);

-- 3. 明细表 acc_commission_lines: 每票一行, 便于追溯
CREATE TABLE IF NOT EXISTS acc_commission_lines (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid NOT NULL DEFAULT current_setting('app.current_tenant_id')::uuid,
    commission_id uuid NOT NULL REFERENCES acc_commissions(id) ON DELETE CASCADE,
    shipment_id  uuid REFERENCES shipments(id),
    order_no     text,
    customer_id  uuid,
    sales_amount numeric(14,2) DEFAULT 0,
    profit_amount numeric(14,2) DEFAULT 0,
    piece_count  int DEFAULT 0,
    contribution numeric(14,2) DEFAULT 0,
    rule_id      uuid REFERENCES acc_commission_rules(id),
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_commission_lines_commission
  ON acc_commission_lines (commission_id);

COMMENT ON COLUMN acc_commission_rules.method IS 'SALES_PER_MILLE=按销售额千分比 / PROFIT_PER_MILLE=按利润额千分比 / PER_COUNT=按销售件数';
COMMENT ON COLUMN acc_commission_rules.customer_id IS '客户级覆盖, NULL=员工默认规则';
COMMENT ON TABLE acc_commission_lines IS '提成明细 - 每票贡献追溯';
