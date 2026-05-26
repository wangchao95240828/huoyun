-- ACC 异常流（collects/detains/asks/reparations）+ 财务扩展（fees/fines）批量迁移
-- 对应 ACC 旧表 + 类：
--   Collect.php     → acc_collects        (总单/留仓)
--   Detain.php      → acc_detains         (扣件)
--   Ask.php         → acc_asks            (问题件)
--   Repair/Refund   → acc_reparations     (赔偿)
--   Fee.php         → acc_fees            (杂费套餐)
--   CFine.php / SFine → acc_fines (side 区分 CUSTOMER/SUPPLIER)
--
-- 字段统一带审核流四件套：audit_status / audited_at / audit_name + RLS。

-- ─────────────────────────────────────────────────────────────────────────────
-- 1) 总单 / 留仓
-- ─────────────────────────────────────────────────────────────────────────────
create table acc_collects (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  collect_no text not null,
  piece int not null default 0,
  weight_kg numeric(14, 3) not null default 0,
  status text not null default 'PENDING'
    check (status in ('PENDING', 'CONFIRMED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED')),
  remark text,
  add_name text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, collect_no)
);
alter table acc_collects enable row level security;
alter table acc_collects force row level security;
create policy acc_collects_tenant_isolation on acc_collects
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- ─────────────────────────────────────────────────────────────────────────────
-- 2) 扣件
-- ─────────────────────────────────────────────────────────────────────────────
create table acc_detains (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  detain_no text not null,
  customer_id uuid references customers(id),
  shipment_id uuid references shipments(id),
  detain_type text,
  status text not null default 'PENDING'
    check (status in ('PENDING', 'PROCESSING', 'RELEASED', 'DISCARDED')),
  reason text,
  add_name text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, detain_no)
);
alter table acc_detains enable row level security;
alter table acc_detains force row level security;
create policy acc_detains_tenant_isolation on acc_detains
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- ─────────────────────────────────────────────────────────────────────────────
-- 3) 问题件
-- ─────────────────────────────────────────────────────────────────────────────
create table acc_asks (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  shipment_id uuid references shipments(id),
  customer_ref text,
  content text not null,
  source text,
  ask_type text,
  status text not null default 'OPEN'
    check (status in ('OPEN', 'RESOLVED', 'CLOSED')),
  add_name text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
create index idx_acc_asks_shipment on acc_asks(tenant_id, shipment_id);
alter table acc_asks enable row level security;
alter table acc_asks force row level security;
create policy acc_asks_tenant_isolation on acc_asks
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- ─────────────────────────────────────────────────────────────────────────────
-- 4) 赔偿
-- ─────────────────────────────────────────────────────────────────────────────
create table acc_reparations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  shipment_id uuid references shipments(id),
  customer_ref text,
  apply_amount numeric(14, 2) not null default 0,
  paid_amount numeric(14, 2),
  currency char(3) not null default 'CNY',
  reason text,
  status text not null default 'PENDING'
    check (status in ('PENDING', 'APPROVED', 'REJECTED', 'PAID')),
  add_name text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_reparations enable row level security;
alter table acc_reparations force row level security;
create policy acc_reparations_tenant_isolation on acc_reparations
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- ─────────────────────────────────────────────────────────────────────────────
-- 5) 杂费套餐
-- ─────────────────────────────────────────────────────────────────────────────
create table acc_fees (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  remark text,
  item_count int not null default 0,
  linked_products text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);
alter table acc_fees enable row level security;
alter table acc_fees force row level security;
create policy acc_fees_tenant_isolation on acc_fees
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- ─────────────────────────────────────────────────────────────────────────────
-- 6) 罚款（customer + supplier 共表，side 区分）
-- ─────────────────────────────────────────────────────────────────────────────
create table acc_fines (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  fine_no text not null,
  the_date date,
  side text not null
    check (side in ('CUSTOMER', 'SUPPLIER')),
  customer_id uuid references customers(id),
  partner_id uuid references partners(id),
  amount numeric(14, 2) not null default 0,
  currency char(3) not null default 'CNY',
  remark text,
  add_name text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, fine_no)
);
create index idx_acc_fines_side on acc_fines(tenant_id, side, the_date desc);
alter table acc_fines enable row level security;
alter table acc_fines force row level security;
create policy acc_fines_tenant_isolation on acc_fines
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));
