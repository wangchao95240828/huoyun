-- ACC Submit.php 111-117：JoinID > 0（订单已被合并）时拒绝 Submit
-- 新平台用 merged_to_order_id 显式记录"我被合并到了哪个主订单"，索引加速防护查询。

ALTER TABLE orders
  ADD COLUMN IF NOT EXISTS merged_to_order_id uuid REFERENCES orders(id);

CREATE INDEX IF NOT EXISTS idx_orders_merged_to
  ON orders (tenant_id, merged_to_order_id)
  WHERE merged_to_order_id IS NOT NULL;

COMMENT ON COLUMN orders.merged_to_order_id IS
  'ACC _Express.JoinID 对齐：记录本订单已合并到的主订单 ID。NULL=未合并；非 NULL=不可再单独 Submit';
