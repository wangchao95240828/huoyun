-- 分公司种子数据
do $$
declare v_tenant uuid;
declare v_hq uuid;
begin
  select id into v_tenant from tenants where code = 'xqt';

  insert into organizations (id, tenant_id, parent_id, code, name, org_type)
  values (gen_random_uuid(), v_tenant, null, 'HQ', '深圳总部', 'hq')
  returning id into v_hq;

  insert into organizations (tenant_id, parent_id, code, name, org_type) values
    (v_tenant, v_hq, 'SZ', '深圳分公司', 'branch'),
    (v_tenant, v_hq, 'YW', '义乌分公司', 'branch'),
    (v_tenant, v_hq, 'AP', '机场分部', 'branch'),
    (v_tenant, v_hq, 'GZ', '广州分公司', 'branch');
end $$;
