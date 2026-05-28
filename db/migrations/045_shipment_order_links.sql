-- 任务 S8：shipment_order_links 关联表
--
-- 决策：选方案 A（链接表）
-- 理由：ACC 域模型存在 1:N（一票拆多单）和 N:1（多票合一单）场景，
--       customer_ref 字符串软关联在拆/合时无法表达，且无 UNIQUE 约束可加。
--
-- link_type:
--   - SUBMIT：Submit 提交单据时的默认强关联（1:1 主路径）
--   - SPLIT：单据拆分时的子关联
--   - MERGE：单据合并时的子关联
--   - REPLACE：补单 / 替换时的关联（保留追溯）

create table if not exists shipment_order_links (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  shipment_id uuid not null references shipments(id) on delete cascade,
  order_id uuid not null references orders(id) on delete cascade,
  link_type text not null default 'SUBMIT'
    check (link_type in ('SUBMIT', 'SPLIT', 'MERGE', 'REPLACE')),
  created_at timestamptz not null default now(),
  created_by text,
  unique (tenant_id, shipment_id, order_id)
);

create index if not exists idx_sol_shipment on shipment_order_links(tenant_id, shipment_id);
create index if not exists idx_sol_order on shipment_order_links(tenant_id, order_id);

alter table shipment_order_links enable row level security;
alter table shipment_order_links force row level security;
create policy sol_tenant_isolation on shipment_order_links
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));
