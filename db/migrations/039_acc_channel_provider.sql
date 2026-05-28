-- ACC 渠道取号 provider 数据驱动（任务5）。
--
-- 对照 docs/acc-logic-gap-claude-task-2026-05-28.md §3.5 + 任务5：
--   CarrierGatewayRegistry 由 acc_channel_accounts 驱动渠道 → provider 路由
--   （复刻 ACC Channel_Account.Code → inc/online/<Code>.php 插件选择）。
--
-- acc_channel_accounts 补 provider_code：决定该渠道账号用哪个 CarrierGateway 实现
-- （SANDBOX / UPS / FEDEX / 自营 EDI ...）。

alter table acc_channel_accounts
  add column if not exists provider_code text;

-- 演示：给 EU-AIR-UPS 渠道配一个 SANDBOX provider 账号，便于联调数据驱动路由 + evidence
insert into acc_channel_accounts (
  tenant_id, channel_id, provider_code, account_no, account_name,
  endpoint_url, is_active
)
select t.id, ch.id, 'SANDBOX', 'SBX-001', 'Sandbox 演示取号账号',
       'https://sandbox.carrier.example.com/v1/shipments', true
from tenants t
join channels ch on ch.tenant_id = t.id and ch.code = 'EU-AIR-UPS'
where not exists (
  select 1 from acc_channel_accounts a
  where a.tenant_id = t.id and a.channel_id = ch.id and a.provider_code = 'SANDBOX'
);
