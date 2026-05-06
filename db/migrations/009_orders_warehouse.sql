-- 009: 订单、仓配和扫描履约

create table orders (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  order_no text not null,
  customer_id uuid not null references customers(id),
  service_id uuid references services(id),
  status text not null default 'DRAFT'
    check (status in ('DRAFT', 'SUBMITTED', 'ACCEPTED', 'FULFILLING', 'COMPLETED', 'CANCELLED', 'EXCEPTION')),
  source text not null default 'LOCAL'
    check (source in ('ACC', 'XQT', 'LOCAL', 'API', 'IMPORT')),
  external_id text,
  customer_ref text,
  branch_id uuid references organizations(id),
  seller_id uuid references users(id),
  servicer_id uuid references users(id),
  created_by uuid references users(id),
  submitted_at timestamptz,
  accepted_at timestamptz,
  completed_at timestamptz,
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now(),
  unique (tenant_id, order_no)
);

create table order_lines (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  order_id uuid not null references orders(id) on delete cascade,
  line_no int not null default 1,
  item_name text not null,
  sku text,
  quantity numeric(14,4) not null default 1,
  declared_value numeric(14,2),
  declared_currency char(3),
  weight_kg numeric(12,3),
  metadata jsonb not null default '{}',
  unique (tenant_id, order_id, line_no)
);

create table shipment_order_links (
  tenant_id uuid not null references tenants(id),
  order_id uuid not null references orders(id) on delete cascade,
  shipment_id uuid not null references shipments(id) on delete cascade,
  relation_type text not null default 'FULFILLMENT'
    check (relation_type in ('FULFILLMENT', 'SPLIT', 'MERGE', 'REPLACEMENT')),
  created_at timestamptz not null default now(),
  primary key (order_id, shipment_id, relation_type)
);

create table warehouses (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  warehouse_type text not null default 'DOMESTIC'
    check (warehouse_type in ('DOMESTIC', 'OVERSEAS', 'TRANSIT', 'VIRTUAL')),
  country_code char(2),
  province text,
  city text,
  address text,
  branch_id uuid references organizations(id),
  manager_id uuid references users(id),
  status text not null default 'ACTIVE'
    check (status in ('ACTIVE', 'DISABLED', 'ARCHIVED')),
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);

create table addresses (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  owner_type text not null
    check (owner_type in ('CUSTOMER', 'PARTNER', 'WAREHOUSE', 'SHIPMENT', 'ORDER')),
  owner_id uuid,
  address_type text not null default 'GENERAL'
    check (address_type in ('GENERAL', 'SENDER', 'RECIPIENT', 'BILLING', 'WAREHOUSE')),
  country_code char(2),
  province text,
  city text,
  district text,
  postal_code text,
  address1 text not null,
  address2 text,
  contact_name text,
  phone text,
  email text,
  is_default boolean not null default false,
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now()
);

create table warehouse_receipts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  receipt_no text not null,
  warehouse_id uuid not null references warehouses(id),
  customer_id uuid references customers(id),
  order_id uuid references orders(id),
  shipment_id uuid references shipments(id),
  status text not null default 'OPEN'
    check (status in ('OPEN', 'RECEIVING', 'RECEIVED', 'CANCELLED', 'EXCEPTION')),
  received_at timestamptz,
  received_by uuid references users(id),
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now(),
  unique (tenant_id, receipt_no)
);

create table warehouse_receipt_items (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  receipt_id uuid not null references warehouse_receipts(id) on delete cascade,
  carton_id uuid references cartons(id),
  tracking_no text,
  scan_status text not null default 'PENDING'
    check (scan_status in ('PENDING', 'SCANNED', 'DAMAGED', 'MISSING', 'EXTRA')),
  weight_kg numeric(12,3),
  length_cm numeric(10,2),
  width_cm numeric(10,2),
  height_cm numeric(10,2),
  scanned_at timestamptz,
  scanned_by uuid references users(id),
  metadata jsonb not null default '{}'
);

create table picklists (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  picklist_no text not null,
  warehouse_id uuid not null references warehouses(id),
  status text not null default 'OPEN'
    check (status in ('OPEN', 'PICKING', 'PICKED', 'CANCELLED', 'EXCEPTION')),
  assigned_to uuid references users(id),
  picked_at timestamptz,
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now(),
  unique (tenant_id, picklist_no)
);

create table picklist_items (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  picklist_id uuid not null references picklists(id) on delete cascade,
  shipment_id uuid references shipments(id),
  carton_id uuid references cartons(id),
  status text not null default 'PENDING'
    check (status in ('PENDING', 'PICKED', 'SHORT', 'CANCELLED')),
  picked_at timestamptz,
  picked_by uuid references users(id),
  metadata jsonb not null default '{}'
);

create table ladings (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  lading_no text not null,
  warehouse_id uuid references warehouses(id),
  ship_batch_id uuid references ship_batches(id),
  status text not null default 'DRAFT'
    check (status in ('DRAFT', 'LOADING', 'LOADED', 'DEPARTED', 'CANCELLED')),
  loaded_at timestamptz,
  departed_at timestamptz,
  loaded_by uuid references users(id),
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now(),
  unique (tenant_id, lading_no)
);

create table lading_items (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  lading_id uuid not null references ladings(id) on delete cascade,
  shipment_id uuid references shipments(id),
  carton_id uuid references cartons(id),
  loaded_weight_kg numeric(12,3),
  loaded_cbm numeric(12,4),
  status text not null default 'PENDING'
    check (status in ('PENDING', 'LOADED', 'REMOVED')),
  metadata jsonb not null default '{}'
);

create table pallets (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  pallet_no text not null,
  warehouse_id uuid references warehouses(id),
  status text not null default 'OPEN'
    check (status in ('OPEN', 'SEALED', 'LOADED', 'CANCELLED')),
  weight_kg numeric(12,3),
  cbm numeric(12,4),
  sealed_at timestamptz,
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now(),
  unique (tenant_id, pallet_no)
);

create table pallet_items (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  pallet_id uuid not null references pallets(id) on delete cascade,
  shipment_id uuid references shipments(id),
  carton_id uuid references cartons(id),
  tracking_no text,
  added_at timestamptz not null default now(),
  added_by uuid references users(id),
  metadata jsonb not null default '{}'
);

create table scan_events (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  warehouse_id uuid references warehouses(id),
  shipment_id uuid references shipments(id),
  carton_id uuid references cartons(id),
  tracking_no text,
  scan_type text not null,
  scan_result text not null default 'OK'
    check (scan_result in ('OK', 'WARNING', 'ERROR')),
  scanned_at timestamptz not null default now(),
  operator_id uuid references users(id),
  device_code text,
  raw_payload jsonb not null default '{}'
);

create index idx_orders_customer_status on orders(tenant_id, customer_id, status);
create index idx_orders_source on orders(tenant_id, source, external_id);
create index idx_order_lines_order on order_lines(tenant_id, order_id);
create index idx_shipment_order_links_shipment on shipment_order_links(tenant_id, shipment_id);
create index idx_warehouses_status on warehouses(tenant_id, status);
create index idx_addresses_owner on addresses(tenant_id, owner_type, owner_id);
create index idx_warehouse_receipts_lookup on warehouse_receipts(tenant_id, warehouse_id, status, received_at);
create index idx_receipt_items_tracking on warehouse_receipt_items(tenant_id, tracking_no);
create index idx_picklists_lookup on picklists(tenant_id, warehouse_id, status);
create index idx_picklist_items_shipment on picklist_items(tenant_id, shipment_id, carton_id);
create index idx_ladings_batch on ladings(tenant_id, ship_batch_id, status);
create index idx_lading_items_shipment on lading_items(tenant_id, shipment_id, carton_id);
create index idx_pallets_lookup on pallets(tenant_id, warehouse_id, status);
create index idx_pallet_items_tracking on pallet_items(tenant_id, tracking_no);
create index idx_scan_events_tracking on scan_events(tenant_id, tracking_no, scanned_at);
create index idx_scan_events_shipment on scan_events(tenant_id, shipment_id, carton_id, scanned_at);

alter table orders enable row level security;
alter table orders force row level security;
create policy orders_tenant_isolation on orders
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table order_lines enable row level security;
alter table order_lines force row level security;
create policy order_lines_tenant_isolation on order_lines
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table shipment_order_links enable row level security;
alter table shipment_order_links force row level security;
create policy shipment_order_links_tenant_isolation on shipment_order_links
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table warehouses enable row level security;
alter table warehouses force row level security;
create policy warehouses_tenant_isolation on warehouses
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table addresses enable row level security;
alter table addresses force row level security;
create policy addresses_tenant_isolation on addresses
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table warehouse_receipts enable row level security;
alter table warehouse_receipts force row level security;
create policy warehouse_receipts_tenant_isolation on warehouse_receipts
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table warehouse_receipt_items enable row level security;
alter table warehouse_receipt_items force row level security;
create policy warehouse_receipt_items_tenant_isolation on warehouse_receipt_items
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table picklists enable row level security;
alter table picklists force row level security;
create policy picklists_tenant_isolation on picklists
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table picklist_items enable row level security;
alter table picklist_items force row level security;
create policy picklist_items_tenant_isolation on picklist_items
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table ladings enable row level security;
alter table ladings force row level security;
create policy ladings_tenant_isolation on ladings
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table lading_items enable row level security;
alter table lading_items force row level security;
create policy lading_items_tenant_isolation on lading_items
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table pallets enable row level security;
alter table pallets force row level security;
create policy pallets_tenant_isolation on pallets
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table pallet_items enable row level security;
alter table pallet_items force row level security;
create policy pallet_items_tenant_isolation on pallet_items
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table scan_events enable row level security;
alter table scan_events force row level security;
create policy scan_events_tenant_isolation on scan_events
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));
