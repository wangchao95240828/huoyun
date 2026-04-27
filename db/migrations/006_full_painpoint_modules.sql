create type milestone_source as enum ('MANUAL', 'CARRIER_API', 'EDI', 'IMPORT', 'SYSTEM');
create type tracking_status as enum ('CREATED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'EXCEPTION', 'CLAIMING', 'RETURNED', 'VOID');
create type relation_type as enum ('RETURN', 'RELABEL', 'SPLIT', 'MERGE', 'SUPPLEMENT');
create type ticket_status as enum ('OPEN', 'IN_PROGRESS', 'WAITING_EXTERNAL', 'RESOLVED', 'CLOSED', 'CANCELLED');

create table ship_batches (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  batch_no text not null,
  lane text not null,
  carrier_id uuid references carriers(id),
  vessel_name text,
  voyage_no text,
  origin_port text,
  destination_port text,
  cutoff_at timestamptz,
  loaded_at timestamptz,
  departed_at timestamptz,
  arrived_at timestamptz,
  status text not null default 'DRAFT',
  created_at timestamptz not null default now(),
  unique (tenant_id, batch_no)
);

create table containers (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  ship_batch_id uuid references ship_batches(id),
  container_no text not null,
  container_type text,
  seal_no text,
  loading_location text,
  gross_weight_kg numeric(12,3),
  cbm numeric(12,4),
  status text not null default 'DRAFT',
  unique (tenant_id, container_no)
);

create table shipment_batch_links (
  tenant_id uuid not null references tenants(id),
  shipment_id uuid not null references shipments(id) on delete cascade,
  ship_batch_id uuid not null references ship_batches(id) on delete cascade,
  container_id uuid references containers(id) on delete set null,
  loaded_weight_kg numeric(12,3),
  loaded_cbm numeric(12,4),
  primary key (shipment_id, ship_batch_id)
);

create table cost_allocation_lines (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  batch_id uuid not null references cost_allocation_batches(id) on delete cascade,
  shipment_id uuid not null references shipments(id),
  source_charge_id uuid references charges(id),
  allocation_basis text not null,
  basis_value numeric(14,4) not null default 0,
  allocated_amount numeric(14,2) not null default 0,
  currency char(3) not null,
  created_at timestamptz not null default now()
);

create table shipment_milestones (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  shipment_id uuid references shipments(id) on delete cascade,
  carton_id uuid references cartons(id) on delete cascade,
  ship_batch_id uuid references ship_batches(id) on delete cascade,
  container_id uuid references containers(id) on delete cascade,
  milestone_code text not null,
  milestone_name text not null,
  occurred_at timestamptz,
  source milestone_source not null default 'MANUAL',
  responsible_role text,
  evidence jsonb not null default '{}',
  created_at timestamptz not null default now()
);

create table sla_rules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  channel_id uuid references channels(id),
  code text not null,
  name text not null,
  start_milestone_code text not null,
  end_milestone_code text not null,
  promised_min_days int,
  promised_max_days int,
  visible_to_customer boolean not null default true,
  active boolean not null default true,
  effective_from date not null,
  effective_to date,
  unique (tenant_id, code)
);

create table sla_results (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  shipment_id uuid not null references shipments(id) on delete cascade,
  sla_rule_id uuid not null references sla_rules(id),
  start_at timestamptz,
  end_at timestamptz,
  actual_days numeric(10,2),
  calculable boolean not null default false,
  breach boolean,
  created_at timestamptz not null default now(),
  unique (tenant_id, shipment_id, sla_rule_id)
);

create table compensation_rules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  channel_id uuid references channels(id),
  code text not null,
  name text not null,
  trigger_json jsonb not null default '{}',
  formula_json jsonb not null default '{}',
  internal_only boolean not null default true,
  effective_from date not null,
  effective_to date,
  unique (tenant_id, code)
);

create table tracking_status_mappings (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  carrier_id uuid references carriers(id),
  raw_status text not null,
  raw_substatus text,
  normalized_status tracking_status not null,
  description text,
  unique (tenant_id, carrier_id, raw_status, raw_substatus)
);

create table tracking_events (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  shipment_id uuid references shipments(id) on delete cascade,
  carton_id uuid references cartons(id) on delete cascade,
  carrier_id uuid references carriers(id),
  tracking_no text not null,
  event_time timestamptz not null,
  raw_status text not null,
  normalized_status tracking_status not null,
  location text,
  source milestone_source not null default 'CARRIER_API',
  raw_payload jsonb not null default '{}',
  created_at timestamptz not null default now()
);

create table shipment_relations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  parent_shipment_id uuid not null references shipments(id) on delete cascade,
  child_shipment_id uuid not null references shipments(id) on delete cascade,
  relation_type relation_type not null,
  reason text,
  created_at timestamptz not null default now(),
  unique (tenant_id, parent_shipment_id, child_shipment_id, relation_type)
);

create table return_orders (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  return_no text not null,
  original_shipment_id uuid not null references shipments(id),
  status text not null default 'OPEN',
  reason text,
  created_by uuid references users(id),
  created_at timestamptz not null default now(),
  unique (tenant_id, return_no)
);

create table return_order_cartons (
  tenant_id uuid not null references tenants(id),
  return_order_id uuid not null references return_orders(id) on delete cascade,
  carton_id uuid not null references cartons(id) on delete cascade,
  action text not null default 'RELABEL',
  new_tracking_no text,
  primary key (return_order_id, carton_id)
);

create table relabel_tasks (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  return_order_id uuid references return_orders(id),
  original_carton_id uuid references cartons(id),
  new_shipment_id uuid references shipments(id),
  status text not null default 'PENDING',
  requested_by uuid references users(id),
  completed_at timestamptz,
  created_at timestamptz not null default now()
);

create table fee_mapping_dictionary (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  carrier_id uuid references carriers(id),
  channel_id uuid references channels(id),
  raw_fee_name text not null,
  raw_fee_code text,
  charge_item_id uuid not null references charge_items(id),
  side charge_side not null,
  active boolean not null default true,
  unique (tenant_id, carrier_id, channel_id, raw_fee_name, raw_fee_code)
);

create table invoice_templates (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  template_type text not null,
  currency_mode text not null default 'ORIGINAL',
  layout_json jsonb not null default '{}',
  active boolean not null default true,
  unique (tenant_id, code)
);

create table exchange_rates (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  rate_date date not null,
  from_currency char(3) not null,
  to_currency char(3) not null,
  rate numeric(18,8) not null,
  rate_type text not null default 'ACCOUNTING',
  source text,
  unique (tenant_id, rate_date, from_currency, to_currency, rate_type)
);

create table commission_runs (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  run_no text not null,
  period_month char(7) not null,
  status text not null default 'DRAFT',
  plan_id uuid references commission_plans(id),
  created_by uuid references users(id),
  approved_by uuid references users(id),
  approved_at timestamptz,
  created_at timestamptz not null default now(),
  unique (tenant_id, run_no)
);

create table commission_lines (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  run_id uuid not null references commission_runs(id) on delete cascade,
  salesperson_id uuid references users(id),
  shipment_id uuid references shipments(id),
  basis_amount numeric(14,2) not null default 0,
  rate numeric(8,5) not null,
  commission_amount numeric(14,2) not null default 0,
  adjustment_amount numeric(14,2) not null default 0,
  metadata jsonb not null default '{}'
);

create table value_added_services (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  charge_item_id uuid references charge_items(id),
  default_uom billing_uom not null,
  active boolean not null default true,
  unique (tenant_id, code)
);

create table service_orders (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  service_order_no text not null,
  shipment_id uuid references shipments(id),
  carton_id uuid references cartons(id),
  service_id uuid not null references value_added_services(id),
  status text not null default 'REQUESTED',
  quantity numeric(14,4) not null default 1,
  requested_by uuid references users(id),
  completed_at timestamptz,
  evidence jsonb not null default '{}',
  unique (tenant_id, service_order_no)
);

create table pod_requests (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  request_no text not null,
  shipment_id uuid references shipments(id),
  carton_id uuid references cartons(id),
  pod_type text not null,
  status text not null default 'REQUESTED',
  charge_id uuid references charges(id),
  file_url text,
  requested_by uuid references users(id),
  created_at timestamptz not null default now(),
  completed_at timestamptz,
  unique (tenant_id, request_no)
);

create table exception_tickets (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  ticket_no text not null,
  shipment_id uuid references shipments(id),
  carton_id uuid references cartons(id),
  ticket_type text not null,
  status ticket_status not null default 'OPEN',
  severity text not null default 'NORMAL',
  title text not null,
  description text,
  owner_id uuid references users(id),
  due_at timestamptz,
  closed_at timestamptz,
  created_at timestamptz not null default now(),
  unique (tenant_id, ticket_no)
);

create table claim_reviews (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  claim_no text not null,
  shipment_id uuid references shipments(id),
  carton_id uuid references cartons(id),
  exception_ticket_id uuid references exception_tickets(id),
  compensation_rule_id uuid references compensation_rules(id),
  requested_amount numeric(14,2),
  approved_amount numeric(14,2),
  currency char(3),
  status text not null default 'DRAFT',
  internal_note text,
  customer_visible boolean not null default false,
  created_at timestamptz not null default now(),
  unique (tenant_id, claim_no)
);

create table prohibited_items (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  item_name text not null,
  keyword_pattern text not null,
  country_code char(2),
  channel_id uuid references channels(id),
  risk_level text not null default 'BLOCK',
  active boolean not null default true,
  effective_from date not null,
  effective_to date,
  unique (tenant_id, code)
);

create table risk_screening_results (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  shipment_id uuid not null references shipments(id) on delete cascade,
  declaration_id uuid references declarations(id),
  prohibited_item_id uuid references prohibited_items(id),
  risk_type text not null,
  risk_level text not null,
  matched_text text,
  decision text not null default 'PENDING',
  overridden_by uuid references users(id),
  override_reason text,
  created_at timestamptz not null default now()
);

create index idx_ship_batches_tenant_status on ship_batches(tenant_id, status);
create index idx_cost_allocation_lines_batch on cost_allocation_lines(tenant_id, batch_id);
create index idx_shipment_milestones_lookup on shipment_milestones(tenant_id, shipment_id, milestone_code);
create index idx_tracking_events_tracking on tracking_events(tenant_id, tracking_no, event_time);
create index idx_exception_tickets_status on exception_tickets(tenant_id, status, ticket_type);
create index idx_risk_screening_shipment on risk_screening_results(tenant_id, shipment_id, decision);

alter table ship_batches enable row level security;
alter table ship_batches force row level security;
create policy ship_batches_tenant_isolation on ship_batches using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table containers enable row level security;
alter table containers force row level security;
create policy containers_tenant_isolation on containers using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table shipment_batch_links enable row level security;
alter table shipment_batch_links force row level security;
create policy shipment_batch_links_tenant_isolation on shipment_batch_links using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table cost_allocation_lines enable row level security;
alter table cost_allocation_lines force row level security;
create policy cost_allocation_lines_tenant_isolation on cost_allocation_lines using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table shipment_milestones enable row level security;
alter table shipment_milestones force row level security;
create policy shipment_milestones_tenant_isolation on shipment_milestones using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table sla_rules enable row level security;
alter table sla_rules force row level security;
create policy sla_rules_tenant_isolation on sla_rules using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table sla_results enable row level security;
alter table sla_results force row level security;
create policy sla_results_tenant_isolation on sla_results using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table compensation_rules enable row level security;
alter table compensation_rules force row level security;
create policy compensation_rules_tenant_isolation on compensation_rules using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table tracking_status_mappings enable row level security;
alter table tracking_status_mappings force row level security;
create policy tracking_status_mappings_tenant_isolation on tracking_status_mappings using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table tracking_events enable row level security;
alter table tracking_events force row level security;
create policy tracking_events_tenant_isolation on tracking_events using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table shipment_relations enable row level security;
alter table shipment_relations force row level security;
create policy shipment_relations_tenant_isolation on shipment_relations using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table return_orders enable row level security;
alter table return_orders force row level security;
create policy return_orders_tenant_isolation on return_orders using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table return_order_cartons enable row level security;
alter table return_order_cartons force row level security;
create policy return_order_cartons_tenant_isolation on return_order_cartons using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table relabel_tasks enable row level security;
alter table relabel_tasks force row level security;
create policy relabel_tasks_tenant_isolation on relabel_tasks using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table fee_mapping_dictionary enable row level security;
alter table fee_mapping_dictionary force row level security;
create policy fee_mapping_dictionary_tenant_isolation on fee_mapping_dictionary using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table invoice_templates enable row level security;
alter table invoice_templates force row level security;
create policy invoice_templates_tenant_isolation on invoice_templates using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table exchange_rates enable row level security;
alter table exchange_rates force row level security;
create policy exchange_rates_tenant_isolation on exchange_rates using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table commission_runs enable row level security;
alter table commission_runs force row level security;
create policy commission_runs_tenant_isolation on commission_runs using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table commission_lines enable row level security;
alter table commission_lines force row level security;
create policy commission_lines_tenant_isolation on commission_lines using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table value_added_services enable row level security;
alter table value_added_services force row level security;
create policy value_added_services_tenant_isolation on value_added_services using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table service_orders enable row level security;
alter table service_orders force row level security;
create policy service_orders_tenant_isolation on service_orders using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table pod_requests enable row level security;
alter table pod_requests force row level security;
create policy pod_requests_tenant_isolation on pod_requests using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table exception_tickets enable row level security;
alter table exception_tickets force row level security;
create policy exception_tickets_tenant_isolation on exception_tickets using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table claim_reviews enable row level security;
alter table claim_reviews force row level security;
create policy claim_reviews_tenant_isolation on claim_reviews using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table prohibited_items enable row level security;
alter table prohibited_items force row level security;
create policy prohibited_items_tenant_isolation on prohibited_items using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
alter table risk_screening_results enable row level security;
alter table risk_screening_results force row level security;
create policy risk_screening_results_tenant_isolation on risk_screening_results using (app_tenant_matches(tenant_id)) with check (app_tenant_matches(tenant_id));
