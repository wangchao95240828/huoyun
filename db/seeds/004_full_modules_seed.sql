select set_config('app.service_role', 'true', false);

insert into invoice_templates (tenant_id, code, name, template_type, currency_mode, layout_json)
select t.id, v.code, v.name, v.template_type, v.currency_mode, v.layout_json::jsonb
from tenants t
cross join (
  values
    ('STANDARD_TOTAL', '标准总金额账单', 'TOTAL', 'ORIGINAL', '{"columns":["shipment_no","charge_item","amount","currency"]}'),
    ('DIFF_VIEW', '差价对账账单', 'DIFFERENCE', 'DUAL', '{"columns":["shipment_no","original_amount","adjusted_amount","difference"]}'),
    ('CHANNEL_SUMMARY', '多渠道汇总账单', 'SUMMARY', 'DUAL', '{"group_by":["channel","currency"]}')
) as v(code, name, template_type, currency_mode, layout_json)
where t.code = 'xqt'
on conflict (tenant_id, code) do nothing;

insert into value_added_services (tenant_id, code, name, charge_item_id, default_uom)
select t.id, v.code, v.name, ci.id, v.default_uom::billing_uom
from tenants t
join charge_items ci on ci.tenant_id = t.id and ci.code = 'LTL_DELIVERY'
cross join (
  values
    ('LABELING', '贴标服务', 'PIECE'),
    ('PALLETIZING', '打板服务', 'PIECE'),
    ('INTERCEPT', '海外仓拦截', 'SHIPMENT'),
    ('POD_MANUAL', '手签 POD 申请', 'PIECE')
) as v(code, name, default_uom)
where t.code = 'xqt'
on conflict (tenant_id, code) do nothing;

insert into prohibited_items (tenant_id, code, item_name, keyword_pattern, country_code, risk_level, effective_from)
select t.id, v.code, v.item_name, v.keyword_pattern, v.country_code, v.risk_level, date '2026-04-01'
from tenants t
cross join (
  values
    ('US_SOLAR', '太阳能产品', '太阳能|solar', 'US', 'BLOCK'),
    ('US_STEEL_RACK', '不锈钢货架', '不锈钢货架|steel rack', 'US', 'BLOCK'),
    ('ADULT_GOODS', '成人用品', '成人用品|adult', null, 'REVIEW')
) as v(code, item_name, keyword_pattern, country_code, risk_level)
where t.code = 'xqt'
on conflict (tenant_id, code) do nothing;

select set_config('app.service_role', '', false);
