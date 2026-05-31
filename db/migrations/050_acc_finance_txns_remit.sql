-- ACC 财务中心 → 退款记录页 "批量汇款" 功能
-- acc_finance_txns 表加 3 个字段记录汇款经手信息

ALTER TABLE acc_finance_txns
  ADD COLUMN IF NOT EXISTS financial_account_id uuid,
  ADD COLUMN IF NOT EXISTS remitted_at          timestamptz,
  ADD COLUMN IF NOT EXISTS remit_name           text;

CREATE INDEX IF NOT EXISTS idx_acc_finance_txns_remitted_at
  ON acc_finance_txns(remitted_at);
CREATE INDEX IF NOT EXISTS idx_acc_finance_txns_financial_account
  ON acc_finance_txns(financial_account_id);

COMMENT ON COLUMN acc_finance_txns.financial_account_id IS '批量汇款时绑定的资金账户';
COMMENT ON COLUMN acc_finance_txns.remitted_at         IS '批量汇款时间';
COMMENT ON COLUMN acc_finance_txns.remit_name          IS '汇款经手人';
