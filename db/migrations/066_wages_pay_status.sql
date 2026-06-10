-- ACC Wage.php 发工资业务动作配套字段。
-- acc_wages 之前只有 audit_status（审核），缺发放状态。补 pay_status + paid_at + paid_name。
-- borrowing 表已有 repayment_status + repaid_amount（041 加过），不需要再补。

ALTER TABLE acc_wages
  ADD COLUMN IF NOT EXISTS pay_status text NOT NULL DEFAULT 'PENDING'
    CHECK (pay_status IN ('PENDING','PAID','VOIDED')),
  ADD COLUMN IF NOT EXISTS paid_at timestamptz,
  ADD COLUMN IF NOT EXISTS paid_name text;

COMMENT ON COLUMN acc_wages.pay_status IS '发放状态：PENDING 未发 / PAID 已发 / VOIDED 已作废';
COMMENT ON COLUMN acc_wages.paid_at IS '发放时间，POST /{id}/pay 时填';
COMMENT ON COLUMN acc_wages.paid_name IS '发放经办人';
