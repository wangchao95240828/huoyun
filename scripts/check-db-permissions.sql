-- 服务器数据库权限自检脚本
-- 用法：在服务器上以应用账号 xqt 连接 xqt_saas 库后执行
--   psql -h 127.0.0.1 -p 5432 -U xqt -d xqt_saas -f check-db-permissions.sql
-- 或在容器内：
--   docker exec -i <pg-container> psql -U xqt -d xqt_saas < check-db-permissions.sql
-- 把整段输出贴回来即可。

\echo '========== 1. 连接身份 =========='
select current_user, session_user, current_database(), version();

\echo ''
\echo '========== 2. 当前角色属性（superuser / createdb / bypassrls 等）=========='
select rolname, rolsuper, rolcreaterole, rolcreatedb, rolcanlogin, rolbypassrls, rolreplication
from pg_roles
where rolname = current_user;

\echo ''
\echo '========== 3. 当前角色继承到的所有角色（解释为什么有/没有权限）=========='
with recursive role_chain as (
  select oid, rolname from pg_roles where rolname = current_user
  union
  select r.oid, r.rolname from pg_roles r
  join pg_auth_members m on m.roleid = r.oid
  join role_chain rc on rc.oid = m.member
)
select rolname from role_chain order by rolname;

\echo ''
\echo '========== 4. public schema 的 CREATE / USAGE 权限 =========='
select has_schema_privilege(current_user, 'public', 'CREATE') as can_create_in_public,
       has_schema_privilege(current_user, 'public', 'USAGE')  as can_use_public;

\echo ''
\echo '========== 5. ACC 集成会用到的两张已有表是否可写 =========='
select t.tablename,
       t.tableowner,
       has_table_privilege(current_user, t.schemaname || '.' || t.tablename, 'SELECT') as can_select,
       has_table_privilege(current_user, t.schemaname || '.' || t.tablename, 'INSERT') as can_insert,
       has_table_privilege(current_user, t.schemaname || '.' || t.tablename, 'UPDATE') as can_update
from pg_tables t
where t.tablename in ('api_credentials', 'financial_accounts', 'customers',
                      'tenants', 'orders', 'order_lines')
order by t.tablename;

\echo ''
\echo '========== 6. 表数量和迁移已应用到哪里 =========='
select count(*) as table_count from pg_tables where schemaname = 'public';

select tablename from pg_tables where schemaname = 'public'
  and tablename in ('api_credentials','financial_accounts','external_field_mappings',
                    'comparison_cases','business_flows','tracking_events')
order by tablename;

\echo ''
\echo '========== 7. RLS 状态（决定 xqt 能否绕过租户隔离）=========='
select tablename, rowsecurity as rls_enabled, forcerowsecurity as rls_forced
from pg_tables
where schemaname = 'public'
  and tablename in ('api_credentials','financial_accounts','customers','orders');

\echo ''
\echo '========== 8. 当前角色受限到哪些 GRANT 上 =========='
select table_schema, table_name, privilege_type
from information_schema.role_table_grants
where grantee = current_user
order by table_name, privilege_type
limit 50;

\echo ''
\echo '========== 9. 探测：当前用户是否能 CREATE TABLE（不实际改动数据）=========='
do $$
begin
  create temporary table xqt_perm_probe (id int);
  raise notice 'CREATE TEMP TABLE: OK';
  drop table xqt_perm_probe;
exception when others then
  raise notice 'CREATE TEMP TABLE: FAILED -> %', SQLERRM;
end$$;

do $$
begin
  execute 'create table public._xqt_perm_probe(id int)';
  raise notice 'CREATE TABLE public._xqt_perm_probe: OK';
  execute 'drop table public._xqt_perm_probe';
exception when others then
  raise notice 'CREATE TABLE public: FAILED -> %', SQLERRM;
end$$;
