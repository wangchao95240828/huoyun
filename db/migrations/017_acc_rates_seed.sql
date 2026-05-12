-- 017: ACC 运费试算 (rates) 演示费率数据
-- 复用已有的 channels / rate_cards / rate_card_lines / fuel_surcharge_rates / remote_zones 表。
-- 给现有 EU-AIR-UPS 渠道加一张 AR 价表，3 段重量梯度；再加一条 GB 偏远 zone 样本。

select set_config('app.service_role', 'true', false);

insert into rate_cards (
  tenant_id, channel_id, side, version, effective_from, currency, status
)
select t.id, ch.id, 'AR', 'ACC-DEMO-2026Q2', date '2026-04-01', 'CNY', 'ACTIVE'
from tenants t
join channels ch on ch.tenant_id = t.id and ch.code = 'EU-AIR-UPS'
where t.code = 'xqt'
on conflict (tenant_id, channel_id, side, version) do update
set effective_from = excluded.effective_from,
    currency = excluded.currency,
    status = excluded.status;

insert into rate_card_lines (
  tenant_id, rate_card_id, zone_code, weight_from, weight_to, uom, unit_price, min_amount, metadata
)
select t.id, rc.id, v.zone, v.wf::numeric, v.wt::numeric, 'KG', v.up::numeric, v.minamt::numeric, '{}'::jsonb
from tenants t
join rate_cards rc on rc.tenant_id = t.id and rc.version = 'ACC-DEMO-2026Q2'
cross join (
  values
    ('ZONE_A',  0,  1, 30.0, 30),
    ('ZONE_A',  1, 21, 25.0, null),
    ('ZONE_A', 21, 70, 22.0, null),
    ('ZONE_A', 70, 1000, 20.0, null)
) as v(zone, wf, wt, up, minamt)
where t.code = 'xqt'
  and not exists (
    select 1 from rate_card_lines rcl
    where rcl.rate_card_id = rc.id
      and rcl.zone_code = v.zone
      and rcl.weight_from = v.wf::numeric
  );

insert into remote_zones (
  tenant_id, channel_id, version, country_code, postal_code_pattern, level, effective_from
)
select t.id, ch.id, 'ACC-DEMO-2026Q2', 'GB', '^HS[0-9]', 'REMOTE'::remote_level, date '2026-04-01'
from tenants t
join channels ch on ch.tenant_id = t.id and ch.code = 'EU-AIR-UPS'
where t.code = 'xqt'
on conflict (tenant_id, channel_id, version, postal_code_pattern, fba_code) do nothing;

insert into fuel_surcharge_rates (tenant_id, channel_id, year_month, rate, source)
select t.id, ch.id, v.ym, v.r::numeric, 'ACC demo'
from tenants t
join channels ch on ch.tenant_id = t.id and ch.code = 'EU-AIR-UPS'
cross join (values ('2026-05', 0.185), ('2026-06', 0.190)) as v(ym, r)
where t.code = 'xqt'
on conflict (tenant_id, channel_id, year_month) do nothing;

select set_config('app.service_role', '', false);
