-- ═════════════════════════════════════════════════════════════════════
-- Sprint 1 剩余需求 — R-9 / R-11 / R-12 / R-6 数据模型
-- ═════════════════════════════════════════════════════════════════════

-- R-11: charges.source_type 标识来源
ALTER TABLE charges
  ADD COLUMN IF NOT EXISTS source_type text
    CHECK (source_type IN ('ESTIMATE','MANUAL','INVOICE','LIVE_QUOTE','RESTATE','BATCH_IMPORT'));

-- R-9 + R-11: charges.source_charge_id 补收/回退 charge 关联源
ALTER TABLE charges
  ADD COLUMN IF NOT EXISTS source_charge_id uuid REFERENCES charges(id);

CREATE INDEX IF NOT EXISTS idx_charges_source_charge_id ON charges(source_charge_id)
  WHERE source_charge_id IS NOT NULL;

-- R-12: 简化报价表 — customer_rate_strategies (基础表 × 佣金率)
CREATE TABLE IF NOT EXISTS customer_rate_strategies (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL DEFAULT current_setting('app.current_tenant_id', true)::uuid,
  customer_id uuid NOT NULL REFERENCES customers(id),
  channel_id uuid NOT NULL REFERENCES channels(id),
  base_rate_card_id uuid REFERENCES rate_cards(id),
  commission_rate numeric(6,4) NOT NULL DEFAULT 1.0000,  -- 1.00=原价, 0.90=9 折, 1.10=加 10%
  floor_amount numeric(10,2),                            -- 最低兜底
  effective_from timestamptz NOT NULL DEFAULT now(),
  effective_to timestamptz,
  active boolean NOT NULL DEFAULT true,
  remark text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, customer_id, channel_id, effective_from)
);

CREATE INDEX IF NOT EXISTS idx_crs_lookup
  ON customer_rate_strategies (tenant_id, customer_id, channel_id, active, effective_from DESC);

COMMENT ON TABLE customer_rate_strategies IS
  'R-12: 客户价格策略 — 基础价目表 × 佣金率, 替代每客户独立 rate_card_lines';

-- R-6: 批量预添加渠道报价 — cost_pre_estimates
CREATE TABLE IF NOT EXISTS cost_pre_estimates (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL DEFAULT current_setting('app.current_tenant_id', true)::uuid,
  channel_id uuid REFERENCES channels(id),
  channel_code text,
  customer_id uuid REFERENCES customers(id),
  qty int NOT NULL,
  unit_price numeric(10,4) NOT NULL,
  currency char(3) NOT NULL DEFAULT 'USD',
  target_weight_kg numeric(8,3),
  effective_date date NOT NULL DEFAULT current_date,
  reconciled_count int NOT NULL DEFAULT 0,  -- 已被真账单匹中的笔数
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','EXHAUSTED','EXPIRED','VOID')),
  remark text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cpe_lookup
  ON cost_pre_estimates (tenant_id, channel_code, status, effective_date DESC);

COMMENT ON TABLE cost_pre_estimates IS
  'R-6: 渠道商批量预估报价池 — 真账单到位后按 tracking 自动匹配核销';
