-- ═════════════════════════════════════════════════════════════════════════
-- 099_carrier_rules
--
-- W1 deep-research 落地 — Carrier Rule 抽象层
--
-- 调研结论 (UPS Fuel PDF + ShipEngine + EasyPost 路径):
--   - UPS 燃油按周维护, $0.04/加仑桶 → 0.25% 步进
--   - UPS DIM divisor 历史调整过 (139 → 166 retail/2024)
--   - 燃油作用范围有白名单 (Residential/Saturday/Signature/Add Handling/...)
--   - 不能写死, 必须可热更新规则集
-- ═════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS carrier_rules (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL DEFAULT current_setting('app.current_tenant_id')::uuid,
    -- 承运商: UPS / FEDEX / DHL / EMS / SF / YT...
    carrier     text NOT NULL,
    -- 产品级 (可空, 空 = 全承运商共用):
    --   UPS Ground / UPS Worldwide Express / UPS Worldwide Saver /
    --   FedEx Ground / FedEx Express Saver / FedEx International / ...
    product_class text,
    -- 规则类型 (rule_type):
    --   DIM_DIVISOR             dim factor (in³/lb 或 cm³/kg)
    --   FUEL_BUCKET             燃油桶查表 (key = week_start, value = {gallon_lo, gallon_hi, pct})
    --   FUEL_APPLICABLE_CODES   燃油叠加在哪些 charge code 上 (白名单)
    --   ACCESSORIAL             固定附加费 (Residential/Signature/Saturday/etc 单价)
    --   OVERSIZE_THRESHOLD      超大件判定阈值 (单边 in / girth+length in / weight lb)
    --   LARGE_PACKAGE_THRESHOLD 大包裹判定阈值
    --   ADDITIONAL_HANDLING_THRESHOLD 超长超重判定
    --   ZONE_MAP                origin→dest zone 查表
    --   REMOTE_FEE              偏远附加费
    --   DEMAND_SURCHARGE        旺季附加费
    rule_type   text NOT NULL CHECK (rule_type IN (
        'DIM_DIVISOR','FUEL_BUCKET','FUEL_APPLICABLE_CODES','ACCESSORIAL',
        'OVERSIZE_THRESHOLD','LARGE_PACKAGE_THRESHOLD','ADDITIONAL_HANDLING_THRESHOLD',
        'ZONE_MAP','REMOTE_FEE','DEMAND_SURCHARGE','MIN_BILLABLE_WEIGHT','OTHER')),
    -- key: 规则内的子键 (e.g. fuel_bucket 用 week_start; accessorial 用 charge code)
    --      可空 (整张规则一个 key)
    key         text,
    -- value: 规则数据 (jsonb 灵活, 演进时不改 schema)
    --   FUEL_BUCKET 例: {"gallon_lo": 2.19, "gallon_hi": 2.23, "pct": 0.2075, "currency": "USD"}
    --   FUEL_APPLICABLE_CODES 例: {"codes": ["Residential","Saturday","Signature","Add_Handling","Large_Package"]}
    --   DIM_DIVISOR 例: {"divisor": 139, "unit": "IN-LBS"}
    --   ACCESSORIAL 例: {"code": "Residential", "amount": 5.45, "currency": "USD", "name": "Residential Surcharge"}
    --   OVERSIZE_THRESHOLD 例: {"max_edge_in": 96, "max_girth_plus_length_in": 130}
    value_json  jsonb NOT NULL,
    -- 时间窗 (rule set 模式, 可重叠预上线测试)
    effective_from timestamptz NOT NULL DEFAULT now(),
    effective_to   timestamptz,
    -- 优先级 (多 active 规则同时命中时, 高优先级覆盖低)
    priority    int NOT NULL DEFAULT 0,
    -- 元数据
    source      text,                  -- 'UPS_PDF_2026_W26' / 'manual' / 'etl'
    source_url  text,                  -- 数据源 URL (审计)
    remark      text,
    active      boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_carrier_rules_lookup
    ON carrier_rules (tenant_id, carrier, rule_type, key, effective_from DESC)
    WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_carrier_rules_effective
    ON carrier_rules (tenant_id, carrier, rule_type, effective_from, effective_to)
    WHERE active = true;

COMMENT ON TABLE carrier_rules IS 'W1: 承运商规则抽象层 (燃油桶/DIM/附加费白名单/超大件阈值, 可热更新)';

-- ════════ 种子数据: UPS Ground 2026 W26 ════════
INSERT INTO carrier_rules (carrier, product_class, rule_type, key, value_json, source, remark)
VALUES
  -- DIM divisor: UPS US 国内 139 (in³/lb)
  ('UPS', 'GROUND_US', 'DIM_DIVISOR', NULL,
   '{"divisor": 139, "unit": "IN-LBS", "note": "UPS US daily/retail 国内 div=139"}'::jsonb,
   'UPS_OFFICIAL_2024', '139 in³/lb = ~6000 cm³/kg'),
  ('UPS', 'INTERNATIONAL', 'DIM_DIVISOR', NULL,
   '{"divisor": 166, "unit": "IN-LBS", "note": "UPS Daily/Retail 国际 div=166 (从 2024 起)"}'::jsonb,
   'UPS_OFFICIAL_2024', '166 in³/lb = ~5000 cm³/kg'),

  -- 燃油作用范围白名单 (UPS fsc-applicable-charges.pdf)
  ('UPS', NULL, 'FUEL_APPLICABLE_CODES', NULL,
   '{"codes": ["DELIVERY","PICKUP","RESIDENTIAL","SATURDAY","SIGNATURE","ADDITIONAL_HANDLING","LARGE_PACKAGE","OVER_MAXIMUM_LIMITS","REMOTE_AREA","DEMAND_SURCHARGE"]}'::jsonb,
   'UPS_OFFICIAL_PDF', 'fsc-applicable-charges.pdf 白名单'),

  -- 当前周燃油 (示例, 2026 年 6 月 第 26 周 - 实际值需 ETL)
  ('UPS', 'GROUND_US', 'FUEL_BUCKET', '2026-W26',
   '{"gallon_lo": 4.00, "gallon_hi": 4.04, "pct": 0.2575, "currency": "USD", "note": "Ground 当周燃油示例 25.75% (DEMO, 实际需 ETL)"}'::jsonb,
   'PLACEHOLDER', '⚠ 需挂 ETL 每周一更新真实值'),

  -- 超大件阈值 (UPS Ground)
  ('UPS', 'GROUND_US', 'OVERSIZE_THRESHOLD', NULL,
   '{"max_edge_in": 108, "max_girth_plus_length_in": 165, "max_weight_lb": 150}'::jsonb,
   'UPS_OFFICIAL', 'UPS Ground 上限'),

  -- 大包裹 (Large Package Surcharge 触发)
  ('UPS', 'GROUND_US', 'LARGE_PACKAGE_THRESHOLD', NULL,
   '{"max_edge_in": 96, "max_girth_plus_length_in": 130}'::jsonb,
   'UPS_OFFICIAL', '超过即收 Large Package'),

  -- 超长超重 (Additional Handling Surcharge)
  ('UPS', 'GROUND_US', 'ADDITIONAL_HANDLING_THRESHOLD', NULL,
   '{"max_edge_in": 48, "max_weight_lb": 50, "packaging": ["NOT_SUPPLIED_BY_UPS"]}'::jsonb,
   'UPS_OFFICIAL', '一边 > 48in 或 重 > 50lb'),

  -- 固定附加费 (Residential Surcharge 示例)
  ('UPS', 'GROUND_US', 'ACCESSORIAL', 'RESIDENTIAL',
   '{"code": "RESIDENTIAL", "amount": 5.65, "currency": "USD", "name": "Residential Surcharge", "billable": true}'::jsonb,
   'UPS_OFFICIAL_2026', '住宅派送 5.65 USD/票'),
  ('UPS', 'GROUND_US', 'ACCESSORIAL', 'SIGNATURE_STANDARD',
   '{"code": "SIG_STD", "amount": 7.50, "currency": "USD", "name": "Signature Required"}'::jsonb,
   'UPS_OFFICIAL_2026', '签收 7.50 USD/票'),
  ('UPS', 'GROUND_US', 'ACCESSORIAL', 'SIGNATURE_ADULT',
   '{"code": "SIG_ADULT", "amount": 8.55, "currency": "USD", "name": "Adult Signature Required"}'::jsonb,
   'UPS_OFFICIAL_2026', '成人签收 8.55 USD/票'),
  ('UPS', 'GROUND_US', 'ACCESSORIAL', 'SATURDAY_DELIVERY',
   '{"code": "SAT_DEL", "amount": 19.50, "currency": "USD", "name": "Saturday Delivery"}'::jsonb,
   'UPS_OFFICIAL_2026', '周六派送 19.50 USD/票'),
  ('UPS', 'GROUND_US', 'ACCESSORIAL', 'ADDITIONAL_HANDLING',
   '{"code": "ADD_HANDLING", "amount": 27.80, "currency": "USD", "name": "Additional Handling Surcharge"}'::jsonb,
   'UPS_OFFICIAL_2026', '超长超重附加费 27.80 USD/票'),
  ('UPS', 'GROUND_US', 'ACCESSORIAL', 'LARGE_PACKAGE',
   '{"code": "LRG_PKG", "amount": 195.00, "currency": "USD", "name": "Large Package Surcharge"}'::jsonb,
   'UPS_OFFICIAL_2026', '大包裹 195.00 USD/票');

COMMENT ON COLUMN carrier_rules.value_json IS 'W1: 规则数据, jsonb schema 因 rule_type 而异';
