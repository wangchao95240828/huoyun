-- 任务 S5 收尾：acc_transit_items 转运 ↔ 配载关联表
--
-- 解决 StowageStateMachine.onTransitInTransit 当时的占位 no-op：
-- transit 起运时需要 fanout 到所有关联 stowage → cartons → shipments 写 tracking_events。
--
-- 一个 transit 可装多个 stowage（拼柜）；一个 stowage 一般只属一个 transit，但允许中转改装。

create table if not exists acc_transit_items (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  transit_id uuid not null references acc_transits(id) on delete cascade,
  stowage_id uuid not null references stowages(id) on delete cascade,
  loaded_at timestamptz not null default now(),
  unloaded_at timestamptz,
  remark text,
  unique (tenant_id, transit_id, stowage_id)
);

create index if not exists idx_ati_transit on acc_transit_items(tenant_id, transit_id);
create index if not exists idx_ati_stowage on acc_transit_items(tenant_id, stowage_id);

alter table acc_transit_items enable row level security;
alter table acc_transit_items force row level security;
create policy ati_tenant_isolation on acc_transit_items
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));
