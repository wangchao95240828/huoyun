-- 072: 补齐 ACC Express 表单字段（对齐 acc/Express.php 全部表单字段）
-- orders 加 8 个 header 级字段（影响价目表查询/打单/计费）
-- shipments 加 recipient_email / recipient_amazon_ref / is_customs

BEGIN;

ALTER TABLE orders
  ADD COLUMN IF NOT EXISTS postcode      text,
  ADD COLUMN IF NOT EXISTS item_type     text,          -- 文件/普货/敏感（对齐 ACC Type）
  ADD COLUMN IF NOT EXISTS battery_type  text,          -- 无/纯电/内置（对齐 ACC BatteryType）
  ADD COLUMN IF NOT EXISTS special_type  text,          -- 标准/化工/液体（对齐 ACC SpecialType）
  ADD COLUMN IF NOT EXISTS materials_en  text,          -- 英文申报品名
  ADD COLUMN IF NOT EXISTS is_insurance  boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS is_remote     boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS surcharge_ids jsonb   NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE shipments
  ADD COLUMN IF NOT EXISTS recipient_email      text,
  ADD COLUMN IF NOT EXISTS recipient_amazon_ref text,
  ADD COLUMN IF NOT EXISTS is_customs           boolean NOT NULL DEFAULT false;

-- item_type 等保持 text + check 而不是 enum，因为 ACC 是动态可配置的 radio
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_item_type_check;
ALTER TABLE orders ADD CONSTRAINT orders_item_type_check CHECK (
  item_type IS NULL OR item_type IN ('DOCUMENT','GENERAL','SENSITIVE','LIQUID','POWDER')
);
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_battery_type_check;
ALTER TABLE orders ADD CONSTRAINT orders_battery_type_check CHECK (
  battery_type IS NULL OR battery_type IN ('NONE','PURE','BUILT_IN','MATCH')
);
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_special_type_check;
ALTER TABLE orders ADD CONSTRAINT orders_special_type_check CHECK (
  special_type IS NULL OR special_type IN ('STANDARD','CHEMICAL','LIQUID','MAGNETIC')
);

COMMIT;
