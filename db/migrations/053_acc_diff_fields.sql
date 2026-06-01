-- ACC vs 现有系统差异补齐（对照工作簿1777.xlsx）
-- 1) acc_fees 加 sort 字段（"杂费套餐 多一个排序"）
-- 2) fuel_surcharge_rates 加 fuel_type + remark（"燃油费用 多类型和备注"）
-- 3) customer_groups 加 product_limit（"分组管理 acc 多了产品限制"）

ALTER TABLE acc_fees
  ADD COLUMN IF NOT EXISTS sort_order integer NOT NULL DEFAULT 0;
COMMENT ON COLUMN acc_fees.sort_order IS '排序（ACC 杂费套餐 sort 字段）';

ALTER TABLE fuel_surcharge_rates
  ADD COLUMN IF NOT EXISTS fuel_type text,
  ADD COLUMN IF NOT EXISTS remark    text;
COMMENT ON COLUMN fuel_surcharge_rates.fuel_type IS '燃油类型（ACC: 国际/国内/海运 等）';
COMMENT ON COLUMN fuel_surcharge_rates.remark    IS '备注';

-- customer_groups 加 product_limit (jsonb 存允许使用的渠道/产品 id 列表)
ALTER TABLE customer_groups
  ADD COLUMN IF NOT EXISTS product_limit jsonb NOT NULL DEFAULT '{}'::jsonb;
COMMENT ON COLUMN customer_groups.product_limit IS '组的可用产品/渠道白名单（ACC: 分组管理 产品限制）';
