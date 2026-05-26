-- ACC Labels 数据模型迁移
-- 对应 ACC 表：
--   Online_File  → label_files       面单文件元数据
--   Change       → relabel_no_map(scope='CARRIER')  渠道换单号
--   Change_No    → relabel_no_map(scope='CUSTOMER') 客户换单号

create table label_files (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  shipment_id uuid not null references shipments(id) on delete cascade,
  carton_id uuid references cartons(id),
  tracking_no text,
  label_type text not null default 'PDF'
    check (label_type in ('PDF', 'ZPL', 'IMAGE', 'ZIP')),
  file_hash text not null,
  file_ext text not null,
  storage_path text not null,
  file_size int,
  source text not null default 'API',
  created_at timestamptz not null default now()
);
create index idx_label_files_shipment on label_files(tenant_id, shipment_id);
create index idx_label_files_tracking on label_files(tenant_id, tracking_no);

alter table label_files enable row level security;
alter table label_files force row level security;
create policy label_files_tenant_isolation on label_files
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

create table relabel_no_map (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  old_no text not null,
  new_no text not null,
  scope text not null default 'CARRIER'
    check (scope in ('CARRIER', 'CUSTOMER')),
  status int not null default 0,
  -- 0 = active, 1 = intercepted（旧 ACC.Status==1 表示拦截）
  created_at timestamptz not null default now(),
  unique (tenant_id, old_no, scope)
);
create index idx_relabel_no_map_new on relabel_no_map(tenant_id, new_no);

alter table relabel_no_map enable row level security;
alter table relabel_no_map force row level security;
create policy relabel_no_map_tenant_isolation on relabel_no_map
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));
