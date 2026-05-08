select set_config('app.service_role', 'true', false);

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
      '测试数据覆盖卖货客户订单、仓配、发货、账单、利润复盘。'
    ),
    (
      'DOCUMENT_SHIPPING',
      'DOCUMENT_CUSTOMER',
      '制单客户发货流程',
      '["api_order","batch_import","manual_document","customer_portal"]',
      '["rate_quote","order_validate","label_create","freight_deduct","ship","track","customer_statement","balance_reconcile"]',
      'PREPAID_OR_MONTHLY',
      '测试数据覆盖制单客户下单、取号、面单、扣费、轨迹和对账。'
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

insert into permissions (tenant_id, code, name, resource, action, description)
select t.id, v.code, v.name, v.resource, v.action, v.description
from tenants t
cross join (
  values
    ('flow.seller.read', '查看卖货流程', 'flow.seller', 'read', '查看卖货客户履约流程和数据'),
    ('flow.seller.write', '维护卖货流程', 'flow.seller', 'write', '创建和维护卖货客户订单、仓库、出货和账单'),
    ('flow.document.read', '查看制单流程', 'flow.document', 'read', '查看制单客户订单、面单、轨迹和账单'),
    ('flow.document.write', '维护制单流程', 'flow.document', 'write', '创建和维护制单、面单、扣费和状态回传'),
    ('business.flow.read', '查看业务流程', 'business.flow', 'read', '查看两套客户流程配置'),
    ('business.flow.write', '维护业务流程', 'business.flow', 'write', '维护两套客户流程配置')
) as v(code, name, resource, action, description)
where t.code = 'xqt'
on conflict (tenant_id, code) do update
set name = excluded.name,
    resource = excluded.resource,
    action = excluded.action,
    description = excluded.description,
    status = 'ACTIVE';

insert into role_permissions (tenant_id, role_id, permission_id)
select r.tenant_id, r.id, p.id
from roles r
join permissions p on p.tenant_id = r.tenant_id
where r.code = 'ADMIN'
on conflict do nothing;

insert into role_permissions (tenant_id, role_id, permission_id)
select r.tenant_id, r.id, p.id
from roles r
join permissions p on p.tenant_id = r.tenant_id
where r.code = 'OPERATOR'
  and p.code in (
    'flow.seller.read',
    'flow.seller.write',
    'flow.document.read',
    'flow.document.write',
    'business.flow.read'
  )
on conflict do nothing;

do $$
declare
  v_tenant uuid;
  v_admin uuid;
  v_seller_customer uuid;
  v_document_customer uuid;
  v_seller_service uuid;
  v_document_service uuid;
  v_seller_order uuid;
  v_document_order uuid;
  v_seller_shipment uuid;
  v_document_shipment uuid;
  v_seller_carton uuid;
  v_document_carton uuid;
  v_freight_item uuid;
  v_fuel_item uuid;
  v_ltl_item uuid;
  v_partner uuid;
  v_account uuid;
  v_seller_ar_charge uuid;
  v_seller_ap_charge uuid;
  v_document_ar_charge uuid;
  v_seller_invoice uuid;
  v_document_invoice uuid;
  v_partner_invoice uuid;
begin
  select id into v_tenant from tenants where code = 'xqt';
  select id into v_admin
  from users
  where tenant_id = v_tenant
    and username = 'admin'
  order by created_at
  limit 1;

  if v_tenant is null then
    raise exception 'tenant xqt is required before loading demo data';
  end if;

  insert into customers (
    tenant_id, code, name, account_mode, default_currency, credit_limit,
    customer_direction, service_modes, source, status, created_by, updated_by
  )
  values
    (
      v_tenant,
      'SELLER-DEMO',
      '卖货流程样例客户',
      'MONTHLY',
      'CNY',
      200000,
      'SELLER_CUSTOMER',
      '["SELLER_FULFILLMENT"]',
      'LOCAL',
      'ACTIVE',
      v_admin,
      v_admin
    ),
    (
      v_tenant,
      'DOC-DEMO',
      '制单流程样例客户',
      'PREPAID',
      'CNY',
      50000,
      'DOCUMENT_CUSTOMER',
      '["DOCUMENT_SHIPPING"]',
      'LOCAL',
      'ACTIVE',
      v_admin,
      v_admin
    )
  on conflict (tenant_id, code) do update
  set name = excluded.name,
      account_mode = excluded.account_mode,
      default_currency = excluded.default_currency,
      credit_limit = excluded.credit_limit,
      customer_direction = excluded.customer_direction,
      service_modes = excluded.service_modes,
      status = 'ACTIVE',
      updated_by = excluded.updated_by;

  select id into v_seller_customer
  from customers
  where tenant_id = v_tenant and code = 'SELLER-DEMO';

  select id into v_document_customer
  from customers
  where tenant_id = v_tenant and code = 'DOC-DEMO';

  insert into customer_contacts (tenant_id, customer_id, name, role, phone, email, wechat, is_primary)
  select v_tenant, v_seller_customer, '王测试', '仓配对接', '13800000001', 'seller-demo@xqt.local', 'seller-demo', true
  where not exists (
    select 1 from customer_contacts
    where tenant_id = v_tenant and customer_id = v_seller_customer and email = 'seller-demo@xqt.local'
  );

  insert into customer_contacts (tenant_id, customer_id, name, role, phone, email, wechat, is_primary)
  select v_tenant, v_document_customer, '李测试', '制单对接', '13800000002', 'doc-demo@xqt.local', 'doc-demo', true
  where not exists (
    select 1 from customer_contacts
    where tenant_id = v_tenant and customer_id = v_document_customer and email = 'doc-demo@xqt.local'
  );

  insert into customer_settlement_profiles (
    tenant_id, customer_id, pay_type, credit_days, invoice_confirm_required,
    due_actions, tax_required, statement_template_code, effective_from
  )
  values
    (
      v_tenant,
      v_seller_customer,
      'MONTHLY',
      30,
      true,
      '["statement_confirm","invoice_issue","reconcile_profit"]',
      true,
      'CHANNEL_SUMMARY',
      date '2026-05-01'
    ),
    (
      v_tenant,
      v_document_customer,
      'PREPAID',
      0,
      false,
      '["balance_deduct","label_create","statement_export"]',
      false,
      'STANDARD_TOTAL',
      date '2026-05-01'
    )
  on conflict (tenant_id, customer_id, effective_from) do update
  set pay_type = excluded.pay_type,
      credit_days = excluded.credit_days,
      invoice_confirm_required = excluded.invoice_confirm_required,
      due_actions = excluded.due_actions,
      tax_required = excluded.tax_required,
      statement_template_code = excluded.statement_template_code;

  insert into customer_accounts (tenant_id, customer_id, username, password_hash, status)
  values
    (
      v_tenant,
      v_seller_customer,
      'seller-demo',
      'pbkdf2$sha256$210000$xqt-dev-admin-salt-2026$TCDjVY6HTDD_BemIdNIORU5HMaR7GIRot6oTcid2MYA',
      'ACTIVE'
    ),
    (
      v_tenant,
      v_document_customer,
      'doc-demo',
      'pbkdf2$sha256$210000$xqt-dev-admin-salt-2026$TCDjVY6HTDD_BemIdNIORU5HMaR7GIRot6oTcid2MYA',
      'ACTIVE'
    )
  on conflict (tenant_id, username) do update
  set customer_id = excluded.customer_id,
      password_hash = excluded.password_hash,
      status = 'ACTIVE';

  insert into api_credentials (tenant_id, owner_type, owner_id, access_key, secret_hash, status, scopes)
  values (
    v_tenant,
    'CUSTOMER',
    v_document_customer,
    'ak_doc_demo_202605',
    'pbkdf2$sha256$210000$xqt-dev-api-salt-2026$6SMh-demo-secret-hash-for-local-test',
    'ACTIVE',
    '["order:create","label:create","track:read","statement:read"]'
  )
  on conflict (tenant_id, access_key) do update
  set owner_id = excluded.owner_id,
      status = 'ACTIVE',
      scopes = excluded.scopes;

  insert into services (tenant_id, code, name, service_type, channel_id, default_currency, status, metadata)
  select v_tenant,
         'SVC-US-GROUND',
         '美国本土派送 FedEx Ground',
         'EXPRESS',
         ch.id,
         'CNY',
         'ACTIVE',
         '{"flow":"SELLER_FULFILLMENT","demo":true}'::jsonb
  from channels ch
  where ch.tenant_id = v_tenant and ch.code = 'US-GROUND-FEDEX'
  on conflict (tenant_id, code) do update
  set name = excluded.name,
      channel_id = excluded.channel_id,
      status = 'ACTIVE',
      metadata = excluded.metadata
  returning id into v_seller_service;

  insert into services (tenant_id, code, name, service_type, channel_id, default_currency, status, metadata)
  select v_tenant,
         'SVC-EU-AIR',
         '欧洲空派 UPS 制单',
         'EXPRESS',
         ch.id,
         'CNY',
         'ACTIVE',
         '{"flow":"DOCUMENT_SHIPPING","demo":true}'::jsonb
  from channels ch
  where ch.tenant_id = v_tenant and ch.code = 'EU-AIR-UPS'
  on conflict (tenant_id, code) do update
  set name = excluded.name,
      channel_id = excluded.channel_id,
      status = 'ACTIVE',
      metadata = excluded.metadata
  returning id into v_document_service;

  insert into warehouses (
    tenant_id, code, name, warehouse_type, country_code, province, city, address, status, metadata
  )
  values
    (
      v_tenant,
      'SZ-01',
      '深圳集货仓',
      'DOMESTIC',
      'CN',
      '广东',
      '深圳',
      '宝安区测试路 100 号',
      'ACTIVE',
      '{"demo":true,"flow":"SELLER_FULFILLMENT"}'
    ),
    (
      v_tenant,
      'LA-01',
      '洛杉矶海外仓',
      'OVERSEAS',
      'US',
      'CA',
      'Los Angeles',
      '100 Demo Warehouse Ave',
      'ACTIVE',
      '{"demo":true,"flow":"SELLER_FULFILLMENT"}'
    )
  on conflict (tenant_id, code) do update
  set name = excluded.name,
      warehouse_type = excluded.warehouse_type,
      country_code = excluded.country_code,
      province = excluded.province,
      city = excluded.city,
      address = excluded.address,
      status = 'ACTIVE',
      metadata = excluded.metadata;

  insert into tags (tenant_id, scope, code, name, color, active)
  values
    (v_tenant, 'ORDER', 'DEMO_SELLER', '卖货流程测试', '#2563eb', true),
    (v_tenant, 'ORDER', 'DEMO_DOCUMENT', '制单流程测试', '#16a34a', true),
    (v_tenant, 'CUSTOMER', 'PREPAID', '预付客户', '#0891b2', true),
    (v_tenant, 'CUSTOMER', 'MONTHLY', '月结客户', '#7c3aed', true)
  on conflict (tenant_id, scope, code) do update
  set name = excluded.name,
      color = excluded.color,
      active = true;

  insert into orders (
    tenant_id, order_no, customer_id, service_id, status, source, customer_ref,
    created_by, updated_by, metadata, customer_direction, order_entry_type, service_mode
  )
  values (
    v_tenant,
    'SO-DEMO-20260508-001',
    v_seller_customer,
    v_seller_service,
    'FULFILLING',
    'LOCAL',
    'SELLER-REF-001',
    v_admin,
    v_admin,
    '{"demo":true,"scenario":"卖货客户订单-入仓后派送","warehouseCode":"SZ-01"}',
    'SELLER_CUSTOMER',
    'SALES_ORDER',
    'SELLER_FULFILLMENT'
  )
  on conflict (tenant_id, order_no) do update
  set customer_id = excluded.customer_id,
      service_id = excluded.service_id,
      status = excluded.status,
      customer_ref = excluded.customer_ref,
      metadata = excluded.metadata,
      customer_direction = excluded.customer_direction,
      order_entry_type = excluded.order_entry_type,
      service_mode = excluded.service_mode,
      updated_by = excluded.updated_by
  returning id into v_seller_order;

  insert into orders (
    tenant_id, order_no, customer_id, service_id, status, source, customer_ref,
    created_by, updated_by, metadata, customer_direction, order_entry_type, service_mode
  )
  values (
    v_tenant,
    'DO-DEMO-20260508-001',
    v_document_customer,
    v_document_service,
    'ACCEPTED',
    'API',
    'DOC-REF-001',
    v_admin,
    v_admin,
    '{"demo":true,"scenario":"制单客户API下单-待取号","labelFormat":"PDF_100X150"}',
    'DOCUMENT_CUSTOMER',
    'API_ORDER',
    'DOCUMENT_SHIPPING'
  )
  on conflict (tenant_id, order_no) do update
  set customer_id = excluded.customer_id,
      service_id = excluded.service_id,
      status = excluded.status,
      source = excluded.source,
      customer_ref = excluded.customer_ref,
      metadata = excluded.metadata,
      customer_direction = excluded.customer_direction,
      order_entry_type = excluded.order_entry_type,
      service_mode = excluded.service_mode,
      updated_by = excluded.updated_by
  returning id into v_document_order;

  insert into order_lines (
    tenant_id, order_id, line_no, item_name, sku, quantity, declared_value,
    declared_currency, weight_kg, metadata, created_by, updated_by
  )
  values
    (
      v_tenant,
      v_seller_order,
      1,
      '智能升降桌',
      'SKU-DESK-140',
      2,
      2580,
      'CNY',
      42.5,
      '{"cartons":2,"demo":true}',
      v_admin,
      v_admin
    ),
    (
      v_tenant,
      v_seller_order,
      2,
      '配件包',
      'SKU-PARTS-01',
      4,
      320,
      'CNY',
      6.8,
      '{"cartons":1,"demo":true}',
      v_admin,
      v_admin
    )
  on conflict (tenant_id, order_id, line_no) do update
  set item_name = excluded.item_name,
      sku = excluded.sku,
      quantity = excluded.quantity,
      declared_value = excluded.declared_value,
      declared_currency = excluded.declared_currency,
      weight_kg = excluded.weight_kg,
      metadata = excluded.metadata,
      updated_by = excluded.updated_by;

  insert into order_lines (
    tenant_id, order_id, line_no, item_name, sku, quantity, declared_value,
    declared_currency, weight_kg, metadata, created_by, updated_by
  )
  values (
    v_tenant,
    v_document_order,
    1,
    '样品服装',
    'SKU-SAMPLE-CLOTH',
    10,
    680,
    'CNY',
    9.2,
    '{"recipientCountry":"DE","demo":true}',
    v_admin,
    v_admin
  )
  on conflict (tenant_id, order_id, line_no) do update
  set item_name = excluded.item_name,
      sku = excluded.sku,
      quantity = excluded.quantity,
      declared_value = excluded.declared_value,
      declared_currency = excluded.declared_currency,
      weight_kg = excluded.weight_kg,
      metadata = excluded.metadata,
      updated_by = excluded.updated_by;

  insert into shipments (
    tenant_id, customer_id, channel_id, shipment_no, customer_ref, status, service_mode,
    destination_country, destination_postal_code, destination_warehouse_code, declared_value,
    declared_currency, insured, ordered_at, warehouse_in_at, measured_at, departed_at, customer_direction
  )
  select v_tenant,
         v_seller_customer,
         ch.id,
         'SHP-DEMO-SELLER-001',
         'SELLER-REF-001',
         'IN_TRANSIT',
         'SELLER_FULFILLMENT',
         'US',
         '91748',
         'LA-01',
         2900,
         'CNY',
         true,
         timestamptz '2026-05-06 09:20:00+08',
         timestamptz '2026-05-06 17:40:00+08',
         timestamptz '2026-05-07 10:10:00+08',
         timestamptz '2026-05-08 08:30:00+08',
         'SELLER_CUSTOMER'
  from channels ch
  where ch.tenant_id = v_tenant and ch.code = 'US-GROUND-FEDEX'
  on conflict (tenant_id, shipment_no) do update
  set customer_id = excluded.customer_id,
      channel_id = excluded.channel_id,
      customer_ref = excluded.customer_ref,
      status = excluded.status,
      service_mode = excluded.service_mode,
      destination_country = excluded.destination_country,
      destination_postal_code = excluded.destination_postal_code,
      destination_warehouse_code = excluded.destination_warehouse_code,
      declared_value = excluded.declared_value,
      declared_currency = excluded.declared_currency,
      insured = excluded.insured,
      customer_direction = excluded.customer_direction
  returning id into v_seller_shipment;

  insert into shipments (
    tenant_id, customer_id, channel_id, shipment_no, customer_ref, status, service_mode,
    destination_country, destination_postal_code, declared_value, declared_currency,
    insured, ordered_at, customer_direction
  )
  select v_tenant,
         v_document_customer,
         ch.id,
         'SHP-DEMO-DOC-001',
         'DOC-REF-001',
         'ORDERED',
         'DOCUMENT_SHIPPING',
         'DE',
         '10115',
         680,
         'CNY',
         false,
         timestamptz '2026-05-08 10:15:00+08',
         'DOCUMENT_CUSTOMER'
  from channels ch
  where ch.tenant_id = v_tenant and ch.code = 'EU-AIR-UPS'
  on conflict (tenant_id, shipment_no) do update
  set customer_id = excluded.customer_id,
      channel_id = excluded.channel_id,
      customer_ref = excluded.customer_ref,
      status = excluded.status,
      service_mode = excluded.service_mode,
      destination_country = excluded.destination_country,
      destination_postal_code = excluded.destination_postal_code,
      declared_value = excluded.declared_value,
      declared_currency = excluded.declared_currency,
      insured = excluded.insured,
      customer_direction = excluded.customer_direction
  returning id into v_document_shipment;

  insert into shipment_order_links (tenant_id, order_id, shipment_id, relation_type)
  values
    (v_tenant, v_seller_order, v_seller_shipment, 'FULFILLMENT'),
    (v_tenant, v_document_order, v_document_shipment, 'FULFILLMENT')
  on conflict do nothing;

  insert into cartons (
    tenant_id, shipment_id, carton_no, tracking_no, carrier_master_tracking_no,
    actual_weight_kg, length_cm, width_cm, height_cm, chargeable_weight_kg,
    chargeable_weight_lb, cbm, oversize_flags
  )
  values (
    v_tenant,
    v_seller_shipment,
    'CTN-DEMO-SELLER-001',
    'FDX-DEMO-SELLER-001',
    'M-FDX-DEMO-001',
    49.3,
    120,
    48,
    42,
    51.0,
    112.44,
    0.2419,
    '{"oversize":true,"reason":"length_gt_100cm"}'
  )
  on conflict (tenant_id, shipment_id, carton_no) do update
  set tracking_no = excluded.tracking_no,
      carrier_master_tracking_no = excluded.carrier_master_tracking_no,
      actual_weight_kg = excluded.actual_weight_kg,
      length_cm = excluded.length_cm,
      width_cm = excluded.width_cm,
      height_cm = excluded.height_cm,
      chargeable_weight_kg = excluded.chargeable_weight_kg,
      chargeable_weight_lb = excluded.chargeable_weight_lb,
      cbm = excluded.cbm,
      oversize_flags = excluded.oversize_flags
  returning id into v_seller_carton;

  insert into cartons (
    tenant_id, shipment_id, carton_no, tracking_no, carrier_master_tracking_no,
    actual_weight_kg, length_cm, width_cm, height_cm, chargeable_weight_kg,
    chargeable_weight_lb, cbm, oversize_flags
  )
  values (
    v_tenant,
    v_document_shipment,
    'CTN-DEMO-DOC-001',
    '1Z-DEMO-DOC-001',
    'M-UPS-DEMO-001',
    9.2,
    45,
    36,
    28,
    9.2,
    20.28,
    0.0454,
    '{}'
  )
  on conflict (tenant_id, shipment_id, carton_no) do update
  set tracking_no = excluded.tracking_no,
      carrier_master_tracking_no = excluded.carrier_master_tracking_no,
      actual_weight_kg = excluded.actual_weight_kg,
      length_cm = excluded.length_cm,
      width_cm = excluded.width_cm,
      height_cm = excluded.height_cm,
      chargeable_weight_kg = excluded.chargeable_weight_kg,
      chargeable_weight_lb = excluded.chargeable_weight_lb,
      cbm = excluded.cbm,
      oversize_flags = excluded.oversize_flags
  returning id into v_document_carton;

  insert into declarations (tenant_id, shipment_id, item_name, material, hs_code, quantity, value_amount, attributes)
  select v_tenant, v_seller_shipment, '智能升降桌', 'metal/wood', '940320', 2, 2580, '{"demo":true}'
  where not exists (
    select 1 from declarations
    where tenant_id = v_tenant and shipment_id = v_seller_shipment and item_name = '智能升降桌'
  );

  insert into declarations (tenant_id, shipment_id, item_name, material, hs_code, quantity, value_amount, attributes)
  select v_tenant, v_document_shipment, '样品服装', 'cotton', '610910', 10, 680, '{"demo":true}'
  where not exists (
    select 1 from declarations
    where tenant_id = v_tenant and shipment_id = v_document_shipment and item_name = '样品服装'
  );

  insert into tracking_events (
    tenant_id, shipment_id, carton_id, carrier_id, tracking_no, event_time, raw_status,
    normalized_status, location, source, raw_payload
  )
  select v_tenant,
         v_seller_shipment,
         v_seller_carton,
         c.id,
         'FDX-DEMO-SELLER-001',
         timestamptz '2026-05-08 09:10:00+08',
         'Departed facility',
         'IN_TRANSIT',
         'Shenzhen',
         'MANUAL',
         '{"demo":true}'
  from carriers c
  where c.tenant_id = v_tenant and c.code = 'FEDEX'
    and not exists (
      select 1 from tracking_events
      where tenant_id = v_tenant
        and tracking_no = 'FDX-DEMO-SELLER-001'
        and event_time = timestamptz '2026-05-08 09:10:00+08'
    );

  insert into tracking_events (
    tenant_id, shipment_id, carton_id, carrier_id, tracking_no, event_time, raw_status,
    normalized_status, location, source, raw_payload
  )
  select v_tenant,
         v_document_shipment,
         v_document_carton,
         c.id,
         '1Z-DEMO-DOC-001',
         timestamptz '2026-05-08 10:20:00+08',
         'Label created',
         'CREATED',
         'Shenzhen',
         'MANUAL',
         '{"demo":true,"labelUrl":"/labels/1Z-DEMO-DOC-001.pdf"}'
  from carriers c
  where c.tenant_id = v_tenant and c.code = 'UPS'
    and not exists (
      select 1 from tracking_events
      where tenant_id = v_tenant
        and tracking_no = '1Z-DEMO-DOC-001'
        and event_time = timestamptz '2026-05-08 10:20:00+08'
    );

  select id into v_freight_item from charge_items where tenant_id = v_tenant and code = 'FREIGHT';
  select id into v_fuel_item from charge_items where tenant_id = v_tenant and code = 'FUEL';
  select id into v_ltl_item from charge_items where tenant_id = v_tenant and code = 'LTL_DELIVERY';

  insert into charges (
    tenant_id, shipment_id, carton_id, charge_item_id, side, status, currency,
    quantity, unit_price, amount, rule_snapshot, evidence
  )
  select v_tenant,
         v_seller_shipment,
         v_seller_carton,
         v_freight_item,
         'AR',
         'LOCKED',
         'CNY',
         51.0,
         18.5,
         943.50,
         '{"rule":"demo-freight-us-ground","uom":"KG"}',
         '{"demo":true,"demoKey":"SELLER_AR_FREIGHT"}'
  where not exists (
    select 1 from charges
    where tenant_id = v_tenant
      and shipment_id = v_seller_shipment
      and evidence ->> 'demoKey' = 'SELLER_AR_FREIGHT'
  );

  select id into v_seller_ar_charge
  from charges
  where tenant_id = v_tenant
    and shipment_id = v_seller_shipment
    and evidence ->> 'demoKey' = 'SELLER_AR_FREIGHT'
  limit 1;

  insert into charges (
    tenant_id, shipment_id, carton_id, charge_item_id, side, status, currency,
    quantity, unit_price, amount, rule_snapshot, evidence
  )
  select v_tenant,
         v_seller_shipment,
         v_seller_carton,
         v_ltl_item,
         'AP',
         'LOCKED',
         'CNY',
         51.0,
         11.2,
         571.20,
         '{"rule":"demo-fedex-cost","uom":"KG"}',
         '{"demo":true,"demoKey":"SELLER_AP_DELIVERY"}'
  where not exists (
    select 1 from charges
    where tenant_id = v_tenant
      and shipment_id = v_seller_shipment
      and evidence ->> 'demoKey' = 'SELLER_AP_DELIVERY'
  );

  select id into v_seller_ap_charge
  from charges
  where tenant_id = v_tenant
    and shipment_id = v_seller_shipment
    and evidence ->> 'demoKey' = 'SELLER_AP_DELIVERY'
  limit 1;

  insert into charges (
    tenant_id, shipment_id, carton_id, charge_item_id, side, status, currency,
    quantity, unit_price, amount, rule_snapshot, evidence
  )
  select v_tenant,
         v_document_shipment,
         v_document_carton,
         v_freight_item,
         'AR',
         'ESTIMATED',
         'CNY',
         9.2,
         32.0,
         294.40,
         '{"rule":"demo-eu-air-document","uom":"KG"}',
         '{"demo":true,"demoKey":"DOCUMENT_AR_FREIGHT"}'
  where not exists (
    select 1 from charges
    where tenant_id = v_tenant
      and shipment_id = v_document_shipment
      and evidence ->> 'demoKey' = 'DOCUMENT_AR_FREIGHT'
  );

  select id into v_document_ar_charge
  from charges
  where tenant_id = v_tenant
    and shipment_id = v_document_shipment
    and evidence ->> 'demoKey' = 'DOCUMENT_AR_FREIGHT'
  limit 1;

  insert into customer_invoices (
    tenant_id, customer_id, invoice_no, template_code, currency, total_amount, status,
    invoice_date, due_date, paid_amount, unpaid_amount, source, metadata
  )
  values (
    v_tenant,
    v_seller_customer,
    'AR-DEMO-SELLER-202605',
    'CHANNEL_SUMMARY',
    'CNY',
    943.50,
    'CONFIRMED',
    timestamptz '2026-05-08 11:00:00+08',
    timestamptz '2026-06-07 23:59:59+08',
    0,
    943.50,
    'LOCAL',
    '{"demo":true,"flow":"SELLER_FULFILLMENT"}'
  )
  on conflict (tenant_id, invoice_no) do update
  set customer_id = excluded.customer_id,
      template_code = excluded.template_code,
      total_amount = excluded.total_amount,
      status = excluded.status,
      unpaid_amount = excluded.unpaid_amount,
      metadata = excluded.metadata
  returning id into v_seller_invoice;

  insert into customer_invoices (
    tenant_id, customer_id, invoice_no, template_code, currency, total_amount, status,
    invoice_date, due_date, paid_amount, unpaid_amount, source, metadata
  )
  values (
    v_tenant,
    v_document_customer,
    'AR-DEMO-DOC-202605',
    'STANDARD_TOTAL',
    'CNY',
    294.40,
    'DRAFT',
    timestamptz '2026-05-08 11:10:00+08',
    timestamptz '2026-05-08 23:59:59+08',
    0,
    294.40,
    'LOCAL',
    '{"demo":true,"flow":"DOCUMENT_SHIPPING"}'
  )
  on conflict (tenant_id, invoice_no) do update
  set customer_id = excluded.customer_id,
      template_code = excluded.template_code,
      total_amount = excluded.total_amount,
      status = excluded.status,
      unpaid_amount = excluded.unpaid_amount,
      metadata = excluded.metadata
  returning id into v_document_invoice;

  insert into customer_invoice_lines (tenant_id, invoice_id, shipment_id, charge_id, amount)
  select v_tenant, v_seller_invoice, v_seller_shipment, v_seller_ar_charge, 943.50
  where not exists (
    select 1 from customer_invoice_lines
    where tenant_id = v_tenant and invoice_id = v_seller_invoice and charge_id = v_seller_ar_charge
  );

  insert into customer_invoice_lines (tenant_id, invoice_id, shipment_id, charge_id, amount)
  select v_tenant, v_document_invoice, v_document_shipment, v_document_ar_charge, 294.40
  where not exists (
    select 1 from customer_invoice_lines
    where tenant_id = v_tenant and invoice_id = v_document_invoice and charge_id = v_document_ar_charge
  );

  insert into partners (tenant_id, code, name, partner_type, settlement_currency, carrier_id, status, source, metadata)
  select v_tenant, 'P-FEDEX-DEMO', 'FedEx 成本结算样例', 'CARRIER', 'CNY', c.id, 'ACTIVE', 'LOCAL', '{"demo":true}'
  from carriers c
  where c.tenant_id = v_tenant and c.code = 'FEDEX'
  on conflict (tenant_id, code) do update
  set name = excluded.name,
      carrier_id = excluded.carrier_id,
      status = 'ACTIVE',
      metadata = excluded.metadata
  returning id into v_partner;

  insert into partner_invoices (
    tenant_id, partner_id, invoice_no, currency, total_amount, paid_amount, unpaid_amount,
    status, writeoff_status, invoice_date, due_date, source, metadata, created_by
  )
  values (
    v_tenant,
    v_partner,
    'AP-DEMO-FEDEX-202605',
    'CNY',
    571.20,
    0,
    571.20,
    'CONFIRMED',
    'UNPAID',
    timestamptz '2026-05-08 12:00:00+08',
    timestamptz '2026-05-18 23:59:59+08',
    'LOCAL',
    '{"demo":true,"flow":"SELLER_FULFILLMENT"}',
    v_admin
  )
  on conflict (tenant_id, invoice_no) do update
  set partner_id = excluded.partner_id,
      total_amount = excluded.total_amount,
      unpaid_amount = excluded.unpaid_amount,
      status = excluded.status,
      metadata = excluded.metadata
  returning id into v_partner_invoice;

  insert into partner_invoice_lines (
    tenant_id, invoice_id, shipment_id, carton_id, charge_id, charge_item_id,
    line_no, description, currency, amount, metadata
  )
  select v_tenant,
         v_partner_invoice,
         v_seller_shipment,
         v_seller_carton,
         v_seller_ap_charge,
         v_ltl_item,
         1,
         'FedEx 派送成本',
         'CNY',
         571.20,
         '{"demo":true}'
  where not exists (
    select 1 from partner_invoice_lines
    where tenant_id = v_tenant and invoice_id = v_partner_invoice and charge_id = v_seller_ap_charge
  );

  insert into financial_accounts (
    tenant_id, owner_type, account_name, account_type, bank_name, bank_account_no,
    currency, balance, status, source, metadata
  )
  select v_tenant,
         'COMPANY',
         '新航线测试结算户',
         'BANK',
         '招商银行深圳分行',
         '6222********0001',
         'CNY',
         100000,
         'ACTIVE',
         'LOCAL',
         '{"demo":true}'
  where not exists (
    select 1 from financial_accounts
    where tenant_id = v_tenant and account_name = '新航线测试结算户'
  );

  select id into v_account
  from financial_accounts
  where tenant_id = v_tenant and account_name = '新航线测试结算户'
  limit 1;

  insert into financial_account_records (
    tenant_id, account_id, record_no, direction, currency, amount, balance_after,
    payment_type, business_time, source_type, source_id, related_entity_type,
    related_entity_id, audited_status, audited_at, audited_by, description,
    source, raw_payload, created_by
  )
  select v_tenant,
         v_account,
         'BANK-DEMO-IN-20260508-001',
         'IN',
         'CNY',
         5000,
         105000,
         'BANK_TRANSFER',
         timestamptz '2026-05-08 15:00:00+08',
         'CUSTOMER_ADVANCE',
         v_document_customer,
         'CUSTOMER',
         v_document_customer,
         'PASSED',
         timestamptz '2026-05-08 15:05:00+08',
         v_admin,
         '制单客户测试预充值',
         'LOCAL',
         '{"demo":true}',
         v_admin
  where not exists (
    select 1 from financial_account_records
    where tenant_id = v_tenant and record_no = 'BANK-DEMO-IN-20260508-001'
  );

  insert into shipment_charge_snapshots (
    tenant_id, shipment_id, ar_total, ap_total, seller_cost_total,
    commission_total, gross_profit, currency, audited_status, metadata
  )
  values (
    v_tenant,
    v_seller_shipment,
    943.50,
    571.20,
    571.20,
    37.23,
    335.07,
    'CNY',
    'PARTIAL',
    '{"demo":true,"formula":"ar-ap-commission"}'
  )
  on conflict (tenant_id, shipment_id) do update
  set ar_total = excluded.ar_total,
      ap_total = excluded.ap_total,
      seller_cost_total = excluded.seller_cost_total,
      commission_total = excluded.commission_total,
      gross_profit = excluded.gross_profit,
      audited_status = excluded.audited_status,
      metadata = excluded.metadata;

  insert into profit_snapshots (
    tenant_id, shipment_id, ar_amount, ap_amount, seller_cost_amount,
    commission_amount, gross_profit, currency, metadata
  )
  select v_tenant,
         v_seller_shipment,
         943.50,
         571.20,
         571.20,
         37.23,
         335.07,
         'CNY',
         '{"demo":true}'
  where not exists (
    select 1 from profit_snapshots
    where tenant_id = v_tenant
      and shipment_id = v_seller_shipment
      and metadata ->> 'demo' = 'true'
  );

  insert into audit_logs (
    tenant_id, actor_id, entity_type, entity_id, action, before_data, after_data, request_id, metadata
  )
  select v_tenant,
         v_admin,
         'demo_data',
         v_tenant,
         'SEED',
         '{}',
         '{"sellerOrder":"SO-DEMO-20260508-001","documentOrder":"DO-DEMO-20260508-001"}',
         'demo-seed-20260508',
         '{"demo":true}'
  where not exists (
    select 1 from audit_logs
    where tenant_id = v_tenant
      and entity_type = 'demo_data'
      and request_id = 'demo-seed-20260508'
  );
end $$;

select set_config('app.service_role', '', false);
