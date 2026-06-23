-- ═════════════════════════════════════════════════════════════════════════
-- 096_master_data_fields
--
-- 修 P0 第 6 轮主数据缺字段 M1-M5:
--   M1 HSCode: rate (税率) / declared_value (默认申报值) / quick (快速候选)
--   M2 Channels: plugin (制单插件) / is_debug (调试模式)
--   M3 BankName: remark / sort_order
--   M4 Fuel: start_time (替代 year_month 时点级) + fuel_types 母表
--   M5 Remote: type (区间/单点/城市) / city
-- ═════════════════════════════════════════════════════════════════════════

-- M1 HSCode
ALTER TABLE hs_codes
  ADD COLUMN IF NOT EXISTS rate            numeric(6,4) DEFAULT 0,        -- 出口/进口税率 (千分比)
  ADD COLUMN IF NOT EXISTS declared_value  numeric(12,2) DEFAULT 0,       -- 默认申报金额
  ADD COLUMN IF NOT EXISTS quick           boolean NOT NULL DEFAULT false; -- 快速候选标记
CREATE INDEX IF NOT EXISTS idx_hs_codes_quick ON hs_codes (tenant_id, quick) WHERE quick = true;

-- M2 Channels
ALTER TABLE channels
  ADD COLUMN IF NOT EXISTS plugin       text,        -- 制单插件代码 (e.g. UPS_DEMO / FEDEX_DEMO / NOOP)
  ADD COLUMN IF NOT EXISTS is_debug     boolean NOT NULL DEFAULT false; -- 调试模式

-- M3 BankName
ALTER TABLE bank_names
  ADD COLUMN IF NOT EXISTS remark       text,
  ADD COLUMN IF NOT EXISTS sort_order   int DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_bank_names_sort ON bank_names (tenant_id, sort_order);

-- M4 Fuel: 加 fuel_types 母表 + 时点级 start_time
CREATE TABLE IF NOT EXISTS fuel_types (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL DEFAULT current_setting('app.current_tenant_id')::uuid,
    name        text NOT NULL,    -- e.g. "UPS 燃油" / "FedEx 燃油" / "DHL 燃油"
    remark      text,
    sort_order  int DEFAULT 0,
    active      boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);
ALTER TABLE fuel_surcharge_rates
  ADD COLUMN IF NOT EXISTS fuel_type_id uuid REFERENCES fuel_types(id),
  ADD COLUMN IF NOT EXISTS start_time   timestamptz;
-- 兼容: year_month 字段保留, start_time 当作精确时点优先
CREATE INDEX IF NOT EXISTS idx_fuel_rates_start ON fuel_surcharge_rates (tenant_id, start_time DESC);

-- M5 Remote
ALTER TABLE remote_zones
  ADD COLUMN IF NOT EXISTS type     text DEFAULT 'RANGE'
    CHECK (type IN ('RANGE','SINGLE','CITY')),   -- 区间/单点/城市三种判断模式
  ADD COLUMN IF NOT EXISTS city     text;        -- 城市级偏远

COMMENT ON COLUMN hs_codes.rate IS 'M1: 出口退税/进口税率 (千分比)';
COMMENT ON COLUMN hs_codes.declared_value IS 'M1: 默认申报金额 (USD)';
COMMENT ON COLUMN hs_codes.quick IS 'M1: 快速候选 (制单下拉优先显示)';
COMMENT ON COLUMN channels.plugin IS 'M2: 制单插件代码 (路由到 CarrierGateway)';
COMMENT ON COLUMN channels.is_debug IS 'M2: 调试模式 (true 时走 mock, 不发真单)';
COMMENT ON TABLE fuel_types IS 'M4: 燃油类型母表';
COMMENT ON COLUMN fuel_surcharge_rates.start_time IS 'M4: 时点级生效时间 (优先于 year_month)';
COMMENT ON COLUMN remote_zones.type IS 'M5: 偏远判断模式 RANGE/SINGLE/CITY';
