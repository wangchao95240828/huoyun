-- ACC FreightClass / Product 的限制字段对齐：
--
-- channels.max_weight_kg / max_length_cm（承运商硬限）：
--   ACC _Product.MaxWeight / MaxLength。超过则 Submit 拒绝。NULL=不启用。
--   放到 channels 表是因为这是渠道/承运商级别的硬性限制（如 UPS Ground ≤ 70kg, 单件 ≤ 274cm）。
--
-- rate_card_lines.min_item_kg（per-tier 最小件重提升）：
--   ACC _Product.MinItem 对应。单件计费重 < min_item_kg → 自动提升参与计费（不拦截，是修正）。

ALTER TABLE channels
  ADD COLUMN IF NOT EXISTS max_weight_kg  numeric(8,2),  -- 单件最大重量（kg），NULL=不限
  ADD COLUMN IF NOT EXISTS max_length_cm  numeric(8,2);  -- 单件最大长度（cm），NULL=不限

ALTER TABLE rate_card_lines
  ADD COLUMN IF NOT EXISTS min_item_kg    numeric(8,3);  -- 单件最小计费重（kg），实重低于此值自动提升

COMMENT ON COLUMN channels.max_weight_kg IS
  'ACC _Product.MaxWeight：承运商单件最大重量限制（kg），Submit 前校验。如 UPS Ground = 70';
COMMENT ON COLUMN channels.max_length_cm IS
  'ACC _Product.MaxLength：承运商单件最大长度（cm），Submit 前校验。如 UPS Ground = 274';
COMMENT ON COLUMN rate_card_lines.min_item_kg IS
  'ACC _Product.MinItem：单件最小计费重（kg），实重 < min_item_kg 时自动提升到该值参与计费';

-- 给 UPS Ground 种子（示例值，可改）
UPDATE channels SET max_weight_kg = 70, max_length_cm = 274
  WHERE code IN ('UPS-GROUND-US', 'EU-AIR-UPS') AND max_weight_kg IS NULL;
