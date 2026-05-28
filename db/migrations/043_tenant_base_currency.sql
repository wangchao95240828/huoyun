-- 任务 S6：tenants 加 base_currency 字段
--
-- 资金动作（balance_ledger 11 类 biz_type）写入时，如果 ledger.currency != tenant.base_currency
-- 自动捕获 fx_rate_snapshots 一条记录，便于事后对账重现。
--
-- 默认 CNY；多币种租户可后端配置 UPDATE。

alter table tenants
  add column if not exists base_currency char(3) not null default 'CNY';

-- 同时给 fx_rate_snapshots 加 biz_type / source_type / source_ref 三列，
-- 让 snapshot 能精确反查到触发它的 ledger 动作（11 类）
alter table fx_rate_snapshots
  add column if not exists biz_type text,
  add column if not exists source_type text,
  add column if not exists source_ref text;

create index if not exists idx_fx_snapshots_biz
  on fx_rate_snapshots(tenant_id, biz_type, snapshot_at desc)
  where biz_type is not null;
