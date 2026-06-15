-- 084: customers 表加 servicer_user_id 列 (客服代表)
-- ────────────────────────────────────────────────────────────────────
-- customers.salesman_user_id 早已存在（对应 ACC SalesmanID = 销售代表）。
-- 但缺 servicer_user_id（ACC ServicerID = 客服代表）。
-- 加上后:
--   - 客户档案页可以维护「默认销售/客服」
--   - 制单时新订单可从 customer 继承 seller_id/servicer_id
--   - 报表/筛选可以按客户的销售/客服分组（现在 orders 已有列，customer 层 为业务管理用）
-- ────────────────────────────────────────────────────────────────────

ALTER TABLE customers ADD COLUMN IF NOT EXISTS servicer_user_id uuid REFERENCES users(id);

CREATE INDEX IF NOT EXISTS idx_customers_servicer
  ON customers (tenant_id, servicer_user_id)
  WHERE servicer_user_id IS NOT NULL;

COMMENT ON COLUMN customers.servicer_user_id
  IS 'ACC ServicerID 兼容: 客户默认的客服代表，新订单 servicer_id 从此继承';
