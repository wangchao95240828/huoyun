-- 010: 财务复刻核心表

create table partner_invoices (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  partner_id uuid not null references partners(id),
  invoice_no text not null,
  branch_id uuid references organizations(id),
  currency char(3) not null default 'CNY',
  total_amount numeric(14,2) not null default 0,
  paid_amount numeric(14,2) not null default 0,
  unpaid_amount numeric(14,2) not null default 0,
  status text not null default 'DRAFT'
    check (status in ('DRAFT', 'CONFIRMED', 'PARTIAL_PAID', 'PAID', 'VOID')),
  writeoff_status text not null default 'UNPAID'
    check (writeoff_status in ('UNPAID', 'PARTIAL', 'PAID')),
  invoice_date timestamptz,
  due_date timestamptz,
  ship_time timestamptz,
  confirmed_at timestamptz,
  confirmed_by uuid references users(id),
  source text not null default 'LOCAL'
    check (source in ('ACC', 'XQT', 'LOCAL', 'IMPORT')),
  external_id text,
  metadata jsonb not null default '{}',
  created_by uuid references users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, invoice_no)
);

create table partner_invoice_lines (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  invoice_id uuid not null references partner_invoices(id) on delete cascade,
  shipment_id uuid references shipments(id),
  carton_id uuid references cartons(id),
  charge_id uuid references charges(id),
  source_bill_line_id uuid references carrier_bill_lines(id),
  charge_item_id uuid references charge_items(id),
  line_no int not null default 1,
  description text,
  currency char(3) not null default 'CNY',
  amount numeric(14,2) not null default 0,
  tax_amount numeric(14,2) not null default 0,
  metadata jsonb not null default '{}'
);

create table partner_payments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  partner_id uuid not null references partners(id),
  payment_no text not null,
  financial_account_id uuid,
  currency char(3) not null default 'CNY',
  amount numeric(14,2) not null check (amount >= 0),
  payment_type text,
  reference_no text,
  status text not null default 'DRAFT'
    check (status in ('DRAFT', 'PAID', 'VOID')),
  paid_at timestamptz,
  paid_by uuid references users(id),
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now(),
  unique (tenant_id, payment_no)
);

create table receivable_settlements (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  settlement_no text not null,
  customer_id uuid not null references customers(id),
  payment_id uuid references payments(id),
  currency char(3) not null default 'CNY',
  settled_amount numeric(14,2) not null default 0,
  status text not null default 'DRAFT'
    check (status in ('DRAFT', 'POSTED', 'VOID')),
  settled_at timestamptz,
  settled_by uuid references users(id),
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now(),
  unique (tenant_id, settlement_no)
);

create table receivable_settlement_lines (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  settlement_id uuid not null references receivable_settlements(id) on delete cascade,
  invoice_id uuid references customer_invoices(id),
  charge_id uuid references charges(id),
  amount numeric(14,2) not null default 0,
  metadata jsonb not null default '{}'
);

create table payable_settlements (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  settlement_no text not null,
  partner_id uuid not null references partners(id),
  payment_id uuid references partner_payments(id),
  currency char(3) not null default 'CNY',
  settled_amount numeric(14,2) not null default 0,
  status text not null default 'DRAFT'
    check (status in ('DRAFT', 'POSTED', 'VOID')),
  settled_at timestamptz,
  settled_by uuid references users(id),
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now(),
  unique (tenant_id, settlement_no)
);

create table payable_settlement_lines (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  settlement_id uuid not null references payable_settlements(id) on delete cascade,
  partner_invoice_id uuid references partner_invoices(id),
  charge_id uuid references charges(id),
  amount numeric(14,2) not null default 0,
  metadata jsonb not null default '{}'
);

create table tax_invoices (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  tax_invoice_no text not null,
  customer_id uuid references customers(id),
  customer_invoice_id uuid references customer_invoices(id),
  currency char(3) not null default 'CNY',
  amount numeric(14,2) not null default 0,
  tax_amount numeric(14,2) not null default 0,
  status text not null default 'DRAFT'
    check (status in ('DRAFT', 'ISSUED', 'VOID')),
  issued_at timestamptz,
  issued_by uuid references users(id),
  file_url text,
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now(),
  unique (tenant_id, tax_invoice_no)
);

create table financial_accounts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  owner_type text not null
    check (owner_type in ('COMPANY', 'CUSTOMER', 'PARTNER', 'STAFF')),
  owner_id uuid,
  account_name text not null,
  account_type text not null default 'CASH'
    check (account_type in ('CASH', 'BANK', 'ALIPAY', 'WECHAT', 'VIRTUAL', 'OTHER')),
  bank_name text,
  bank_account_no text,
  currency char(3) not null default 'CNY',
  balance numeric(18,2) not null default 0,
  status text not null default 'ACTIVE'
    check (status in ('ACTIVE', 'DISABLED', 'ARCHIVED')),
  source text not null default 'LOCAL'
    check (source in ('ACC', 'XQT', 'LOCAL', 'IMPORT')),
  external_id text,
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now()
);

alter table partner_payments
  add constraint partner_payments_financial_account_fk
  foreign key (financial_account_id) references financial_accounts(id);

create table financial_account_records (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  account_id uuid not null references financial_accounts(id),
  record_no text,
  direction text not null
    check (direction in ('IN', 'OUT')),
  currency char(3) not null default 'CNY',
  amount numeric(18,2) not null check (amount >= 0),
  balance_after numeric(18,2),
  payment_type text,
  business_time timestamptz,
  source_type text,
  source_id uuid,
  related_entity_type text,
  related_entity_id uuid,
  audited_status text not null default 'PENDING'
    check (audited_status in ('PENDING', 'PASSED', 'REJECTED', 'CANCELLED')),
  audited_at timestamptz,
  audited_by uuid references users(id),
  description text,
  source text not null default 'LOCAL'
    check (source in ('ACC', 'XQT', 'LOCAL', 'IMPORT')),
  external_id text,
  raw_payload jsonb not null default '{}',
  created_by uuid references users(id),
  created_at timestamptz not null default now()
);

create table charge_audit_events (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  charge_id uuid not null references charges(id) on delete cascade,
  audit_type text not null
    check (audit_type in ('AR', 'AP', 'SELLER_COST', 'SELLER_COMMISSION', 'GENERAL')),
  from_status text,
  to_status text not null,
  actor_id uuid references users(id),
  reason text,
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now()
);

create table charge_adjustments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  charge_id uuid references charges(id),
  adjustment_no text not null,
  adjustment_type text not null
    check (adjustment_type in ('DISCOUNT', 'SURCHARGE', 'REVERSAL', 'WRITE_OFF', 'CORRECTION')),
  currency char(3) not null default 'CNY',
  amount_delta numeric(14,2) not null default 0,
  reason text not null,
  status text not null default 'PENDING'
    check (status in ('PENDING', 'APPROVED', 'REJECTED', 'POSTED', 'VOID')),
  approval_request_id uuid references approval_requests(id),
  created_by uuid references users(id),
  approved_by uuid references users(id),
  approved_at timestamptz,
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now(),
  unique (tenant_id, adjustment_no)
);

create table shipment_charge_snapshots (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  shipment_id uuid not null references shipments(id) on delete cascade,
  ar_total numeric(14,2) not null default 0,
  ap_total numeric(14,2) not null default 0,
  seller_cost_total numeric(14,2) not null default 0,
  commission_total numeric(14,2) not null default 0,
  gross_profit numeric(14,2) not null default 0,
  currency char(3) not null default 'CNY',
  audited_status text not null default 'PENDING'
    check (audited_status in ('PENDING', 'PARTIAL', 'PASSED', 'REJECTED')),
  snapshot_at timestamptz not null default now(),
  metadata jsonb not null default '{}',
  unique (tenant_id, shipment_id)
);

create table profit_snapshots (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  shipment_id uuid not null references shipments(id) on delete cascade,
  ar_amount numeric(14,2) not null default 0,
  ap_amount numeric(14,2) not null default 0,
  seller_cost_amount numeric(14,2) not null default 0,
  commission_amount numeric(14,2) not null default 0,
  gross_profit numeric(14,2) not null default 0,
  currency char(3) not null default 'CNY',
  snapshot_at timestamptz not null default now(),
  metadata jsonb not null default '{}'
);

create table reconciliation_cases (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  case_no text not null,
  result_id uuid references reconciliation_results(id),
  owner_id uuid references users(id),
  status text not null default 'OPEN'
    check (status in ('OPEN', 'IN_REVIEW', 'CLAIMING', 'RESOLVED', 'CLOSED', 'CANCELLED')),
  resolution_type text,
  resolution_note text,
  resolved_at timestamptz,
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now(),
  unique (tenant_id, case_no)
);

alter table customer_invoices
  add column if not exists invoice_date timestamptz,
  add column if not exists due_date timestamptz,
  add column if not exists confirmed_at timestamptz,
  add column if not exists confirmed_by uuid references users(id),
  add column if not exists paid_amount numeric(14,2) not null default 0,
  add column if not exists unpaid_amount numeric(14,2) not null default 0,
  add column if not exists tax_status text not null default 'NONE'
    check (tax_status in ('NONE', 'PARTIAL', 'ISSUED', 'VOID')),
  add column if not exists writeoff_status text not null default 'UNPAID'
    check (writeoff_status in ('UNPAID', 'PARTIAL', 'PAID')),
  add column if not exists source text not null default 'LOCAL'
    check (source in ('ACC', 'XQT', 'LOCAL', 'IMPORT')),
  add column if not exists external_id text,
  add column if not exists metadata jsonb not null default '{}',
  add column if not exists updated_at timestamptz not null default now();

create index idx_partner_invoices_partner_status on partner_invoices(tenant_id, partner_id, status);
create index idx_partner_invoices_source on partner_invoices(tenant_id, source, external_id);
create index idx_partner_invoice_lines_invoice on partner_invoice_lines(tenant_id, invoice_id);
create index idx_partner_invoice_lines_shipment on partner_invoice_lines(tenant_id, shipment_id, charge_id);
create index idx_partner_payments_partner on partner_payments(tenant_id, partner_id, status);
create index idx_receivable_settlements_customer on receivable_settlements(tenant_id, customer_id, status);
create index idx_receivable_settlement_lines_invoice on receivable_settlement_lines(tenant_id, invoice_id);
create index idx_payable_settlements_partner on payable_settlements(tenant_id, partner_id, status);
create index idx_payable_settlement_lines_invoice on payable_settlement_lines(tenant_id, partner_invoice_id);
create index idx_tax_invoices_customer on tax_invoices(tenant_id, customer_id, status);
create index idx_financial_accounts_owner on financial_accounts(tenant_id, owner_type, owner_id, status);
create index idx_financial_accounts_source on financial_accounts(tenant_id, source, external_id);
create index idx_financial_account_records_account_time on financial_account_records(tenant_id, account_id, business_time);
create index idx_financial_account_records_source on financial_account_records(tenant_id, source, external_id);
create index idx_financial_account_records_related on financial_account_records(tenant_id, related_entity_type, related_entity_id);
create index idx_charge_audit_events_charge on charge_audit_events(tenant_id, charge_id, created_at);
create index idx_charge_adjustments_charge on charge_adjustments(tenant_id, charge_id, status);
create index idx_shipment_charge_snapshots_status on shipment_charge_snapshots(tenant_id, audited_status);
create index idx_profit_snapshots_shipment on profit_snapshots(tenant_id, shipment_id, snapshot_at);
create index idx_reconciliation_cases_status on reconciliation_cases(tenant_id, status, owner_id);
create index idx_customer_invoices_source on customer_invoices(tenant_id, source, external_id);

alter table partner_invoices enable row level security;
alter table partner_invoices force row level security;
create policy partner_invoices_tenant_isolation on partner_invoices
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table partner_invoice_lines enable row level security;
alter table partner_invoice_lines force row level security;
create policy partner_invoice_lines_tenant_isolation on partner_invoice_lines
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table partner_payments enable row level security;
alter table partner_payments force row level security;
create policy partner_payments_tenant_isolation on partner_payments
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table receivable_settlements enable row level security;
alter table receivable_settlements force row level security;
create policy receivable_settlements_tenant_isolation on receivable_settlements
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table receivable_settlement_lines enable row level security;
alter table receivable_settlement_lines force row level security;
create policy receivable_settlement_lines_tenant_isolation on receivable_settlement_lines
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table payable_settlements enable row level security;
alter table payable_settlements force row level security;
create policy payable_settlements_tenant_isolation on payable_settlements
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table payable_settlement_lines enable row level security;
alter table payable_settlement_lines force row level security;
create policy payable_settlement_lines_tenant_isolation on payable_settlement_lines
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table tax_invoices enable row level security;
alter table tax_invoices force row level security;
create policy tax_invoices_tenant_isolation on tax_invoices
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table financial_accounts enable row level security;
alter table financial_accounts force row level security;
create policy financial_accounts_tenant_isolation on financial_accounts
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table financial_account_records enable row level security;
alter table financial_account_records force row level security;
create policy financial_account_records_tenant_isolation on financial_account_records
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table charge_audit_events enable row level security;
alter table charge_audit_events force row level security;
create policy charge_audit_events_tenant_isolation on charge_audit_events
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table charge_adjustments enable row level security;
alter table charge_adjustments force row level security;
create policy charge_adjustments_tenant_isolation on charge_adjustments
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table shipment_charge_snapshots enable row level security;
alter table shipment_charge_snapshots force row level security;
create policy shipment_charge_snapshots_tenant_isolation on shipment_charge_snapshots
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table profit_snapshots enable row level security;
alter table profit_snapshots force row level security;
create policy profit_snapshots_tenant_isolation on profit_snapshots
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table reconciliation_cases enable row level security;
alter table reconciliation_cases force row level security;
create policy reconciliation_cases_tenant_isolation on reconciliation_cases
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));
