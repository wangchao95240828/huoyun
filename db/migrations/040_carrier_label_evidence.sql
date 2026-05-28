-- 给 cartons 和 label_files 加 provider evidence 字段。
--
-- 对照 docs/acc-logic-gap-claude-task-2026-05-28.md §3.5 + §6 实现约束：
--   "provider request/response evidence 需要保存"
--
-- 之前 SandboxCarrierGateway / SandboxLabelGateway 把 request+response 放进
-- Issuance.raw / LabelArtifact.raw，但 insertCarton / insertLabelFile 没接 raw
-- 参数，evidence 实际没落库。本 migration 补字段，配套 service 改造写入。

alter table cartons
  add column if not exists carrier_evidence jsonb not null default '{}';

alter table label_files
  add column if not exists evidence jsonb not null default '{}';
