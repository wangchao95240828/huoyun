create table roles (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  description text,
  system_role boolean not null default false,
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);

create table permissions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  resource text not null,
  action text not null,
  description text,
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);

create table user_roles (
  tenant_id uuid not null references tenants(id),
  user_id uuid not null references users(id) on delete cascade,
  role_id uuid not null references roles(id) on delete cascade,
  scope_type text not null default 'TENANT',
  scope_id uuid not null default '00000000-0000-0000-0000-000000000000',
  granted_by uuid references users(id),
  granted_at timestamptz not null default now(),
  primary key (user_id, role_id, scope_type, scope_id)
);

create table role_permissions (
  tenant_id uuid not null references tenants(id),
  role_id uuid not null references roles(id) on delete cascade,
  permission_id uuid not null references permissions(id) on delete cascade,
  granted_at timestamptz not null default now(),
  primary key (role_id, permission_id)
);

create table approval_policies (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  resource text not null,
  action text not null,
  min_approvals int not null default 1,
  condition_json jsonb not null default '{}',
  active boolean not null default true,
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);

create table approval_requests (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  policy_id uuid references approval_policies(id),
  requester_id uuid not null references users(id),
  resource text not null,
  action text not null,
  target_id uuid not null,
  status text not null default 'PENDING',
  reason text not null,
  payload jsonb not null default '{}',
  created_at timestamptz not null default now(),
  decided_at timestamptz
);

create table approval_decisions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  request_id uuid not null references approval_requests(id) on delete cascade,
  approver_id uuid not null references users(id),
  decision text not null,
  comment text,
  decided_at timestamptz not null default now()
);

create index idx_user_roles_user on user_roles(tenant_id, user_id);
create index idx_role_permissions_role on role_permissions(tenant_id, role_id);
create index idx_permissions_resource_action on permissions(tenant_id, resource, action);
create index idx_approval_requests_status on approval_requests(tenant_id, status, resource, action);

alter table roles enable row level security;
alter table roles force row level security;
create policy roles_tenant_isolation on roles
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table permissions enable row level security;
alter table permissions force row level security;
create policy permissions_tenant_isolation on permissions
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table user_roles enable row level security;
alter table user_roles force row level security;
create policy user_roles_tenant_isolation on user_roles
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table role_permissions enable row level security;
alter table role_permissions force row level security;
create policy role_permissions_tenant_isolation on role_permissions
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table approval_policies enable row level security;
alter table approval_policies force row level security;
create policy approval_policies_tenant_isolation on approval_policies
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table approval_requests enable row level security;
alter table approval_requests force row level security;
create policy approval_requests_tenant_isolation on approval_requests
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table approval_decisions enable row level security;
alter table approval_decisions force row level security;
create policy approval_decisions_tenant_isolation on approval_decisions
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));
