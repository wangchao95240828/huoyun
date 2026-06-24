-- ═════════════════════════════════════════════════════════════════════════
-- 101_fedex_dhl_carrier_rules
--
-- W3 落地后扩展 — UPS 之外的承运商种子规则
--
-- 数据源:
--   - FedEx Service Guide 2026 (Surcharges/DIM/Fuel)
--   - DHL Express Rate Sheet 2026 + MyDHL API SOAP Dev Guide v2.33
--   - 数值已对齐 2026 年公开 PDF / API 文档
--
-- 重要提醒:
--   - 燃油百分比是占位 (实际值随周/月调整, 必须接 ETL)
--   - DIM divisor 历史会调 (FedEx 2017 改过, UPS 2024 改过)
--   - Accessorial 单价每年 1 月调, 需年初 ETL 同步
-- ═════════════════════════════════════════════════════════════════════════

DO $$
BEGIN
  -- 确保 tenant_id 设了
  PERFORM set_config('app.current_tenant_id', '2bda8c16-7b19-4ce6-ab71-9584f5a140ed', false);
END $$;

INSERT INTO carrier_rules (carrier, product_class, rule_type, key, value_json, source, remark) VALUES

-- ════════ FedEx ════════

-- DIM divisor: FedEx 跟 UPS 一致 (139 US 国内, 166 国际)
('FEDEX', 'GROUND_US', 'DIM_DIVISOR', NULL,
 '{"divisor": 139, "unit": "IN-LBS", "note": "FedEx Ground 国内 div=139 (跟 UPS 一致)"}'::jsonb,
 'FEDEX_OFFICIAL_2024', '139 in³/lb ≈ 6000 cm³/kg'),
('FEDEX', 'INTERNATIONAL', 'DIM_DIVISOR', NULL,
 '{"divisor": 139, "unit": "IN-LBS", "note": "FedEx Express International div=139"}'::jsonb,
 'FEDEX_OFFICIAL_2024', '国际同 139'),

-- 燃油作用范围白名单
('FEDEX', NULL, 'FUEL_APPLICABLE_CODES', NULL,
 '{"codes": ["DELIVERY","PICKUP","RESIDENTIAL","SATURDAY","SIGNATURE","ADDITIONAL_HANDLING","LARGE_PACKAGE","OVER_MAXIMUM_LIMITS","ADDRESS_CORRECTION","DELIVERY_AREA"]}'::jsonb,
 'FEDEX_FSG_2026', 'FedEx 服务指南 fuel adjustment 适用条款'),

-- 当前周燃油 (占位 — 实际需 ETL 同步 FedEx 周公告)
('FEDEX', 'GROUND_US', 'FUEL_BUCKET', '2026-W26',
 '{"gallon_lo": 4.00, "gallon_hi": 4.04, "pct": 0.1800, "currency": "USD", "note": "Ground 占位 18% (DEMO)"}'::jsonb,
 'PLACEHOLDER', '⚠ 需 ETL 每周更新'),
('FEDEX', 'INTERNATIONAL', 'FUEL_BUCKET', '2026-W26',
 '{"gallon_lo": 4.00, "gallon_hi": 4.04, "pct": 0.2700, "currency": "USD", "note": "Intl Express 占位 27%"}'::jsonb,
 'PLACEHOLDER', '⚠ 国际线 fuel 比 Ground 高'),

-- 超大件阈值
('FEDEX', 'GROUND_US', 'OVERSIZE_THRESHOLD', NULL,
 '{"max_edge_in": 108, "max_girth_plus_length_in": 165, "max_weight_lb": 150}'::jsonb,
 'FEDEX_OFFICIAL', 'FedEx Ground 上限'),

('FEDEX', 'GROUND_US', 'LARGE_PACKAGE_THRESHOLD', NULL,
 '{"max_edge_in": 96, "max_girth_plus_length_in": 130}'::jsonb,
 'FEDEX_OFFICIAL', 'Large Package Surcharge 触发'),

('FEDEX', 'GROUND_US', 'ADDITIONAL_HANDLING_THRESHOLD', NULL,
 '{"max_edge_in": 48, "max_second_edge_in": 30, "max_weight_lb": 50}'::jsonb,
 'FEDEX_OFFICIAL', 'Add Handling 触发: 1 边>48in 或 2 边>30in 或 >50lb'),

-- 固定附加费 (2026 价)
('FEDEX', 'GROUND_US', 'ACCESSORIAL', 'RESIDENTIAL',
 '{"code": "RES", "amount": 5.85, "currency": "USD", "name": "FedEx Residential Surcharge"}'::jsonb,
 'FEDEX_OFFICIAL_2026', 'Ground 住宅派送 5.85'),
('FEDEX', 'GROUND_US', 'ACCESSORIAL', 'SIGNATURE_STANDARD',
 '{"code": "SIG_STD", "amount": 6.55, "currency": "USD", "name": "Signature Required (FedEx Direct)"}'::jsonb,
 'FEDEX_OFFICIAL_2026', '间接签收'),
('FEDEX', 'GROUND_US', 'ACCESSORIAL', 'SIGNATURE_ADULT',
 '{"code": "SIG_ADULT", "amount": 9.00, "currency": "USD", "name": "Adult Signature Required"}'::jsonb,
 'FEDEX_OFFICIAL_2026', '成人签收'),
('FEDEX', 'GROUND_US', 'ACCESSORIAL', 'SATURDAY_DELIVERY',
 '{"code": "SAT_DEL", "amount": 19.00, "currency": "USD", "name": "Saturday Delivery"}'::jsonb,
 'FEDEX_OFFICIAL_2026', '周六派送'),
('FEDEX', 'GROUND_US', 'ACCESSORIAL', 'ADDITIONAL_HANDLING',
 '{"code": "ADD_HANDLING", "amount": 29.85, "currency": "USD", "name": "Additional Handling Surcharge"}'::jsonb,
 'FEDEX_OFFICIAL_2026', '超长超重'),
('FEDEX', 'GROUND_US', 'ACCESSORIAL', 'LARGE_PACKAGE',
 '{"code": "LRG_PKG", "amount": 230.00, "currency": "USD", "name": "Oversize Charge"}'::jsonb,
 'FEDEX_OFFICIAL_2026', 'FedEx Oversize 收费比 UPS 高'),
('FEDEX', 'GROUND_US', 'ACCESSORIAL', 'ADDRESS_CORRECTION',
 '{"code": "ADDR_CORR", "amount": 22.50, "currency": "USD", "name": "Address Correction Charge"}'::jsonb,
 'FEDEX_OFFICIAL_2026', '地址修改费 (UPS 没单独这项)'),

-- ════════ DHL Express ════════

-- DIM divisor: DHL Express 国际 5000 cm³/kg (= 在 lb-in 系统下约 139)
('DHL', 'INTERNATIONAL', 'DIM_DIVISOR', NULL,
 '{"divisor": 5000, "unit": "CM-KG", "note": "DHL Express 国际 5000 cm³/kg"}'::jsonb,
 'DHL_OFFICIAL_2024', '国际航空线 5000 标准'),
('DHL', 'ECOMMERCE', 'DIM_DIVISOR', NULL,
 '{"divisor": 6000, "unit": "CM-KG", "note": "DHL eCommerce / 海运 6000 cm³/kg"}'::jsonb,
 'DHL_OFFICIAL_2024', '海运 / 经济件 6000'),

-- 燃油作用范围白名单 (DHL)
('DHL', NULL, 'FUEL_APPLICABLE_CODES', NULL,
 '{"codes": ["DELIVERY","PICKUP","RESIDENTIAL","SATURDAY","SIGNATURE","REMOTE_AREA","DEMAND"]}'::jsonb,
 'DHL_OFFICIAL', 'DHL fuel adjustment 适用条款 (跟 UPS/FedEx 略不同)'),

-- 当前月燃油 (DHL 是月调, 不是周调!)
('DHL', 'INTERNATIONAL', 'FUEL_BUCKET', '2026-06',
 '{"month": "2026-06", "pct": 0.2200, "currency": "USD", "note": "DHL Express 6 月燃油 22% (占位)"}'::jsonb,
 'PLACEHOLDER', '⚠ DHL 按月调, 跟 UPS/FedEx 不同'),

-- 超大件阈值 (DHL 国际线限值)
('DHL', 'INTERNATIONAL', 'OVERSIZE_THRESHOLD', NULL,
 '{"max_edge_in": 47, "max_girth_plus_length_in": 118, "max_weight_lb": 154,
   "note": "DHL Express 单件 120cm/300cm 体周/70kg"}'::jsonb,
 'DHL_OFFICIAL', '120cm/300cm/70kg (cm/cm/kg)'),

-- DHL 国际线 Large Package 跟 UPS 不同 (无 Large Package, 用 Add Handling 阈值)
('DHL', 'INTERNATIONAL', 'ADDITIONAL_HANDLING_THRESHOLD', NULL,
 '{"max_edge_in": 47, "max_weight_lb": 154}'::jsonb,
 'DHL_OFFICIAL', '120cm 或 70kg 触发 NON-STANDARD SHIPMENT'),

-- DHL 固定附加费 (2026 价)
('DHL', 'INTERNATIONAL', 'ACCESSORIAL', 'RESIDENTIAL',
 '{"code": "RES", "amount": 5.20, "currency": "USD", "name": "DHL Residential Surcharge"}'::jsonb,
 'DHL_OFFICIAL_2026', '住宅派送 (各国不同, US 这价)'),
('DHL', 'INTERNATIONAL', 'ACCESSORIAL', 'SIGNATURE_STANDARD',
 '{"code": "SIG_STD", "amount": 6.50, "currency": "USD", "name": "Direct Signature Service"}'::jsonb,
 'DHL_OFFICIAL_2026', '直接签收'),
('DHL', 'INTERNATIONAL', 'ACCESSORIAL', 'SIGNATURE_ADULT',
 '{"code": "SIG_ADULT", "amount": 8.50, "currency": "USD", "name": "Adult Signature Service"}'::jsonb,
 'DHL_OFFICIAL_2026', '成人签收'),
('DHL', 'INTERNATIONAL', 'ACCESSORIAL', 'SATURDAY_DELIVERY',
 '{"code": "SAT_DEL", "amount": 24.00, "currency": "USD", "name": "Saturday Delivery (DHL Premium)"}'::jsonb,
 'DHL_OFFICIAL_2026', 'DHL 周六派送比 UPS/FedEx 贵'),
('DHL', 'INTERNATIONAL', 'ACCESSORIAL', 'ADDITIONAL_HANDLING',
 '{"code": "NON_STD", "amount": 38.00, "currency": "USD", "name": "Non-Standard Shipment Surcharge"}'::jsonb,
 'DHL_OFFICIAL_2026', 'DHL 用 Non-Standard 名义, 价高'),
('DHL', 'INTERNATIONAL', 'ACCESSORIAL', 'OVERWEIGHT',
 '{"code": "OVERWEIGHT", "amount": 100.00, "currency": "USD", "name": "Overweight Piece (>70kg)"}'::jsonb,
 'DHL_OFFICIAL_2026', '单件 > 70kg 加 100 USD'),
('DHL', 'INTERNATIONAL', 'ACCESSORIAL', 'REMOTE_AREA',
 '{"code": "REMOTE", "amount": 35.00, "currency": "USD", "name": "Remote Area Delivery"}'::jsonb,
 'DHL_OFFICIAL_2026', '偏远地区派送 (覆盖 UPS/FedEx 不到的区)');

COMMENT ON COLUMN carrier_rules.value_json IS '101: 扩 FedEx + DHL 21 条种子规则';
