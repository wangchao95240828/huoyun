create type background_job_status as enum ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'RETRYING', 'DEAD');
create type idempotency_status as enum ('STARTED', 'SUCCEEDED', 'FAILED');

create table idempotency_keys (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  key text not null,
  request_hash text,
  status idempotency_status not null default 'STARTED',
  locked_until timestamptz,
  response_status int,
  response_body jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, key)
);

create table background_jobs (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  job_type text not null,
  status background_job_status not null default 'QUEUED',
  idempotency_key_id uuid references idempotency_keys(id),
  priority int not null default 100,
  max_attempts int not null default 5,
  attempts int not null default 0,
  run_after timestamptz not null default now(),
  payload jsonb not null default '{}',
  result jsonb,
  last_error text,
  created_by uuid references users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table background_job_attempts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  job_id uuid not null references background_jobs(id) on delete cascade,
  attempt_no int not null,
  worker_id text,
  started_at timestamptz not null default now(),
  finished_at timestamptz,
  status background_job_status not null,
  error_message text,
  metadata jsonb not null default '{}',
  unique (job_id, attempt_no)
);

create table import_file_fingerprints (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  source_type text not null,
  source_id uuid,
  file_name text not null,
  sha256 text not null,
  byte_size bigint,
  uploaded_by uuid references users(id),
  created_at timestamptz not null default now(),
  unique (tenant_id, source_type, sha256)
);

alter table carrier_bill_lines
  add column natural_key text,
  add column source_line_hash text;

create unique index carrier_bill_lines_import_natural_key
  on carrier_bill_lines(tenant_id, import_id, natural_key)
  where natural_key is not null;

create unique index carrier_bill_lines_import_source_hash
  on carrier_bill_lines(tenant_id, import_id, source_line_hash)
  where source_line_hash is not null;

create index idx_background_jobs_ready
  on background_jobs(tenant_id, status, run_after, priority);

create index idx_background_job_attempts_job
  on background_job_attempts(tenant_id, job_id, attempt_no);

alter table idempotency_keys enable row level security;
alter table idempotency_keys force row level security;
create policy idempotency_keys_tenant_isolation on idempotency_keys
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table background_jobs enable row level security;
alter table background_jobs force row level security;
create policy background_jobs_tenant_isolation on background_jobs
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table background_job_attempts enable row level security;
alter table background_job_attempts force row level security;
create policy background_job_attempts_tenant_isolation on background_job_attempts
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table import_file_fingerprints enable row level security;
alter table import_file_fingerprints force row level security;
create policy import_file_fingerprints_tenant_isolation on import_file_fingerprints
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));
