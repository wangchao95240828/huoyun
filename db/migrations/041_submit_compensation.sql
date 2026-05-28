-- 任务 S2：Submit 失败业务补偿表
--
-- 对照 docs/acc-logic-gap-claude-task-supplement-2026-05-29.md 任务 S2 + 任务书 §3.1#4。
--
-- 渠道 provider 已成功取号但 DB 后续 insert 失败时，DB rollback 会让本地数据回滚，
-- 但 provider 那边的子单号仍然存在 —— "幽灵单号"。本表记录这类异常，
-- SubmitCompensationService 会 best-effort 调 CarrierGateway.cancel；
-- 失败也保留记录，后续运维定期回查批处理。

create table acc_orphan_tracking_nos (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null,
  carrier_master_tracking_no text,
  carrier_tracking_no text,
  provider_code text not null,
  channel_code text,
  order_no text,
  customer_ref text,
  reason text not null,
  raw_request jsonb,
  raw_response jsonb,
  created_at timestamptz not null default now(),
  cancel_attempted boolean not null default false,
  cancel_success boolean,
  cancel_response jsonb,
  cancel_attempted_at timestamptz
);

create index idx_orphan_tracking_tenant on acc_orphan_tracking_nos(tenant_id, created_at desc);
create index idx_orphan_tracking_provider on acc_orphan_tracking_nos(provider_code, cancel_attempted);

alter table acc_orphan_tracking_nos enable row level security;
alter table acc_orphan_tracking_nos force row level security;
create policy acc_orphan_tracking_nos_tenant_isolation on acc_orphan_tracking_nos
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));
