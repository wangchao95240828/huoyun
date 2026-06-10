-- 二审门槛 SQL trigger：审核通过前强制 verify_status='VERIFIED'（金额超阈值）
-- 064 加了 verify_status 字段，AccAuditController 加了 verify-biz endpoint，
-- 这里加 trigger 让任何走 UPDATE 的路径都被卡，不能从 audit-biz 绕过。
--
-- 规则：
-- - acc_finance_txns: amount > 1000 → 须二审（B1）
-- - acc_transfers:    amount > 100000 → 须二审（C2，与 AccTransfersController:182-187 同阈值）

CREATE OR REPLACE FUNCTION enforce_verify_status() RETURNS trigger AS $$
DECLARE
  threshold numeric;
BEGIN
  -- 只在切到 AUDITED 那一刻拦截
  IF NEW.audit_status = 'AUDITED'
     AND coalesce(OLD.audit_status, 'PENDING') <> 'AUDITED' THEN

    threshold := CASE TG_TABLE_NAME
      WHEN 'acc_finance_txns' THEN 1000
      WHEN 'acc_transfers'    THEN 100000
      ELSE NULL
    END;

    IF threshold IS NOT NULL
       AND NEW.amount > threshold
       AND coalesce(NEW.verify_status, 'PENDING') <> 'VERIFIED' THEN
      RAISE EXCEPTION '金额 % 超过 % 阈值，须先二审 (verify_status=VERIFIED)',
        NEW.amount, threshold USING ERRCODE = 'check_violation';
    END IF;

  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_enforce_verify_finance_txns ON acc_finance_txns;
CREATE TRIGGER trg_enforce_verify_finance_txns
  BEFORE UPDATE ON acc_finance_txns
  FOR EACH ROW EXECUTE FUNCTION enforce_verify_status();

DROP TRIGGER IF EXISTS trg_enforce_verify_transfers ON acc_transfers;
CREATE TRIGGER trg_enforce_verify_transfers
  BEFORE UPDATE ON acc_transfers
  FOR EACH ROW EXECUTE FUNCTION enforce_verify_status();
