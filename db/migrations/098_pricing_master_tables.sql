-- ═════════════════════════════════════════════════════════════════════════
-- 098_pricing_master_tables
--
-- 修 P0-M6 + M7 + M8 三张大主表 (ACC PHP Zone/Surcharge/Insurance 复刻):
--   M6 zones + zone_countries: 价格分区 + 国家映射
--   M7 surcharges: 附加费规则主表 (含 Formula 公式表达式)
--   M8 insurance_rates: 保险费率 (Rate/MinFee/MaxFee/FreeCoverage)
-- ═════════════════════════════════════════════════════════════════════════

-- M6.1 价格分区主表
CREATE TABLE IF NOT EXISTS zones (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL DEFAULT current_setting('app.current_tenant_id')::uuid,
    code        text NOT NULL,         -- 分区代码 (Zone1/A区/...)
    name        text NOT NULL,
    pinyin      text,
    sort_order  int DEFAULT 0,
    remark      text,
    audit_status text NOT NULL DEFAULT 'PENDING' CHECK (audit_status IN ('PENDING','AUDITED','UNAUDITED')),
    audited_at  timestamptz, audit_name text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, code)
);

-- M6.2 分区 ↔ 国家 多对多映射 (一个分区可含 N 个国家)
CREATE TABLE IF NOT EXISTS zone_countries (
    zone_id     uuid NOT NULL REFERENCES zones(id) ON DELETE CASCADE,
    country_code char(2) NOT NULL,
    PRIMARY KEY (zone_id, country_code)
);
CREATE INDEX IF NOT EXISTS idx_zone_countries_country ON zone_countries (country_code);

-- M7 附加费规则主表 (公式驱动)
CREATE TABLE IF NOT EXISTS surcharges (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL DEFAULT current_setting('app.current_tenant_id')::uuid,
    code        text NOT NULL,         -- 附加费代码
    name        text NOT NULL,
    short_name  text,                  -- 附加简称 (面单/账单显示)
    -- price_id: 关联到 rate_card (按费率表分别配置)
    rate_card_id uuid REFERENCES rate_cards(id),
    -- fee_item_id: 关联费用项目
    fee_item_id uuid REFERENCES charge_items(id),
    -- method: 计费方式
    --   FIXED         固定 (Actual 元/票)
    --   PER_KG        按重量 (Actual 元/kg)
    --   PER_PIECE     按件数 (Actual 元/件)
    --   PERCENT_AR    按运费 % (Actual 千分比)
    --   PERCENT_VALUE 按申报价值 % (Actual 千分比)
    --   FORMULA       自定义公式 (eval Formula 表达式)
    method      text NOT NULL CHECK (method IN ('FIXED','PER_KG','PER_PIECE','PERCENT_AR','PERCENT_VALUE','FORMULA')),
    -- operate: 运算符 (Add/Sub/Mul/Div, 默认 Add)
    operate     text NOT NULL DEFAULT 'ADD' CHECK (operate IN ('ADD','SUB','MUL','DIV')),
    -- actual: 实际值 (元/千分比/系数)
    actual      numeric(14,4) NOT NULL DEFAULT 0,
    -- formula: 公式表达式 (method=FORMULA 时用, e.g. "weight * 0.5 + 10")
    formula     text,
    -- currency: 计费币种
    currency    char(3) NOT NULL DEFAULT 'CNY',
    -- active: 是否启用
    active      boolean NOT NULL DEFAULT true,
    effective_from date, effective_to date,
    audit_status text NOT NULL DEFAULT 'PENDING',
    audited_at  timestamptz, audit_name text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, code)
);
CREATE INDEX IF NOT EXISTS idx_surcharges_rate_card ON surcharges (tenant_id, rate_card_id, active)
    WHERE active = true;

-- M8 保险费率主表
CREATE TABLE IF NOT EXISTS insurance_rates (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL DEFAULT current_setting('app.current_tenant_id')::uuid,
    -- 关联渠道 (NULL = 全渠道兜底)
    channel_id  uuid REFERENCES channels(id),
    currency    char(3) NOT NULL DEFAULT 'CNY',
    -- rate: 保险费率 (千分比, e.g. 5.0 = 5‰)
    rate        numeric(8,4) NOT NULL,
    -- free_coverage: 免费保额 (低于此值不收保费, 走承运商赔付上限)
    free_coverage numeric(14,2) NOT NULL DEFAULT 0,
    -- min_fee: 最低保费
    min_fee     numeric(14,2) NOT NULL DEFAULT 0,
    -- max_fee: 最高保费 (0 = 不限)
    max_fee     numeric(14,2) NOT NULL DEFAULT 0,
    -- max_coverage: 最高保额 (单票上限)
    max_coverage numeric(14,2) NOT NULL DEFAULT 0,
    effective_from date NOT NULL DEFAULT current_date,
    effective_to   date,
    active      boolean NOT NULL DEFAULT true,
    remark      text,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_insurance_channel ON insurance_rates (tenant_id, channel_id, active)
    WHERE active = true;

COMMENT ON TABLE zones IS 'M6: 价格分区主表';
COMMENT ON TABLE zone_countries IS 'M6: 分区↔国家映射';
COMMENT ON TABLE surcharges IS 'M7: 附加费规则主表 (公式驱动, 复刻 ACC Online.php Surcharge)';
COMMENT ON COLUMN surcharges.formula IS '公式表达式 (method=FORMULA), 支持变量 weight/piece/declared_value/freight';
COMMENT ON TABLE insurance_rates IS 'M8: 保险费率 (复刻 ACC Online.php Insurance, 含免费保额/最低/最高/上限)';
