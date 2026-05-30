-- 任务：去 SaaS 多租户，回退为单租户 + 应用层 RBAC（角色 + 分公司）
--
-- 删除所有 RLS policy + 关闭所有表的 ROW LEVEL SECURITY。
-- 保留：
--   - tenant_id 字段（210 张表都有，删字段成本太大，留着不用即可）
--   - app_tenant_matches() 函数（其他 migration 引用，不删）
--   - users.branch_id / customers.branch_id / shipments.branch_id（RBAC 过滤用）
--   - user_roles / role_permissions 等 RBAC 表（继续用）
--
-- 业务层后续在 controller WHERE 加：
--   - SALESMAN 只看 customers.salesman_user_id = current_user
--   - BRANCH_MANAGER 只看 *.branch_id = current_user.branch_id
--   - ADMIN/FINANCE 不加过滤

-- 关掉所有 public schema 表的 RLS
do $$
declare
  t record;
begin
  for t in
    select schemaname, tablename from pg_tables
    where schemaname = 'public' and rowsecurity = true
  loop
    execute format('alter table %I.%I disable row level security', t.schemaname, t.tablename);
    execute format('alter table %I.%I no force row level security', t.schemaname, t.tablename);
  end loop;
end $$;

-- 删除所有 policy
do $$
declare
  p record;
begin
  for p in
    select schemaname, tablename, policyname from pg_policies where schemaname = 'public'
  loop
    execute format('drop policy if exists %I on %I.%I', p.policyname, p.schemaname, p.tablename);
  end loop;
end $$;

-- 验证
select
  (select count(*) from pg_tables where schemaname='public' and rowsecurity=true) as rls_enabled_tables,
  (select count(*) from pg_policies where schemaname='public') as remaining_policies;
