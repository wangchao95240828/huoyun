-- 013: greenfield product direction
-- The new platform is no longer a clone of XQT or ACC. It owns two first-class flows:
-- seller fulfillment and document shipping.

select set_config('app.service_role', 'true', false);

alter table customers
  add column if not exists customer_direction text not null default 'BOTH';

alter table customers
  add column if not exists service_modes jsonb not null default '[]';

alter table orders
  add column if not exists customer_direction text not null default 'DOCUMENT_CUSTOMER';

alter table orders
  add column if not exists order_entry_type text not null default 'MANUAL_DOCUMENT';

alter table orders
  add column if not exists service_mode text not null default 'DOCUMENT_SHIPPING';

alter table shipments
  add column if not exists customer_direction text not null default 'DOCUMENT_CUSTOMER';

do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'customers_customer_direction_check') then
    alter table customers
      add constraint customers_customer_direction_check
      check (customer_direction in ('SELLER_CUSTOMER', 'DOCUMENT_CUSTOMER', 'BOTH'));
  end if;

  if not exists (select 1 from pg_constraint where conname = 'orders_customer_direction_check') then
    alter table orders
      add constraint orders_customer_direction_check
      check (customer_direction in ('SELLER_CUSTOMER', 'DOCUMENT_CUSTOMER'));
  end if;

  if not exists (select 1 from pg_constraint where conname = 'orders_order_entry_type_check') then
    alter table orders
      add constraint orders_order_entry_type_check
      check (order_entry_type in ('SALES_ORDER', 'PORTAL_ORDER', 'API_ORDER', 'MANUAL_DOCUMENT', 'BATCH_IMPORT'));
  end if;

  if not exists (select 1 from pg_constraint where conname = 'orders_service_mode_check') then
    alter table orders
      add constraint orders_service_mode_check
      check (service_mode in ('SELLER_FULFILLMENT', 'DOCUMENT_SHIPPING', 'WAREHOUSE_ONLY', 'VALUE_ADDED'));
  end if;

  if not exists (select 1 from pg_constraint where conname = 'shipments_customer_direction_check') then
    alter table shipments
      add constraint shipments_customer_direction_check
      check (customer_direction in ('SELLER_CUSTOMER', 'DOCUMENT_CUSTOMER'));
  end if;
end
$$;

create table if not exists business_flows (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  flow_code text not null,
  customer_direction text not null
    check (customer_direction in ('SELLER_CUSTOMER', 'DOCUMENT_CUSTOMER')),
  name text not null,
  entry_channels jsonb not null default '[]',
  default_steps jsonb not null default '[]',
  settlement_model text not null,
  active boolean not null default true,
  notes text,
  created_at timestamptz not null default now(),
  unique (tenant_id, flow_code)
);

alter table business_flows enable row level security;
alter table business_flows force row level security;

do $$
begin
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'business_flows' and policyname = 'business_flows_tenant_isolation') then
    create policy business_flows_tenant_isolation on business_flows
      using (app_tenant_matches(tenant_id))
      with check (app_tenant_matches(tenant_id));
  end if;
end
$$;

insert into business_flows (
  tenant_id, flow_code, customer_direction, name, entry_channels, default_steps, settlement_model, notes
)
select t.id,
       v.flow_code,
       v.customer_direction,
       v.name,
       v.entry_channels::jsonb,
       v.default_steps::jsonb,
       v.settlement_model,
       v.notes
from tenants t
cross join (
  values
    (
      'SELLER_FULFILLMENT',
      'SELLER_CUSTOMER',
      '卖货客户履约流程',
      '["sales_order","warehouse_receipt","manual_order"]',
      '["quote","order","warehouse_in","pick_pack","ship","track","customer_invoice","partner_reconcile","profit_review"]',
      'AR_AP_PROFIT',
      '从商品、仓库、出货、账单到利润复盘的完整闭环。'
    ),
    (
      'DOCUMENT_SHIPPING',
      'DOCUMENT_CUSTOMER',
      '制单客户发货流程',
      '["api_order","batch_import","manual_document","customer_portal"]',
      '["rate_quote","order_validate","label_create","freight_deduct","ship","track","customer_statement","balance_reconcile"]',
      'PREPAID_OR_MONTHLY',
      '从 API/批量制单、面单、轨迹到余额和账单的高效率闭环。'
    )
) as v(flow_code, customer_direction, name, entry_channels, default_steps, settlement_model, notes)
where t.code = 'xqt'
on conflict (tenant_id, flow_code) do update
set customer_direction = excluded.customer_direction,
    name = excluded.name,
    entry_channels = excluded.entry_channels,
    default_steps = excluded.default_steps,
    settlement_model = excluded.settlement_model,
    notes = excluded.notes,
    active = true;

update external_systems
set status = 'ARCHIVED',
    notes = '历史参考资料：不再作为复刻或集成目标，当前产品按两套新流程从零开发。',
    metadata = metadata || '{"greenfield_reference_only": true}'::jsonb
where code in ('ACC', 'XQT');

update customers
set customer_direction = 'BOTH',
    service_modes = '["SELLER_FULFILLMENT","DOCUMENT_SHIPPING"]'::jsonb,
    source = 'LOCAL'
where code = 'KA-DEMO';

insert into customers (tenant_id, code, name, account_mode, default_currency, customer_direction, service_modes, source)
select t.id, v.code, v.name, v.account_mode::account_mode, 'CNY', v.customer_direction, v.service_modes::jsonb, 'LOCAL'
from tenants t
cross join (
  values
    ('SELLER-DEMO', '卖货流程样例客户', 'MONTHLY', 'SELLER_CUSTOMER', '["SELLER_FULFILLMENT"]'),
    ('DOC-DEMO', '制单流程样例客户', 'PREPAID', 'DOCUMENT_CUSTOMER', '["DOCUMENT_SHIPPING"]')
) as v(code, name, account_mode, customer_direction, service_modes)
where t.code = 'xqt'
on conflict (tenant_id, code) do update
set name = excluded.name,
    account_mode = excluded.account_mode,
    customer_direction = excluded.customer_direction,
    service_modes = excluded.service_modes,
    source = 'LOCAL';

insert into permissions (tenant_id, code, name, resource, action, description)
select t.id, v.code, v.name, v.resource, v.action, v.description
from tenants t
cross join (
  values
    ('flow.seller.read', '查看卖货流程', 'flow.seller', 'read', '查看卖货客户履约流程和数据'),
    ('flow.seller.write', '维护卖货流程', 'flow.seller', 'write', '创建和维护卖货客户订单、仓库、出货和账单'),
    ('flow.document.read', '查看制单流程', 'flow.document', 'read', '查看制单客户订单、面单、轨迹和账单'),
    ('flow.document.write', '维护制单流程', 'flow.document', 'write', '创建和维护制单、面单、扣费和状态回传')
) as v(code, name, resource, action, description)
where t.code = 'xqt'
on conflict (tenant_id, code) do nothing;

insert into role_permissions (tenant_id, role_id, permission_id)
select r.tenant_id, r.id, p.id
from roles r
join permissions p on p.tenant_id = r.tenant_id
where r.code in ('ADMIN', 'FINANCE_MANAGER')
  and p.code in ('flow.seller.read', 'flow.seller.write', 'flow.document.read', 'flow.document.write')
on conflict do nothing;

select set_config('app.service_role', '', false);
