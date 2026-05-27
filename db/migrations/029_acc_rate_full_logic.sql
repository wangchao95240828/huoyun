-- ACC 全量报价逻辑：扩展 RateEngine 以支持客户专属价 / 客户组价 / 渠道账号限额 /
-- 服务限制（电池、敏感、仿牌）/ 邮编精确匹配 / 佣金规则 / 多段计费类型。
--
-- 对照 ACC 老逻辑（acc/config/Freight.php::getFee 第 350-660 行）：
--   - $Type=0 普通销售价 ↔ rate_cards.side='AR' & no override
--   - $ProductType=1 成本价 ↔ rate_cards.side='AP'
--   - ph.Customer=客户 Id 客户专属价 ↔ customer_rate_cards
--   - pi.Group=客户组 Id 组价 ↔ customer_group_rate_cards
--   - o.MaxCount/MaxPiece/MaxWeight 渠道账号限额 ↔ channel_account_limits
--   - find_in_set(BatteryType+1, o.Allow) 电池/敏感/仿牌过滤 ↔ service_restrictions
--   - Customer_Brokerage.Amount 佣金 ↔ commission_rules
--   - 邮编优先级（公布价 +8、价格表 +2） ↔ rate_card_lines.postal_priority

-- 1) rate_card_lines 加多段计费 + 邮编优先级字段
alter table rate_card_lines
  add column if not exists calculation_type text not null default 'PER_KG'
    check (calculation_type in ('PER_KG', 'PER_PIECE', 'TIER_FLAT', 'FIRST_CONTINUED')),
  add column if not exists first_weight_kg numeric(12,3),
  add column if not exists first_amount numeric(14,2),
  add column if not exists continued_step_kg numeric(12,3) default 0.5,
  add column if not exists continued_unit_price numeric(14,4),
  add column if not exists fixed_amount numeric(14,2),
  add column if not exists postal_code_pattern text,
  add column if not exists postal_priority smallint not null default 0;

-- 2) 客户专属价：客户 + 服务/渠道 → rate_card_id（最高优先级 Priority=2）
create table if not exists customer_rate_cards (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  customer_id uuid not null references customers(id) on delete cascade,
  channel_id uuid references channels(id),
  service_code text,
  rate_card_id uuid not null references rate_cards(id) on delete cascade,
  priority smallint not null default 100,
  effective_from date not null,
  effective_to date,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  unique (tenant_id, customer_id, channel_id, service_code, rate_card_id)
);
alter table customer_rate_cards enable row level security;
alter table customer_rate_cards force row level security;
create policy customer_rate_cards_tenant_isolation on customer_rate_cards
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 3) 客户组价：组 + 服务/渠道 → rate_card_id（中优先级 Priority=1）
create table if not exists customer_group_rate_cards (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  customer_group_id uuid not null references customer_groups(id) on delete cascade,
  channel_id uuid references channels(id),
  service_code text,
  rate_card_id uuid not null references rate_cards(id) on delete cascade,
  priority smallint not null default 50,
  effective_from date not null,
  effective_to date,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  unique (tenant_id, customer_group_id, channel_id, service_code, rate_card_id)
);
alter table customer_group_rate_cards enable row level security;
alter table customer_group_rate_cards force row level security;
create policy customer_group_rate_cards_tenant_isolation on customer_group_rate_cards
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 4) 渠道账号限额：单票限件/限重 + 当日票量上限
--    复用 acc_channel_accounts 中已有的渠道账号实体；本表是当天累计计数的"日票池"
create table if not exists channel_account_limits (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  channel_id uuid references channels(id),
  account_code text not null,
  max_count integer,          -- 当日票数上限
  max_piece integer,          -- 单票件数上限
  max_weight numeric(12,3),   -- 单票重量上限（kg）
  active boolean not null default true,
  created_at timestamptz not null default now(),
  unique (tenant_id, channel_id, account_code)
);
alter table channel_account_limits enable row level security;
alter table channel_account_limits force row level security;
create policy channel_account_limits_tenant_isolation on channel_account_limits
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

create table if not exists channel_account_daily_usage (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  channel_id uuid not null references channels(id),
  account_code text not null,
  usage_date date not null,
  count integer not null default 0,
  piece integer not null default 0,
  weight numeric(14,3) not null default 0,
  unique (tenant_id, channel_id, account_code, usage_date)
);
alter table channel_account_daily_usage enable row level security;
alter table channel_account_daily_usage force row level security;
create policy channel_account_daily_usage_tenant_isolation on channel_account_daily_usage
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 5) 服务限制：电池 / 敏感货 / 仿牌 / 国家 / 邮编 黑白名单
--    对应 ACC Channel_Account.Allow 位掩码：
--      bit0 = type=0 一般货, bit1 = type=1 特货, bit2 = 干电池, bit3 = 含电池池, bit5 = 仿牌
create table if not exists service_restrictions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  channel_id uuid references channels(id),
  account_code text,
  service_code text,
  battery_allowed boolean not null default true,
  battery_built_in_allowed boolean not null default true, -- 内置电池
  battery_dry_allowed boolean not null default true,      -- 干电池
  sensitive_allowed boolean not null default true,         -- 敏感货
  brand_allowed boolean not null default true,             -- 仿牌
  country_code char(2),
  country_blacklist boolean not null default false,
  postal_pattern text,
  active boolean not null default true,
  created_at timestamptz not null default now()
);
alter table service_restrictions enable row level security;
alter table service_restrictions force row level security;
create policy service_restrictions_tenant_isolation on service_restrictions
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 6) 佣金规则：客户 / 客户组 / 服务 / 渠道 → 比例或固定金额
--    对应 ACC Customer_Brokerage 表
create table if not exists rate_commission_rules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  customer_id uuid references customers(id) on delete cascade,
  customer_group_id uuid references customer_groups(id) on delete cascade,
  channel_id uuid references channels(id),
  service_code text,
  rule_type text not null check (rule_type in ('RATE', 'FIXED')),
  rate numeric(8,5),
  fixed_amount numeric(14,2),
  effective_from date not null,
  effective_to date,
  active boolean not null default true,
  created_at timestamptz not null default now()
);
alter table rate_commission_rules enable row level security;
alter table rate_commission_rules force row level security;
create policy rate_commission_rules_tenant_isolation on rate_commission_rules
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 7) 偏远规则：把 ACC 那套写死的 15%/25% 改为可配置
--    既有 remote_zones 表只判定级别，本表给出每个级别的费率
create table if not exists remote_rate_rules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  channel_id uuid references channels(id),
  level text not null check (level in ('NONE','REMOTE','SUPER_REMOTE','EMBARGO')),
  rate_type text not null check (rate_type in ('PERCENT', 'FIXED')),
  rate numeric(8,5),
  fixed_amount numeric(14,2),
  min_amount numeric(14,2),
  effective_from date not null default '1970-01-01',
  effective_to date,
  active boolean not null default true,
  created_at timestamptz not null default now()
);
alter table remote_rate_rules enable row level security;
alter table remote_rate_rules force row level security;
create policy remote_rate_rules_tenant_isolation on remote_rate_rules
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 默认偏远规则：兼容旧的 15%/25% 行为，让没配规则的租户能继续工作
insert into remote_rate_rules (tenant_id, channel_id, level, rate_type, rate, effective_from)
select t.id, null, lvl.level, 'PERCENT', lvl.rate, '1970-01-01'
from tenants t
cross join (values ('REMOTE', 0.15), ('SUPER_REMOTE', 0.25)) as lvl(level, rate)
on conflict do nothing;
