-- ACC vs 现有系统差异第二批（对照工作簿1777.xlsx）
--
-- 1) financial_accounts 加 is_default + bank_address + swift_code （账户管理 加银行信息）
-- 2) acc_transfers 加 fee + transfer_in_amount + transfer_out_amount （资金转账 加手续费）
-- 3) finance_account_transaction 加 transaction_type 已存在，加索引
-- 4) customers 加 login_no + api_key （销售中心 客户登录号 + 客户 API）

-- 账户管理
ALTER TABLE financial_accounts
  ADD COLUMN IF NOT EXISTS is_default    boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS bank_address  text,
  ADD COLUMN IF NOT EXISTS swift_code    text;
COMMENT ON COLUMN financial_accounts.is_default   IS '是否默认账户 (ACC 默认账户选择)';
COMMENT ON COLUMN financial_accounts.bank_address IS '开户行地址';
COMMENT ON COLUMN financial_accounts.swift_code   IS 'SWIFT 代码（境外汇款用）';

-- 资金转账
ALTER TABLE acc_transfers
  ADD COLUMN IF NOT EXISTS fee                  numeric(14,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS transfer_in_amount   numeric(14,2),
  ADD COLUMN IF NOT EXISTS transfer_out_amount  numeric(14,2);
COMMENT ON COLUMN acc_transfers.fee                  IS '手续费 (ACC 单号/转入转出/手续费)';
COMMENT ON COLUMN acc_transfers.transfer_in_amount   IS '到账金额';
COMMENT ON COLUMN acc_transfers.transfer_out_amount  IS '汇出金额';

-- 客户登录号 + API
ALTER TABLE customers
  ADD COLUMN IF NOT EXISTS login_no   text,
  ADD COLUMN IF NOT EXISTS api_key    text,
  ADD COLUMN IF NOT EXISTS api_secret text;
CREATE UNIQUE INDEX IF NOT EXISTS idx_customers_login_no
  ON customers(tenant_id, login_no) WHERE login_no IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_customers_api_key
  ON customers(api_key) WHERE api_key IS NOT NULL;
COMMENT ON COLUMN customers.login_no   IS '客户登录号（ACC 销售中心 登陆号）';
COMMENT ON COLUMN customers.api_key    IS '客户 API key（ACC 销售中心 客户api）';
COMMENT ON COLUMN customers.api_secret IS '客户 API secret';
