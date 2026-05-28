-- 任务 S3：RateEngine 补 PRD sprint1 6 条规则
--
-- 对照 docs/prd-sprint1-billing-engine.md + docs/acc-logic-gap-claude-task-supplement-2026-05-29.md 任务 S3。
--
-- 已有：dim_factor（channels）、calculation_type=PER_KG/PER_PIECE/TIER_FLAT/FIRST_CONTINUED、
--       remote_zones.level=NONE/REMOTE/SUPER_REMOTE/EMBARGO、EMBARGO 已在 blockers。
-- 本期补：
--   A1 product_keyword_rules（品名关键词附加费）
--   A2/A3 rate_card_lines.surcharge_group + combination_strategy
--   A4 rate_card_lines.min_weight_per_box + min_amount_per_box
--   A6 加 PER_CBM 到 calculation_type（同时把 LB 看作 KG 等价处理，前端转换后传 KG）

-- ─── A1: 品名关键词附加费 ───
create table product_keyword_rules (
  id bigserial primary key,
  tenant_id uuid not null,
  keyword text not null,
  match_type text not null default 'CONTAINS'
    check (match_type in ('CONTAINS', 'EXACT', 'PREFIX')),
  fee_code text not null,                 -- 对应 charge_items.code，如 BATTERY_SURCHARGE
  charge_unit text not null default 'FIXED'
    check (charge_unit in ('FIXED', 'PCT')),
  amount numeric(12, 2),                  -- FIXED 时用
  rate numeric(6, 4),                     -- PCT 时用（0.05 = 5%）
  currency char(3) not null default 'CNY',
  priority int not null default 0,        -- 同 fee_code 多条命中时取 priority 最高
  is_active boolean not null default true,
  effective_from date,
  effective_to date,
  created_at timestamptz not null default now(),
  created_by text,
  updated_at timestamptz not null default now(),
  updated_by text
);
create index idx_pkr_tenant_active on product_keyword_rules(tenant_id, is_active);
create index idx_pkr_keyword_lookup on product_keyword_rules(tenant_id, fee_code, priority desc)
  where is_active = true;

alter table product_keyword_rules enable row level security;
alter table product_keyword_rules force row level security;
create policy pkr_tenant_isolation on product_keyword_rules
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- ─── A2/A3: 附加费分组策略 ───
alter table rate_card_lines
  add column if not exists surcharge_group text,
  add column if not exists combination_strategy text not null default 'STACK'
    check (combination_strategy in ('STACK', 'MAX'));

-- ─── A4: 按箱最低计费 ───
alter table rate_card_lines
  add column if not exists min_weight_per_box numeric(8, 3),
  add column if not exists min_amount_per_box numeric(12, 2);

-- ─── A6: PER_CBM 加入 calculation_type ───
alter table rate_card_lines
  drop constraint if exists rate_card_lines_calculation_type_check;
alter table rate_card_lines
  add constraint rate_card_lines_calculation_type_check
  check (calculation_type in ('PER_KG', 'PER_PIECE', 'TIER_FLAT', 'FIRST_CONTINUED', 'PER_CBM'));

-- A5/A6 部分已有：
--   remote_zones.level enum 含 EMBARGO（已在 RateEngine 入 blockers）
--   channels.dim_factor 已存在
--   rate_card_lines.uom (billing_uom enum) 已存在
