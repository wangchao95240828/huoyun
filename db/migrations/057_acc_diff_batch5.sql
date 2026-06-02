-- ACC 字段对齐第五批（来源：curl 抓取 20 个 ACC add 页面）
--
-- 1) 财务类: payments + acc_finance_txns + acc_transfers 加 poundage(手续费) + rate(汇率)
-- 2) acc_borrowings 加 cycle/mode/pay_date/remaining/forward/repayment/fixed_amount/employee_id
-- 3) acc_asks 加 is_show (客户可见标记)
-- 4) customer_groups 加 product_limit(已有) 但需用 account_limit 替换
-- 5) customers 加 customer_invoices StartDate/EndDate (已存在不动)
-- 6) countries 加 TW/HK 名称 + Code2/Code3 + Phone (国际区号)
-- 7) acc_remotes (偏远邮编) 加 logistics_type + zip_low/zip_high
-- 8) acc_reparations 已对齐
-- 9) users 加 Range[]/Department[]/Permissions[] 数组列

-- ─── 财务类手续费 + 汇率 ───
ALTER TABLE payments
  ADD COLUMN IF NOT EXISTS poundage  numeric(14,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS fx_rate   numeric(18,8);
COMMENT ON COLUMN payments.poundage IS '手续费 (ACC Poundage 字段)';
COMMENT ON COLUMN payments.fx_rate  IS '汇率 (ACC Rate 字段, 转账时折算用)';

ALTER TABLE partner_payments
  ADD COLUMN IF NOT EXISTS poundage  numeric(14,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS fx_rate   numeric(18,8),
  ADD COLUMN IF NOT EXISTS pay_currency char(3);
COMMENT ON COLUMN partner_payments.poundage      IS '手续费';
COMMENT ON COLUMN partner_payments.pay_currency  IS 'ACC PayCurrency (付款币种, 抵账时与 currency 不同)';

ALTER TABLE acc_finance_txns
  ADD COLUMN IF NOT EXISTS poundage     numeric(14,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS fx_rate      numeric(18,8),
  ADD COLUMN IF NOT EXISTS pay_currency char(3),
  ADD COLUMN IF NOT EXISTS bank_id      uuid;
COMMENT ON COLUMN acc_finance_txns.poundage     IS '手续费 (CRefund/CSponsor/Pay/Received 都有)';
COMMENT ON COLUMN acc_finance_txns.pay_currency IS '实付币种 (与 currency 不同时折算)';
COMMENT ON COLUMN acc_finance_txns.bank_id      IS '关联银行账户 (ACC Bank)';

-- ─── 资金借贷 17 字段（ACC Borrowing 完整字段） ───
ALTER TABLE acc_borrowings
  ADD COLUMN IF NOT EXISTS employee_id  uuid,                 -- ACC Employee (借贷员工)
  ADD COLUMN IF NOT EXISTS start_date   date,
  ADD COLUMN IF NOT EXISTS end_date     date,
  ADD COLUMN IF NOT EXISTS cycle        text,                 -- 借款周期 (按周/月/年)
  ADD COLUMN IF NOT EXISTS mode         text,                 -- 还款模式 (等额本金/等额本息/到期)
  ADD COLUMN IF NOT EXISTS pay_date     date,                 -- 下次还款日
  ADD COLUMN IF NOT EXISTS remaining    numeric(14,2),        -- 剩余本金
  ADD COLUMN IF NOT EXISTS forward      numeric(14,2),        -- 前置利息
  ADD COLUMN IF NOT EXISTS repayment    text,                 -- 还款设置
  ADD COLUMN IF NOT EXISTS fixed_amount numeric(14,2);        -- 固定金额
COMMENT ON COLUMN acc_borrowings.cycle  IS 'ACC Cycle 借款周期';
COMMENT ON COLUMN acc_borrowings.mode   IS 'ACC Mode 还款模式';

-- ─── 问题件 ───
ALTER TABLE acc_asks
  ADD COLUMN IF NOT EXISTS is_show boolean NOT NULL DEFAULT false;
COMMENT ON COLUMN acc_asks.is_show IS 'ACC isShow 客户可见';

-- ─── 客户分组扩账号白名单 ───
ALTER TABLE customer_groups
  ADD COLUMN IF NOT EXISTS account_limit jsonb NOT NULL DEFAULT '[]'::jsonb;
COMMENT ON COLUMN customer_groups.account_limit IS 'ACC Account[] 渠道账号白名单';

-- ─── 国家多语言 + ISO codes ───
ALTER TABLE countries
  ADD COLUMN IF NOT EXISTS name_tw   text,
  ADD COLUMN IF NOT EXISTS name_hk   text,
  ADD COLUMN IF NOT EXISTS code3     char(3),
  ADD COLUMN IF NOT EXISTS phone     text,
  ADD COLUMN IF NOT EXISTS sort_order integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS parent_id  uuid;
COMMENT ON COLUMN countries.name_tw IS 'ACC TW 繁体名';
COMMENT ON COLUMN countries.name_hk IS 'ACC HK 粤语名';
COMMENT ON COLUMN countries.code3   IS 'ISO 3-letter';

-- ─── 偏远邮编 范围式 (用 remote_zones 表) ───
ALTER TABLE remote_zones
  ADD COLUMN IF NOT EXISTS logistics_type text,
  ADD COLUMN IF NOT EXISTS zip_low        text,
  ADD COLUMN IF NOT EXISTS zip_high       text;
COMMENT ON COLUMN remote_zones.logistics_type IS 'ACC Logistics radio (绑定渠道/物流)';
COMMENT ON COLUMN remote_zones.zip_low        IS 'ACC Low 偏远邮编起';
COMMENT ON COLUMN remote_zones.zip_high       IS 'ACC High 偏远邮编止';

-- ─── 用户权限扩展 ───
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS bound_customer_id uuid,           -- 绑定客户 (ACC Customer)
  ADD COLUMN IF NOT EXISTS bound_supplier_id uuid,           -- 绑定服务商
  ADD COLUMN IF NOT EXISTS bound_employee_id uuid,
  ADD COLUMN IF NOT EXISTS user_grade text,                  -- ACC Grade (客户/员工 等级)
  ADD COLUMN IF NOT EXISTS ranges     jsonb DEFAULT '[]',    -- ACC Range[] 功能限制
  ADD COLUMN IF NOT EXISTS departments jsonb DEFAULT '[]',   -- ACC Department[] 部门限制
  ADD COLUMN IF NOT EXISTS allowed_branches jsonb DEFAULT '[]',  -- ACC Branch[] 分店权限
  ADD COLUMN IF NOT EXISTS description text;
COMMENT ON COLUMN users.bound_customer_id IS 'ACC: 客户角色用户绑定的客户';
COMMENT ON COLUMN users.bound_supplier_id IS 'ACC: 服务商角色用户绑定的物流商';

-- ─── 客户账单批量生成支持期间 ───
ALTER TABLE customer_invoices
  ADD COLUMN IF NOT EXISTS bill_no    text,
  ADD COLUMN IF NOT EXISTS start_date date,
  ADD COLUMN IF NOT EXISTS end_date   date;
COMMENT ON COLUMN customer_invoices.start_date IS 'ACC StartDate 账期起';
COMMENT ON COLUMN customer_invoices.end_date   IS 'ACC EndDate 账期止';
