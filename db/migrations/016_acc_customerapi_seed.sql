-- 016: ACC customer-api 演示凭证和余额账户
-- 复用 008_main_master_data.sql 中已建好的 api_credentials 与 financial_accounts。
-- 不新增表。secret_hash 在 ACC 兼容签名（md5(join(',',sorted)+key)）下需要可还原，
-- 当前阶段直接存原始 APIKey；后续可演进到 HMAC-SHA256 或加密存储。

select set_config('app.service_role', 'true', false);

insert into api_credentials (
  tenant_id, owner_type, owner_id, access_key, secret_hash, status, scopes
)
select
  t.id,
  'CUSTOMER',
  c.id,
  '60000DEMO',
  'DEMOAPIKEY32CHARS00000000000DEMO',
  'ACTIVE',
  '["customer-api.order.write","customer-api.balance.read"]'::jsonb
from tenants t
join customers c on c.tenant_id = t.id and c.code = 'DOC-DEMO'
where t.code = 'xqt'
on conflict (tenant_id, access_key) do update
set owner_id = excluded.owner_id,
    secret_hash = excluded.secret_hash,
    scopes = excluded.scopes,
    status = excluded.status;

insert into financial_accounts (
  tenant_id, owner_type, owner_id, account_name, account_type,
  currency, balance, status, source, metadata
)
select t.id, 'CUSTOMER', c.id, 'DOC-DEMO CNY 预付余额', 'VIRTUAL',
       'CNY', 10000.00, 'ACTIVE', 'LOCAL',
       '{"purpose":"customer-api.balance","seed":true}'::jsonb
from tenants t
join customers c on c.tenant_id = t.id and c.code = 'DOC-DEMO'
where t.code = 'xqt'
  and not exists (
    select 1 from financial_accounts fa
    where fa.tenant_id = t.id
      and fa.owner_id = c.id
      and fa.currency = 'CNY'
      and fa.metadata->>'purpose' = 'customer-api.balance'
  );

insert into financial_accounts (
  tenant_id, owner_type, owner_id, account_name, account_type,
  currency, balance, status, source, metadata
)
select t.id, 'CUSTOMER', c.id, 'DOC-DEMO USD 预付余额', 'VIRTUAL',
       'USD', 500.00, 'ACTIVE', 'LOCAL',
       '{"purpose":"customer-api.balance","seed":true}'::jsonb
from tenants t
join customers c on c.tenant_id = t.id and c.code = 'DOC-DEMO'
where t.code = 'xqt'
  and not exists (
    select 1 from financial_accounts fa
    where fa.tenant_id = t.id
      and fa.owner_id = c.id
      and fa.currency = 'USD'
      and fa.metadata->>'purpose' = 'customer-api.balance'
  );

select set_config('app.service_role', '', false);
