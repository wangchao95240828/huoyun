-- 客户账单收款二审门槛（参考 067 acc_transfers / acc_finance_txns 同款 trigger）
-- 大额收款须由主管二审 verify_status='VERIFIED' 才能 mark-paid

ALTER TABLE customer_invoices
  ADD COLUMN IF NOT EXISTS verify_status text NOT NULL DEFAULT 'PENDING'
    CHECK (verify_status IN ('PENDING','VERIFIED','REJECTED')),
  ADD COLUMN IF NOT EXISTS verified_at timestamptz,
  ADD COLUMN IF NOT EXISTS verify_name text;

COMMENT ON COLUMN customer_invoices.verify_status IS '二审状态（total_amount > 5w 才强制二审）';

-- 复用 067 的 enforce_verify_status 函数：
-- BEFORE UPDATE 时如果 status 切到 PAID 且 total_amount 超阈值 → 检查 verify_status

CREATE OR REPLACE FUNCTION enforce_invoice_verify() RETURNS trigger AS $$
BEGIN
  -- 只在 status 切到 PAID 那一刻拦截
  IF NEW.status = 'PAID' AND coalesce(OLD.status, '') <> 'PAID' THEN
    IF NEW.total_amount > 50000
       AND coalesce(NEW.verify_status, 'PENDING') <> 'VERIFIED' THEN
      RAISE EXCEPTION '账单金额 % 超过 5w 阈值，须先二审 (verify_status=VERIFIED)',
        NEW.total_amount USING ERRCODE = 'check_violation';
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_enforce_invoice_verify ON customer_invoices;
CREATE TRIGGER trg_enforce_invoice_verify
  BEFORE UPDATE ON customer_invoices
  FOR EACH ROW EXECUTE FUNCTION enforce_invoice_verify();
