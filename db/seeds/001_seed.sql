select set_config('app.service_role', 'true', false);

insert into tenants (code, name)
values ('xqt', '新启天')
on conflict (code) do nothing;

insert into users (tenant_id, email, display_name, role_code)
select id, 'finance@xqt.local', '财务主管', 'FINANCE_MANAGER'
from tenants where code = 'xqt'
on conflict (tenant_id, email) do nothing;

insert into customers (tenant_id, code, name, account_mode, default_currency)
select id, 'KA-DEMO', '样例客户', 'MONTHLY', 'CNY'
from tenants where code = 'xqt'
on conflict (tenant_id, code) do nothing;

insert into carriers (tenant_id, code, name, carrier_type)
select t.id, v.code, v.name, v.carrier_type
from tenants t
cross join (
  values
    ('UPS', 'UPS', 'EXPRESS'),
    ('FEDEX', 'FedEx', 'EXPRESS'),
    ('LTL-DEMO', '本地卡派代理样例', 'LTL'),
    ('INS-DEMO', '保险公司样例', 'INSURANCE')
) as v(code, name, carrier_type)
where t.code = 'xqt'
on conflict (tenant_id, code) do nothing;

insert into channels (tenant_id, code, name, lane, last_mile_method, primary_uom, dim_factor, min_weight_per_carton)
select t.id, v.code, v.name, v.lane, v.last_mile_method, v.primary_uom::billing_uom, v.dim_factor, v.min_weight_per_carton
from tenants t
cross join (
  values
    ('EU-AIR-UPS', '欧洲空派 UPS', 'EU', 'EXPRESS', 'KG', 6000, 12),
    ('EU-TRUCK-LTL', '欧洲卡航卡派', 'EU', 'LTL', 'KG', 6000, 8),
    ('US-GROUND-FEDEX', '美国尾端 FedEx Ground', 'US', 'EXPRESS', 'LB', 8000, null)
) as v(code, name, lane, last_mile_method, primary_uom, dim_factor, min_weight_per_carton)
where t.code = 'xqt'
on conflict (tenant_id, code) do nothing;

insert into charge_items (tenant_id, code, name, category, default_side, default_uom)
select t.id, v.code, v.name, v.category, v.side::charge_side, v.uom::billing_uom
from tenants t
cross join (
  values
    ('FREIGHT', '基础运费', 'FREIGHT', 'AR', 'KG'),
    ('FUEL', '燃油附加费', 'SURCHARGE', 'AR', 'PERCENT'),
    ('SENSITIVE_GOODS', '敏感品附加费', 'SURCHARGE', 'AR', 'KG'),
    ('OVERSIZE', '超尺寸附加费', 'SURCHARGE', 'AR', 'CARTON'),
    ('REMOTE', '偏远附加费', 'SURCHARGE', 'AR', 'SHIPMENT'),
    ('CUSTOMS', '报关费', 'CUSTOMS', 'AR', 'SHIPMENT'),
    ('INSURANCE', '保险费', 'INSURANCE', 'AP', 'PERCENT'),
    ('LTL_DELIVERY', '卡派派送费', 'DELIVERY', 'AP', 'KG')
) as v(code, name, category, side, uom)
where t.code = 'xqt'
on conflict (tenant_id, code) do nothing;

insert into rule_versions (tenant_id, code, name, effective_from)
select id, 'FIN-2026-MVP', '财务计费规则 MVP 基线', date '2026-04-01'
from tenants where code = 'xqt'
on conflict (tenant_id, code) do nothing;

insert into charge_rules (
  tenant_id, rule_version_id, charge_item_id, channel_id, name, side, uom,
  unit_price, fixed_amount, combination_group, combination_strategy, scope, priority, condition_json
)
select t.id, rv.id, ci.id, ch.id, '木制品敏感品 +0.5/kg', 'AR', 'KG',
       0.5, null, 'PRODUCT_SENSITIVE', 'MAX', 'SHIPMENT', 10,
       '{"keywords":["木制品","wood","bamboo"],"match_field":"declaration.item_name"}'::jsonb
from tenants t
join rule_versions rv on rv.tenant_id = t.id and rv.code = 'FIN-2026-MVP'
join charge_items ci on ci.tenant_id = t.id and ci.code = 'SENSITIVE_GOODS'
join channels ch on ch.tenant_id = t.id and ch.code = 'EU-AIR-UPS'
where t.code = 'xqt';

insert into fuel_surcharge_rates (tenant_id, channel_id, year_month, rate, source)
select t.id, ch.id, '2026-04', 0.185, '手工录入样例'
from tenants t
join channels ch on ch.tenant_id = t.id and ch.code = 'EU-AIR-UPS'
where t.code = 'xqt'
on conflict (tenant_id, channel_id, year_month) do nothing;

select set_config('app.service_role', '', false);
