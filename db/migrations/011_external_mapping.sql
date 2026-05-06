-- 011: 外部系统样本、字段映射与人工对照

create table external_systems (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  system_type text not null
    check (system_type in ('ACC', 'XQT', 'CARRIER', 'CUSTOMER_API', 'OTHER')),
  base_url text,
  status text not null default 'ACTIVE'
    check (status in ('ACTIVE', 'DISABLED', 'ARCHIVED')),
  notes text,
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);

create table external_modules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  external_system_id uuid not null references external_systems(id) on delete cascade,
  module_code text not null,
  module_name text not null,
  page_path text,
  api_path text,
  method text not null default 'GET'
    check (method in ('GET', 'POST', 'PUT', 'PATCH', 'DELETE')),
  request_body_schema jsonb not null default '{}',
  response_schema jsonb not null default '{}',
  safe_mode text not null default 'READ_ONLY'
    check (safe_mode in ('READ_ONLY', 'WRITE_REVIEW_REQUIRED', 'WRITE_ALLOWED')),
  notes text,
  created_at timestamptz not null default now(),
  unique (tenant_id, external_system_id, module_code)
);

create table external_field_mappings (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  external_module_id uuid not null references external_modules(id) on delete cascade,
  external_field text not null,
  external_label text,
  external_type text,
  internal_table text not null,
  internal_field text not null,
  transform_rule jsonb not null default '{}',
  confidence numeric(5,4) not null default 0,
  status text not null default 'DRAFT'
    check (status in ('DRAFT', 'CONFIRMED', 'REJECTED')),
  notes text,
  created_at timestamptz not null default now(),
  unique (tenant_id, external_module_id, external_field, internal_table, internal_field)
);

create table external_object_refs (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  entity_type text not null,
  entity_id uuid not null,
  external_system_id uuid not null references external_systems(id),
  external_id text,
  external_no text,
  external_payload_hash text,
  first_seen_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  metadata jsonb not null default '{}',
  unique (tenant_id, entity_type, entity_id, external_system_id),
  unique (tenant_id, external_system_id, external_id)
);

create table external_payload_snapshots (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  external_module_id uuid not null references external_modules(id) on delete cascade,
  capture_no text not null,
  request_body jsonb not null default '{}',
  response_schema jsonb not null default '{}',
  sample_payload jsonb not null default '{}',
  sanitized boolean not null default true,
  captured_by uuid references users(id),
  captured_at timestamptz not null default now(),
  notes text,
  unique (tenant_id, capture_no)
);

create table comparison_cases (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  case_no text not null,
  external_module_id uuid references external_modules(id),
  internal_route text,
  external_filter jsonb not null default '{}',
  expected_result jsonb not null default '{}',
  actual_result jsonb not null default '{}',
  status text not null default 'PENDING'
    check (status in ('PENDING', 'PASSED', 'FAILED', 'BLOCKED')),
  checked_by uuid references users(id),
  checked_at timestamptz,
  notes text,
  created_at timestamptz not null default now(),
  unique (tenant_id, case_no)
);

create index idx_external_systems_type on external_systems(tenant_id, system_type, status);
create index idx_external_modules_system on external_modules(tenant_id, external_system_id, module_code);
create index idx_external_field_mappings_module on external_field_mappings(tenant_id, external_module_id, status);
create index idx_external_object_refs_entity on external_object_refs(tenant_id, entity_type, entity_id);
create index idx_external_object_refs_external_no on external_object_refs(tenant_id, external_system_id, external_no);
create index idx_external_payload_snapshots_module on external_payload_snapshots(tenant_id, external_module_id, captured_at);
create index idx_comparison_cases_status on comparison_cases(tenant_id, status, external_module_id);

alter table external_systems enable row level security;
alter table external_systems force row level security;
create policy external_systems_tenant_isolation on external_systems
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table external_modules enable row level security;
alter table external_modules force row level security;
create policy external_modules_tenant_isolation on external_modules
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table external_field_mappings enable row level security;
alter table external_field_mappings force row level security;
create policy external_field_mappings_tenant_isolation on external_field_mappings
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table external_object_refs enable row level security;
alter table external_object_refs force row level security;
create policy external_object_refs_tenant_isolation on external_object_refs
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table external_payload_snapshots enable row level security;
alter table external_payload_snapshots force row level security;
create policy external_payload_snapshots_tenant_isolation on external_payload_snapshots
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table comparison_cases enable row level security;
alter table comparison_cases force row level security;
create policy comparison_cases_tenant_isolation on comparison_cases
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

select set_config('app.service_role', 'true', false);

insert into external_systems (tenant_id, code, name, system_type, base_url, notes)
select id, 'ACC', 'ACC 旧委托运输系统', 'ACC', null, '有源码，用于反推旧业务逻辑'
from tenants where code = 'xqt'
on conflict (tenant_id, code) do nothing;

insert into external_systems (tenant_id, code, name, system_type, base_url, notes)
select id, 'XQT', '新智慧 TMS/AOS', 'XQT', 'https://xqtgyl.nextsls.com', '无源码，以页面和只读接口作为复刻样本'
from tenants where code = 'xqt'
on conflict (tenant_id, code) do nothing;

insert into external_modules (tenant_id, external_system_id, module_code, module_name, page_path, api_path, method, safe_mode)
select t.id, es.id, v.module_code, v.module_name, v.page_path, v.api_path, 'POST', 'READ_ONLY'
from tenants t
join external_systems es on es.tenant_id = t.id and es.code = 'XQT'
cross join (
  values
    ('financial_detail', '财务流水', '/tms/aos/financial_detail', '/rest/tms/aos/financial_detail/lists'),
    ('shipment', '运单审计', '/tms/aos/shipment', '/rest/tms/aos/shipment/lists'),
    ('user_report', '应收报表', '/tms/aos/user_report', '/rest/tms/aos/user_report/lists'),
    ('partner_report', '应付报表', '/tms/aos/partner_report', '/rest/tms/aos/partner_report/lists'),
    ('rates', '运价维护', '/tms/aos/rates', '/rest/tms/aos/rates/lists'),
    ('invoice_detail', '客户流水', '/tms/aos/invoice_detail', '/rest/tms/aos/invoice_detail/lists'),
    ('invoice', '客户账单', '/tms/aos/invoice', '/rest/tms/aos/invoice/lists'),
    ('detail_partner', '供应商流水', '/tms/aos/detail_partner', '/rest/tms/aos/detail_partner/lists'),
    ('invoice_partner', '供应商账单', '/tms/aos/invoice_partner', '/rest/tms/aos/invoice_partner/lists'),
    ('financial_account', '账户', '/tms/aos/financial_account', '/rest/tms/aos/financial_account/lists'),
    ('financial_account_record', '账户流水', '/tms/aos/financial_account_record', '/rest/tms/aos/financial_account_record/lists'),
    ('charge_type_mod', '费用类型', '/tms/aos/charge_type_mod', '/rest/tms/aos/charge_type_mod/lists'),
    ('lock_invoice_time', '月结单', '/tms/aos/lock_invoice_time', '/rest/tms/aos/lock_invoice_time/lists'),
    ('charge_approval', '费用审批', '/tms/aos/charge_approval', '/rest/tms/aos/charge_approval/lists'),
    ('approval', '审批', '/tms/aos/approval', '/rest/tms/aos/approval/lists')
) as v(module_code, module_name, page_path, api_path)
where t.code = 'xqt'
on conflict (tenant_id, external_system_id, module_code) do nothing;

select set_config('app.service_role', '', false);
