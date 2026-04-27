create extension if not exists pgcrypto;

create type account_mode as enum ('PREPAID', 'MONTHLY');
create type shipment_status as enum ('DRAFT', 'ORDERED', 'IN_WAREHOUSE', 'MEASURED', 'BOOKED', 'IN_TRANSIT', 'DELIVERED', 'CLOSED', 'EXCEPTION');
create type charge_side as enum ('AR', 'AP');
create type charge_status as enum ('DRAFT', 'ESTIMATED', 'LOCKED', 'ADJUSTED', 'VOID');
create type billing_uom as enum ('KG', 'LB', 'CBM', 'PIECE', 'SHIPMENT', 'CARTON', 'PERCENT');
create type combination_strategy as enum ('STACK', 'MAX');
create type remote_level as enum ('NONE', 'REMOTE', 'SUPER_REMOTE', 'EMBARGO');
create type bill_import_status as enum ('UPLOADED', 'MAPPED', 'MATCHED', 'REVIEWING', 'POSTED', 'REJECTED');
create type reconcile_status as enum ('MATCHED', 'DIFFERENT', 'UNMATCHED', 'PENDING_REVIEW');
create type difference_type as enum ('WEIGHT_DIFF', 'PRICE_DIFF', 'SURCHARGE_DIFF', 'MISSING_CHARGE', 'OVER_CHARGE', 'REFERENCE_MISSING');

create table tenants (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  name text not null,
  created_at timestamptz not null default now()
);

create table users (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  email text not null,
  display_name text not null,
  role_code text not null,
  created_at timestamptz not null default now(),
  unique (tenant_id, email)
);

create table customers (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  account_mode account_mode not null default 'MONTHLY',
  default_currency char(3) not null default 'CNY',
  credit_limit numeric(14,2),
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);

create table carriers (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  carrier_type text not null,
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);

create table channels (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  lane text not null,
  last_mile_method text not null,
  primary_uom billing_uom not null,
  dim_factor numeric(10,2),
  dim_split_ratio numeric(6,4) default 0,
  min_weight_per_carton numeric(10,3),
  fuel_required boolean not null default true,
  active boolean not null default true,
  unique (tenant_id, code)
);

create table contracts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  customer_id uuid not null references customers(id),
  code text not null,
  name text not null,
  currency char(3) not null default 'CNY',
  exchange_formula text,
  effective_from date not null,
  effective_to date,
  status text not null default 'ACTIVE',
  unique (tenant_id, code)
);

create table rate_cards (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  contract_id uuid references contracts(id),
  channel_id uuid not null references channels(id),
  side charge_side not null,
  version text not null,
  effective_from date not null,
  effective_to date,
  currency char(3) not null,
  status text not null default 'DRAFT',
  unique (tenant_id, channel_id, side, version)
);

create table rate_card_lines (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  rate_card_id uuid not null references rate_cards(id) on delete cascade,
  zone_code text,
  warehouse_code text,
  weight_from numeric(12,3),
  weight_to numeric(12,3),
  uom billing_uom not null,
  unit_price numeric(14,4) not null,
  min_amount numeric(14,2),
  metadata jsonb not null default '{}'
);

create table charge_items (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  category text not null,
  default_side charge_side not null,
  default_uom billing_uom not null,
  unique (tenant_id, code)
);

create table rule_versions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  effective_from date not null,
  effective_to date,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);

create table charge_rules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  rule_version_id uuid not null references rule_versions(id),
  charge_item_id uuid not null references charge_items(id),
  channel_id uuid references channels(id),
  name text not null,
  side charge_side not null,
  uom billing_uom not null,
  unit_price numeric(14,4) not null default 0,
  fixed_amount numeric(14,2),
  percentage_rate numeric(8,5),
  combination_group text not null default 'DEFAULT',
  combination_strategy combination_strategy not null default 'STACK',
  scope text not null default 'SHIPMENT',
  priority int not null default 100,
  condition_json jsonb not null default '{}',
  active boolean not null default true
);

create table fuel_surcharge_rates (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  channel_id uuid not null references channels(id),
  year_month char(7) not null,
  rate numeric(8,5) not null,
  source text,
  unique (tenant_id, channel_id, year_month)
);

create table remote_zones (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  channel_id uuid not null references channels(id),
  version text not null,
  country_code char(2),
  postal_code_pattern text,
  fba_code text,
  level remote_level not null,
  effective_from date not null,
  unique (tenant_id, channel_id, version, postal_code_pattern, fba_code)
);

create table shipments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  customer_id uuid not null references customers(id),
  contract_id uuid references contracts(id),
  channel_id uuid references channels(id),
  shipment_no text not null,
  customer_ref text,
  status shipment_status not null default 'DRAFT',
  service_mode text not null default 'CARGO',
  destination_country char(2),
  destination_postal_code text,
  destination_warehouse_code text,
  remote_level remote_level not null default 'NONE',
  declared_value numeric(14,2),
  declared_currency char(3),
  insured boolean not null default false,
  ordered_at timestamptz,
  warehouse_in_at timestamptz,
  measured_at timestamptz,
  departed_at timestamptz,
  delivered_at timestamptz,
  created_at timestamptz not null default now(),
  unique (tenant_id, shipment_no)
);

create table cartons (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  shipment_id uuid not null references shipments(id) on delete cascade,
  carton_no text not null,
  tracking_no text,
  carrier_master_tracking_no text,
  actual_weight_kg numeric(10,3) not null default 0,
  length_cm numeric(10,2),
  width_cm numeric(10,2),
  height_cm numeric(10,2),
  chargeable_weight_kg numeric(10,3),
  chargeable_weight_lb numeric(10,3),
  cbm numeric(10,4),
  oversize_flags jsonb not null default '{}',
  unique (tenant_id, shipment_id, carton_no)
);

create table declarations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  shipment_id uuid not null references shipments(id) on delete cascade,
  item_name text not null,
  material text,
  hs_code text,
  quantity numeric(12,3),
  value_amount numeric(14,2),
  attributes jsonb not null default '{}'
);

create table customs_groups (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  group_no text not null,
  allocation_method text not null default 'BY_SHIPMENT_COUNT',
  primary_shipment_id uuid references shipments(id),
  declaration_pages int not null default 1,
  status text not null default 'DRAFT',
  unique (tenant_id, group_no)
);

create table customs_group_shipments (
  tenant_id uuid not null references tenants(id),
  customs_group_id uuid not null references customs_groups(id) on delete cascade,
  shipment_id uuid not null references shipments(id) on delete cascade,
  primary key (customs_group_id, shipment_id)
);

create table charges (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  shipment_id uuid references shipments(id),
  carton_id uuid references cartons(id),
  charge_item_id uuid not null references charge_items(id),
  source_rule_id uuid references charge_rules(id),
  side charge_side not null,
  status charge_status not null default 'DRAFT',
  currency char(3) not null,
  quantity numeric(14,4) not null default 1,
  unit_price numeric(14,4) not null default 0,
  amount numeric(14,2) not null,
  tax_amount numeric(14,2) not null default 0,
  rule_snapshot jsonb not null default '{}',
  evidence jsonb not null default '{}',
  created_at timestamptz not null default now()
);

create table channel_cost_policies (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  channel_id uuid not null references channels(id),
  carrier_id uuid references carriers(id),
  version text not null,
  weight_source text not null,
  price_policy text not null,
  surcharge_policy jsonb not null default '{}',
  effective_from date not null,
  effective_to date,
  unique (tenant_id, channel_id, carrier_id, version)
);

create table delivery_quote_options (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  shipment_id uuid not null references shipments(id),
  carrier_id uuid references carriers(id),
  method text not null,
  estimated_base_amount numeric(14,2) not null default 0,
  estimated_surcharge_amount numeric(14,2) not null default 0,
  estimated_total_amount numeric(14,2) not null default 0,
  risk_flags jsonb not null default '{}',
  selected boolean not null default false,
  acknowledged_by uuid references users(id),
  acknowledged_at timestamptz
);

create table insurance_policies (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  shipment_id uuid not null references shipments(id),
  provider_code text not null,
  insured_value numeric(14,2) not null,
  currency char(3) not null,
  premium_amount numeric(14,2),
  policy_no text,
  policy_url text,
  status text not null default 'DRAFT',
  api_payload jsonb not null default '{}',
  created_at timestamptz not null default now()
);

create table insurance_events (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  policy_id uuid not null references insurance_policies(id),
  event_type text not null,
  payload jsonb not null default '{}',
  created_at timestamptz not null default now()
);

create table carrier_bill_imports (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  carrier_id uuid references carriers(id),
  channel_id uuid references channels(id),
  template_code text,
  file_name text not null,
  status bill_import_status not null default 'UPLOADED',
  uploaded_by uuid references users(id),
  uploaded_at timestamptz not null default now()
);

create table carrier_bill_lines (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  import_id uuid not null references carrier_bill_imports(id) on delete cascade,
  line_no int not null,
  shipment_no text,
  customer_ref text,
  tracking_no text,
  master_tracking_no text,
  shipment_date date,
  recipient_zip text,
  zone_code text,
  rated_weight numeric(12,3),
  currency char(3) not null default 'CNY',
  freight_amount numeric(14,2) not null default 0,
  fuel_amount numeric(14,2) not null default 0,
  surcharge_amount numeric(14,2) not null default 0,
  total_amount numeric(14,2) not null default 0,
  fee_description text,
  raw_data jsonb not null default '{}'
);

create table reconciliation_results (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  bill_line_id uuid not null references carrier_bill_lines(id) on delete cascade,
  shipment_id uuid references shipments(id),
  carton_id uuid references cartons(id),
  expected_charge_id uuid references charges(id),
  status reconcile_status not null,
  difference_type difference_type,
  expected_amount numeric(14,2),
  actual_amount numeric(14,2),
  difference_amount numeric(14,2),
  confidence numeric(5,4) not null default 0,
  handling_status text not null default 'PENDING',
  notes text,
  created_at timestamptz not null default now()
);

create table customer_invoices (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  customer_id uuid not null references customers(id),
  invoice_no text not null,
  template_code text not null default 'STANDARD',
  currency char(3) not null,
  total_amount numeric(14,2) not null default 0,
  status text not null default 'DRAFT',
  version int not null default 1,
  issued_at timestamptz,
  unique (tenant_id, invoice_no)
);

create table customer_invoice_lines (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  invoice_id uuid not null references customer_invoices(id) on delete cascade,
  shipment_id uuid references shipments(id),
  charge_id uuid references charges(id),
  amount numeric(14,2) not null
);

create table payments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  customer_id uuid not null references customers(id),
  currency char(3) not null,
  amount numeric(14,2) not null,
  exchange_rate numeric(12,6),
  received_at timestamptz not null,
  reference_no text
);

create table bls (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  bl_no text not null,
  carrier_id uuid references carriers(id),
  container_no text,
  departed_at timestamptz,
  arrived_at timestamptz,
  unique (tenant_id, bl_no)
);

create table bl_shipments (
  tenant_id uuid not null references tenants(id),
  bl_id uuid not null references bls(id) on delete cascade,
  shipment_id uuid not null references shipments(id) on delete cascade,
  actual_weight_kg numeric(12,3),
  cbm numeric(12,4),
  primary key (bl_id, shipment_id)
);

create table cost_allocation_batches (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  bl_id uuid references bls(id),
  allocation_method text not null,
  source_amount numeric(14,2) not null,
  status text not null default 'DRAFT',
  created_by uuid references users(id),
  created_at timestamptz not null default now()
);

create table commission_plans (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  basis text not null default 'AR_MINUS_SALES_COST',
  rate numeric(8,5) not null,
  effective_from date not null,
  unique (tenant_id, code)
);

create table audit_logs (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  actor_id uuid references users(id),
  entity_type text not null,
  entity_id uuid not null,
  action text not null,
  before_data jsonb,
  after_data jsonb,
  created_at timestamptz not null default now()
);

create index idx_shipments_tenant_customer on shipments(tenant_id, customer_id);
create index idx_cartons_tracking on cartons(tenant_id, tracking_no);
create index idx_charges_shipment_side on charges(tenant_id, shipment_id, side);
create index idx_bill_lines_refs on carrier_bill_lines(tenant_id, tracking_no, master_tracking_no, customer_ref);
create index idx_reconciliation_status on reconciliation_results(tenant_id, status, handling_status);
