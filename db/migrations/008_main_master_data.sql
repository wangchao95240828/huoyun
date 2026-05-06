-- 008: 主系统主数据补齐

create table customer_accounts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  customer_id uuid not null references customers(id) on delete cascade,
  username text not null,
  password_hash text not null,
  status text not null default 'ACTIVE'
    check (status in ('ACTIVE', 'DISABLED', 'LOCKED')),
  last_login_at timestamptz,
  created_at timestamptz not null default now(),
  unique (tenant_id, username)
);

create table api_credentials (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  owner_type text not null
    check (owner_type in ('CUSTOMER', 'PARTNER', 'USER', 'SYSTEM')),
  owner_id uuid,
  access_key text not null,
  secret_hash text not null,
  status text not null default 'ACTIVE'
    check (status in ('ACTIVE', 'DISABLED', 'REVOKED')),
  scopes jsonb not null default '[]',
  last_used_at timestamptz,
  expires_at timestamptz,
  created_at timestamptz not null default now(),
  unique (tenant_id, access_key)
);

create table user_sessions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  user_id uuid not null references users(id) on delete cascade,
  session_hash text not null,
  ip inet,
  user_agent text,
  expires_at timestamptz not null,
  revoked_at timestamptz,
  created_at timestamptz not null default now(),
  unique (tenant_id, session_hash)
);

create table partners (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  partner_type text not null default 'CARRIER'
    check (partner_type in ('CARRIER', 'AGENT', 'SUPPLIER', 'SERVICE_PROVIDER', 'OTHER')),
  settlement_currency char(3) not null default 'CNY',
  carrier_id uuid references carriers(id),
  branch_id uuid references organizations(id),
  status text not null default 'ACTIVE'
    check (status in ('ACTIVE', 'DISABLED', 'ARCHIVED')),
  source text not null default 'LOCAL'
    check (source in ('ACC', 'XQT', 'LOCAL', 'IMPORT')),
  external_id text,
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);

create table partner_accounts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  partner_id uuid not null references partners(id) on delete cascade,
  account_name text not null,
  bank_name text,
  bank_account_no text,
  currency char(3) not null default 'CNY',
  status text not null default 'ACTIVE'
    check (status in ('ACTIVE', 'DISABLED')),
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now()
);

create table customer_contacts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  customer_id uuid not null references customers(id) on delete cascade,
  name text not null,
  role text,
  phone text,
  email text,
  wechat text,
  is_primary boolean not null default false,
  created_at timestamptz not null default now()
);

create table customer_settlement_profiles (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  customer_id uuid not null references customers(id) on delete cascade,
  pay_type text not null default 'MONTHLY',
  credit_days int not null default 0,
  invoice_confirm_required boolean not null default false,
  due_actions jsonb not null default '[]',
  tax_required boolean not null default false,
  statement_template_code text,
  effective_from date not null default current_date,
  effective_to date,
  created_at timestamptz not null default now(),
  unique (tenant_id, customer_id, effective_from)
);

create table services (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  service_type text not null default 'LINEHAUL'
    check (service_type in ('LINEHAUL', 'EXPRESS', 'LTL', 'WAREHOUSE', 'VALUE_ADDED', 'OTHER')),
  channel_id uuid references channels(id),
  default_currency char(3) not null default 'CNY',
  status text not null default 'ACTIVE'
    check (status in ('ACTIVE', 'DISABLED', 'ARCHIVED')),
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);

create table service_channel_links (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  service_id uuid not null references services(id) on delete cascade,
  channel_id uuid not null references channels(id) on delete cascade,
  priority int not null default 100,
  effective_from date not null default current_date,
  effective_to date,
  active boolean not null default true,
  unique (tenant_id, service_id, channel_id, effective_from)
);

create table tags (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  scope text not null
    check (scope in ('CUSTOMER', 'SHIPMENT', 'INVOICE', 'PARTNER', 'CHARGE', 'ORDER')),
  code text not null,
  name text not null,
  color text,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  unique (tenant_id, scope, code)
);

create table entity_tags (
  tenant_id uuid not null references tenants(id),
  entity_type text not null
    check (entity_type in ('CUSTOMER', 'SHIPMENT', 'INVOICE', 'PARTNER', 'CHARGE', 'ORDER')),
  entity_id uuid not null,
  tag_id uuid not null references tags(id) on delete cascade,
  created_by uuid references users(id),
  created_at timestamptz not null default now(),
  primary key (entity_type, entity_id, tag_id)
);

create index idx_customer_accounts_customer on customer_accounts(tenant_id, customer_id);
create index idx_api_credentials_owner on api_credentials(tenant_id, owner_type, owner_id);
create index idx_user_sessions_user on user_sessions(tenant_id, user_id, expires_at);
create index idx_partners_type_status on partners(tenant_id, partner_type, status);
create index idx_partners_source on partners(tenant_id, source, external_id);
create index idx_partner_accounts_partner on partner_accounts(tenant_id, partner_id);
create index idx_customer_contacts_customer on customer_contacts(tenant_id, customer_id);
create index idx_customer_settlement_customer on customer_settlement_profiles(tenant_id, customer_id);
create index idx_services_status on services(tenant_id, status);
create index idx_service_channel_links_service on service_channel_links(tenant_id, service_id, active);
create index idx_entity_tags_lookup on entity_tags(tenant_id, entity_type, entity_id);

alter table customer_accounts enable row level security;
alter table customer_accounts force row level security;
create policy customer_accounts_tenant_isolation on customer_accounts
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table api_credentials enable row level security;
alter table api_credentials force row level security;
create policy api_credentials_tenant_isolation on api_credentials
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table user_sessions enable row level security;
alter table user_sessions force row level security;
create policy user_sessions_tenant_isolation on user_sessions
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table partners enable row level security;
alter table partners force row level security;
create policy partners_tenant_isolation on partners
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table partner_accounts enable row level security;
alter table partner_accounts force row level security;
create policy partner_accounts_tenant_isolation on partner_accounts
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table customer_contacts enable row level security;
alter table customer_contacts force row level security;
create policy customer_contacts_tenant_isolation on customer_contacts
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table customer_settlement_profiles enable row level security;
alter table customer_settlement_profiles force row level security;
create policy customer_settlement_profiles_tenant_isolation on customer_settlement_profiles
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table services enable row level security;
alter table services force row level security;
create policy services_tenant_isolation on services
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table service_channel_links enable row level security;
alter table service_channel_links force row level security;
create policy service_channel_links_tenant_isolation on service_channel_links
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table tags enable row level security;
alter table tags force row level security;
create policy tags_tenant_isolation on tags
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table entity_tags enable row level security;
alter table entity_tags force row level security;
create policy entity_tags_tenant_isolation on entity_tags
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));
