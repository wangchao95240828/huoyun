-- ACC 审核流 / 状态机 / 级联 / 字段权限 / 汇率快照 框架基础数据模型
-- 对应 ACC 旧代码：每个业务表都有 auditName/auditTime/audited 字段 + audit-biz / undo-biz / batch-audit 端点。

-- ─────────────────────────────────────────────────────────────────────────────
-- 1) audit_events：所有审核动作的不可变事件日志
-- ─────────────────────────────────────────────────────────────────────────────
create table audit_events (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  entity_type text not null,
  -- bills / charges / costs / orders / shipments / ... 与 /api/acc/{tab} 一一对应
  entity_id text not null,
  action text not null
    check (action in ('AUDIT', 'UNDO_AUDIT', 'BATCH_AUDIT', 'CREATE', 'UPDATE', 'DELETE')),
  actor_id uuid references users(id),
  actor_name text,
  occurred_at timestamptz not null default now(),
  before_state jsonb not null default '{}',
  after_state jsonb not null default '{}',
  remark text
);
create index idx_audit_events_entity on audit_events(tenant_id, entity_type, entity_id, occurred_at desc);
create index idx_audit_events_actor on audit_events(tenant_id, actor_id, occurred_at desc);

alter table audit_events enable row level security;
alter table audit_events force row level security;
create policy audit_events_tenant_isolation on audit_events
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- ─────────────────────────────────────────────────────────────────────────────
-- 2) 给 18 个对接表加 audit_status / audited_at / audit_name 列
--    对应 ACC: 每行有 auditName, auditTime, audited 字段，决定是否可改。
-- ─────────────────────────────────────────────────────────────────────────────

-- 财务三剑客
alter table customer_invoices
  add column if not exists audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  add column if not exists audited_at timestamptz,
  add column if not exists audit_name text;

alter table charges
  add column if not exists audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  add column if not exists audited_at timestamptz,
  add column if not exists audit_name text;

alter table payments
  add column if not exists audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  add column if not exists audited_at timestamptz,
  add column if not exists audit_name text;

alter table partner_payments
  add column if not exists audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  add column if not exists audited_at timestamptz,
  add column if not exists audit_name text;

-- 业务 5 件
alter table orders
  add column if not exists audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  add column if not exists audited_at timestamptz,
  add column if not exists audit_name text;

alter table shipments
  add column if not exists audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  add column if not exists audited_at timestamptz,
  add column if not exists audit_name text;

-- 主数据 6 件
alter table customers
  add column if not exists audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  add column if not exists audited_at timestamptz,
  add column if not exists audit_name text;

alter table channels
  add column if not exists audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  add column if not exists audited_at timestamptz,
  add column if not exists audit_name text;

alter table finance_currency
  add column if not exists audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  add column if not exists audited_at timestamptz,
  add column if not exists audit_name text;

alter table partners
  add column if not exists audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  add column if not exists audited_at timestamptz,
  add column if not exists audit_name text;

alter table remote_zones
  add column if not exists audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  add column if not exists audited_at timestamptz,
  add column if not exists audit_name text;

alter table fuel_surcharge_rates
  add column if not exists audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  add column if not exists audited_at timestamptz,
  add column if not exists audit_name text;

alter table charge_items
  add column if not exists audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  add column if not exists audited_at timestamptz,
  add column if not exists audit_name text;

alter table organizations
  add column if not exists audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  add column if not exists audited_at timestamptz,
  add column if not exists audit_name text;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3) exchange_rate_snapshots：所有走货币换算的 charges / payments 都要落汇率快照
--    对应 ACC charges/payments 落库时 freeze 当时的汇率，避免日后修改主汇率时金额漂移。
-- ─────────────────────────────────────────────────────────────────────────────
create table exchange_rate_snapshots (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  entity_type text not null,
  entity_id text not null,
  source_currency char(3) not null,
  target_currency char(3) not null,
  rate numeric(18, 8) not null,
  source_amount numeric(18, 4) not null,
  target_amount numeric(18, 4) not null,
  snapshotted_at timestamptz not null default now(),
  unique (tenant_id, entity_type, entity_id, source_currency, target_currency)
);
create index idx_exchange_rate_snapshots_entity
  on exchange_rate_snapshots(tenant_id, entity_type, entity_id);

alter table exchange_rate_snapshots enable row level security;
alter table exchange_rate_snapshots force row level security;
create policy exchange_rate_snapshots_tenant_isolation on exchange_rate_snapshots
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));
