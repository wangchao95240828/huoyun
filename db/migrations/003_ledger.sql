create type ledger_account_type as enum ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE');
create type ledger_normal_balance as enum ('DEBIT', 'CREDIT');
create type ledger_transaction_status as enum ('DRAFT', 'POSTED', 'REVERSED');
create type ledger_entry_direction as enum ('DEBIT', 'CREDIT');

create table ledger_accounts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  account_type ledger_account_type not null,
  normal_balance ledger_normal_balance not null,
  currency char(3),
  parent_account_id uuid references ledger_accounts(id),
  active boolean not null default true,
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);

create table ledger_transactions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  transaction_no text not null,
  status ledger_transaction_status not null default 'DRAFT',
  source_type text not null,
  source_id uuid not null,
  description text,
  effective_at timestamptz not null default now(),
  posted_at timestamptz,
  posted_by uuid references users(id),
  reversed_transaction_id uuid references ledger_transactions(id),
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now(),
  unique (tenant_id, transaction_no)
);

create table ledger_entries (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  transaction_id uuid not null references ledger_transactions(id) on delete restrict,
  account_id uuid not null references ledger_accounts(id),
  direction ledger_entry_direction not null,
  currency char(3) not null,
  amount numeric(18,2) not null check (amount > 0),
  shipment_id uuid references shipments(id),
  charge_id uuid references charges(id),
  external_ref text,
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now()
);

create table posting_batches (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  batch_no text not null,
  source_type text not null,
  status text not null default 'DRAFT',
  created_by uuid references users(id),
  posted_at timestamptz,
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now(),
  unique (tenant_id, batch_no)
);

create table posting_batch_transactions (
  tenant_id uuid not null references tenants(id),
  batch_id uuid not null references posting_batches(id) on delete cascade,
  transaction_id uuid not null references ledger_transactions(id) on delete restrict,
  primary key (batch_id, transaction_id)
);

create index idx_ledger_accounts_tenant_type on ledger_accounts(tenant_id, account_type);
create index idx_ledger_transactions_source on ledger_transactions(tenant_id, source_type, source_id);
create index idx_ledger_entries_account_effective on ledger_entries(tenant_id, account_id, created_at);
create index idx_ledger_entries_shipment on ledger_entries(tenant_id, shipment_id);
create index idx_ledger_entries_charge on ledger_entries(tenant_id, charge_id);

create or replace function prevent_ledger_entry_mutation()
returns trigger
language plpgsql
as $$
begin
  raise exception 'ledger entries are immutable; post a reversing or adjustment transaction instead';
end;
$$;

create trigger ledger_entries_no_update
before update on ledger_entries
for each row execute function prevent_ledger_entry_mutation();

create trigger ledger_entries_no_delete
before delete on ledger_entries
for each row execute function prevent_ledger_entry_mutation();

create or replace function prevent_posted_ledger_transaction_mutation()
returns trigger
language plpgsql
as $$
begin
  if old.status in ('POSTED', 'REVERSED') then
    raise exception 'posted ledger transactions are immutable';
  end if;

  if old.status = 'DRAFT' and new.status = 'POSTED' then
    new.posted_at := coalesce(new.posted_at, now());
    return new;
  end if;

  return new;
end;
$$;

create trigger ledger_transactions_guard_update
before update on ledger_transactions
for each row execute function prevent_posted_ledger_transaction_mutation();

create or replace function prevent_ledger_transaction_delete()
returns trigger
language plpgsql
as $$
begin
  raise exception 'ledger transactions are immutable; use reversal status and reversing entries instead';
end;
$$;

create trigger ledger_transactions_no_delete
before delete on ledger_transactions
for each row execute function prevent_ledger_transaction_delete();

create or replace function assert_posted_ledger_transaction_balanced()
returns trigger
language plpgsql
as $$
declare
  tx_id uuid;
  tx_status ledger_transaction_status;
  unbalanced_count int;
  entry_count int;
begin
  if TG_TABLE_NAME = 'ledger_transactions' then
    tx_id := coalesce(new.id, old.id);
  else
    tx_id := coalesce(new.transaction_id, old.transaction_id);
  end if;

  select status into tx_status
  from ledger_transactions
  where id = tx_id;

  if tx_status <> 'POSTED' then
    return null;
  end if;

  select count(*) into entry_count
  from ledger_entries
  where transaction_id = tx_id;

  if entry_count < 2 then
    raise exception 'posted ledger transaction % must have at least two entries', tx_id;
  end if;

  select count(*) into unbalanced_count
  from (
    select
      currency,
      sum(case when direction = 'DEBIT' then amount else -amount end) as balance
    from ledger_entries
    where transaction_id = tx_id
    group by currency
  ) x
  where balance <> 0;

  if unbalanced_count > 0 then
    raise exception 'posted ledger transaction % is not balanced by currency', tx_id;
  end if;

  return null;
end;
$$;

create constraint trigger ledger_entries_balance_check
after insert on ledger_entries
deferrable initially deferred
for each row execute function assert_posted_ledger_transaction_balanced();

create constraint trigger ledger_transactions_balance_check
after insert or update of status on ledger_transactions
deferrable initially deferred
for each row execute function assert_posted_ledger_transaction_balanced();

alter table ledger_accounts enable row level security;
alter table ledger_accounts force row level security;
create policy ledger_accounts_tenant_isolation on ledger_accounts
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table ledger_transactions enable row level security;
alter table ledger_transactions force row level security;
create policy ledger_transactions_tenant_isolation on ledger_transactions
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table ledger_entries enable row level security;
alter table ledger_entries force row level security;
create policy ledger_entries_tenant_isolation on ledger_entries
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table posting_batches enable row level security;
alter table posting_batches force row level security;
create policy posting_batches_tenant_isolation on posting_batches
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table posting_batch_transactions enable row level security;
alter table posting_batch_transactions force row level security;
create policy posting_batch_transactions_tenant_isolation on posting_batch_transactions
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));
