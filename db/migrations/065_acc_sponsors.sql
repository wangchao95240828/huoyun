-- ACC CSponsor.php / SSponsor.php → 客户/供应商赞助（押金/预存款）
-- 复用 acc_finance_txns 表，加 SPONSOR txn_type；balance_ledger_biz_type 加 SPONSOR enum。
-- 二审通过时由 FinanceTxnAuditSideEffect 自动写 balance_ledger。

-- 1) acc_finance_txns 加 SPONSOR
ALTER TABLE acc_finance_txns DROP CONSTRAINT IF EXISTS acc_finance_txns_txn_type_check;
ALTER TABLE acc_finance_txns ADD CONSTRAINT acc_finance_txns_txn_type_check
  CHECK (txn_type = ANY (ARRAY['ADJUST','REFUND','REBATE','SPONSOR']));

-- 2) balance_ledger_biz_type enum 加 SPONSOR + SPONSOR_RELEASE
-- (Postgres ALTER TYPE ADD VALUE 必须在 transaction 外执行)
ALTER TYPE balance_ledger_biz_type ADD VALUE IF NOT EXISTS 'SPONSOR';
ALTER TYPE balance_ledger_biz_type ADD VALUE IF NOT EXISTS 'SPONSOR_RELEASE';
