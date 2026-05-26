-- ACC HR 人事 + 提成：employees / attendances / wages / commission_rules / commissions
--   socials / social_persons / funds / fund_persons
-- 对应 ACC 旧类：
--   Employee.php (62KB, 最复杂) → acc_employees
--   Attendances.php → acc_attendances
--   Wages.php       → acc_wages
--   Commission.php  → acc_commissions + acc_commission_rules
--   Social.php      → acc_socials + acc_social_persons
--   Fund.php        → acc_funds + acc_fund_persons

-- 1) 员工管理
create table acc_employees (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  emp_no text not null,
  name text not null,
  gender text check (gender in ('M','F')),
  mobile text,
  branch_id uuid references organizations(id),
  department_id uuid references organizations(id),
  position text,
  status text default 'ACTIVE' check (status in ('ACTIVE','LEFT','PROBATION')),
  entry_date date,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, emp_no)
);
alter table acc_employees enable row level security;
alter table acc_employees force row level security;
create policy acc_employees_tenant_isolation on acc_employees
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 2) 考勤管理
create table acc_attendances (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  employee_id uuid not null references acc_employees(id),
  the_date date not null,
  status text default 'PRESENT' check (status in ('PRESENT','LATE','EARLY','ABSENT','LEAVE','OVERTIME')),
  sign_in_time time,
  sign_out_time time,
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, employee_id, the_date)
);
alter table acc_attendances enable row level security;
alter table acc_attendances force row level security;
create policy acc_attendances_tenant_isolation on acc_attendances
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 3) 工资发放（含金额 + 汇率快照）
create table acc_wages (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  employee_id uuid not null references acc_employees(id),
  the_month char(7) not null,
  basic numeric(14,2), bonus numeric(14,2), commission numeric(14,2),
  deduction numeric(14,2), total numeric(14,2),
  currency char(3) default 'CNY',
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, employee_id, the_month)
);
alter table acc_wages enable row level security;
alter table acc_wages force row level security;
create policy acc_wages_tenant_isolation on acc_wages
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 4) 提成规则
create table acc_commission_rules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  name text not null,
  rule_type text check (rule_type in ('PERCENT','FIXED','TIERED')),
  percent numeric(8,4),
  amount numeric(14,2),
  sales numeric(14,2),
  profit numeric(14,2),
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_commission_rules enable row level security;
alter table acc_commission_rules force row level security;
create policy acc_commission_rules_tenant_isolation on acc_commission_rules
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 5) 员工提成（含金额 + 汇率快照）
create table acc_commissions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  employee_id uuid not null references acc_employees(id),
  rule_id uuid references acc_commission_rules(id),
  the_month char(7),
  amount numeric(14,2) not null default 0,
  currency char(3) default 'CNY',
  sales_amount numeric(14,2),
  profit_amount numeric(14,2),
  status text default 'PENDING' check (status in ('PENDING','CONFIRMED','PAID')),
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_commissions enable row level security;
alter table acc_commissions force row level security;
create policy acc_commissions_tenant_isolation on acc_commissions
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 6) 社保缴纳
create table acc_socials (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  the_month char(7) not null,
  total_amount numeric(14,2) not null default 0,
  currency char(3) default 'CNY',
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, the_month)
);
alter table acc_socials enable row level security;
alter table acc_socials force row level security;
create policy acc_socials_tenant_isolation on acc_socials
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 7) 社保人员明细（关联 socials）
create table acc_social_persons (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  social_id uuid not null references acc_socials(id),
  employee_id uuid not null references acc_employees(id),
  person_amount numeric(14,2) not null default 0,
  company_amount numeric(14,2) not null default 0,
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_social_persons enable row level security;
alter table acc_social_persons force row level security;
create policy acc_social_persons_tenant_isolation on acc_social_persons
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 8) 公积金缴纳（同 socials 结构）
create table acc_funds (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  the_month char(7) not null,
  total_amount numeric(14,2) not null default 0,
  currency char(3) default 'CNY',
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, the_month)
);
alter table acc_funds enable row level security;
alter table acc_funds force row level security;
create policy acc_funds_tenant_isolation on acc_funds
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 9) 公积金人员明细（关联 funds）
create table acc_fund_persons (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  fund_id uuid not null references acc_funds(id),
  employee_id uuid not null references acc_employees(id),
  person_amount numeric(14,2) not null default 0,
  company_amount numeric(14,2) not null default 0,
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_fund_persons enable row level security;
alter table acc_fund_persons force row level security;
create policy acc_fund_persons_tenant_isolation on acc_fund_persons
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));
