-- AccReceivedsController.bankName 真实字段（阶段 2-H）。
--
-- 对照 docs/acc-gap-report-for-claude-2026-05-28.md §4 / §5.9 + 旧 ACC `Received.php`：
--   - 旧 Received.Bank → payments.financial_account_id（指向收款的银行/资金账户）
--   - 旧 Received.Remark → payments.remark
--
-- bankName 在 list 时通过 join financial_accounts 派生（account_name / bank_name）。

alter table payments
  add column if not exists financial_account_id uuid references financial_accounts(id),
  add column if not exists remark text;
