-- 014: common SaaS foundation
-- Adds operator fields for CRUD APIs and strengthens role/user maintenance.

select set_config('app.service_role', 'true', false);

create or replace function app_touch_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

alter table users
  add column if not exists created_by uuid references users(id),
  add column if not exists updated_by uuid references users(id),
  add column if not exists updated_at timestamptz not null default now(),
  add column if not exists deleted_by uuid references users(id),
  add column if not exists deleted_at timestamptz;

alter table roles
  add column if not exists status text not null default 'ACTIVE',
  add column if not exists created_by uuid references users(id),
  add column if not exists updated_by uuid references users(id),
  add column if not exists updated_at timestamptz not null default now(),
  add column if not exists deleted_by uuid references users(id),
  add column if not exists deleted_at timestamptz,
  add column if not exists metadata jsonb not null default '{}';

alter table permissions
  add column if not exists status text not null default 'ACTIVE',
  add column if not exists created_by uuid references users(id),
  add column if not exists updated_by uuid references users(id),
  add column if not exists updated_at timestamptz not null default now(),
  add column if not exists deleted_by uuid references users(id),
  add column if not exists deleted_at timestamptz,
  add column if not exists metadata jsonb not null default '{}';

alter table customers
  add column if not exists status text not null default 'ACTIVE',
  add column if not exists created_by uuid references users(id),
  add column if not exists updated_by uuid references users(id),
  add column if not exists updated_at timestamptz not null default now(),
  add column if not exists deleted_by uuid references users(id),
  add column if not exists deleted_at timestamptz;

alter table business_flows
  add column if not exists created_by uuid references users(id),
  add column if not exists updated_by uuid references users(id),
  add column if not exists updated_at timestamptz not null default now(),
  add column if not exists deleted_by uuid references users(id),
  add column if not exists deleted_at timestamptz,
  add column if not exists metadata jsonb not null default '{}';

alter table orders
  add column if not exists updated_by uuid references users(id),
  add column if not exists updated_at timestamptz not null default now(),
  add column if not exists deleted_by uuid references users(id),
  add column if not exists deleted_at timestamptz;

alter table order_lines
  add column if not exists created_by uuid references users(id),
  add column if not exists created_at timestamptz not null default now(),
  add column if not exists updated_by uuid references users(id),
  add column if not exists updated_at timestamptz not null default now(),
  add column if not exists deleted_by uuid references users(id),
  add column if not exists deleted_at timestamptz;

alter table audit_logs
  alter column before_data set default '{}',
  alter column after_data set default '{}';

alter table audit_logs
  add column if not exists request_id text,
  add column if not exists ip inet,
  add column if not exists user_agent text,
  add column if not exists metadata jsonb not null default '{}';

do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'users_deleted_status_check') then
    alter table users
      add constraint users_deleted_status_check
      check (deleted_at is null or status <> 'ACTIVE');
  end if;

  if not exists (select 1 from pg_constraint where conname = 'roles_status_check') then
    alter table roles
      add constraint roles_status_check
      check (status in ('ACTIVE', 'DISABLED', 'ARCHIVED'));
  end if;

  if not exists (select 1 from pg_constraint where conname = 'permissions_status_check') then
    alter table permissions
      add constraint permissions_status_check
      check (status in ('ACTIVE', 'DISABLED', 'ARCHIVED'));
  end if;

  if not exists (select 1 from pg_constraint where conname = 'customers_status_check') then
    alter table customers
      add constraint customers_status_check
      check (status in ('ACTIVE', 'DISABLED', 'ARCHIVED'));
  end if;
end
$$;

create index if not exists idx_users_deleted on users(tenant_id, deleted_at, status);
create index if not exists idx_roles_deleted on roles(tenant_id, deleted_at, status);
create index if not exists idx_permissions_deleted on permissions(tenant_id, deleted_at, status);
create index if not exists idx_customers_deleted on customers(tenant_id, deleted_at, status);
create index if not exists idx_business_flows_deleted on business_flows(tenant_id, deleted_at, active);
create index if not exists idx_orders_deleted on orders(tenant_id, customer_direction, service_mode, deleted_at);
create index if not exists idx_order_lines_deleted on order_lines(tenant_id, order_id, deleted_at);
create index if not exists idx_audit_logs_entity_created on audit_logs(tenant_id, entity_type, entity_id, created_at);
create index if not exists idx_audit_logs_actor_created on audit_logs(tenant_id, actor_id, created_at);

drop trigger if exists tr_users_touch_updated_at on users;
create trigger tr_users_touch_updated_at
before update on users
for each row execute function app_touch_updated_at();

drop trigger if exists tr_roles_touch_updated_at on roles;
create trigger tr_roles_touch_updated_at
before update on roles
for each row execute function app_touch_updated_at();

drop trigger if exists tr_permissions_touch_updated_at on permissions;
create trigger tr_permissions_touch_updated_at
before update on permissions
for each row execute function app_touch_updated_at();

drop trigger if exists tr_customers_touch_updated_at on customers;
create trigger tr_customers_touch_updated_at
before update on customers
for each row execute function app_touch_updated_at();

drop trigger if exists tr_business_flows_touch_updated_at on business_flows;
create trigger tr_business_flows_touch_updated_at
before update on business_flows
for each row execute function app_touch_updated_at();

drop trigger if exists tr_orders_touch_updated_at on orders;
create trigger tr_orders_touch_updated_at
before update on orders
for each row execute function app_touch_updated_at();

drop trigger if exists tr_order_lines_touch_updated_at on order_lines;
create trigger tr_order_lines_touch_updated_at
before update on order_lines
for each row execute function app_touch_updated_at();

insert into permissions (tenant_id, code, name, resource, action, description)
select t.id, v.code, v.name, v.resource, v.action, v.description
from tenants t
cross join (
  values
    ('business.flow.read', '查看业务流程', 'business.flow', 'read', '查看两套客户流程配置'),
    ('business.flow.write', '维护业务流程', 'business.flow', 'write', '维护两套客户流程配置'),
    ('admin.permission.read', '查看权限点', 'admin.permission', 'read', '查看系统权限点'),
    ('admin.permission.write', '维护权限点', 'admin.permission', 'write', '维护系统权限点')
) as v(code, name, resource, action, description)
where t.code = 'xqt'
on conflict (tenant_id, code) do nothing;

insert into role_permissions (tenant_id, role_id, permission_id)
select r.tenant_id, r.id, p.id
from roles r
join permissions p on p.tenant_id = r.tenant_id
where r.code = 'ADMIN'
  and p.code in ('business.flow.read', 'business.flow.write', 'admin.permission.read', 'admin.permission.write')
on conflict do nothing;

insert into role_permissions (tenant_id, role_id, permission_id)
select r.tenant_id, r.id, p.id
from roles r
join permissions p on p.tenant_id = r.tenant_id
where r.code in ('OPERATOR', 'FINANCE_MANAGER')
  and p.code in ('business.flow.read')
on conflict do nothing;

select set_config('app.service_role', '', false);
