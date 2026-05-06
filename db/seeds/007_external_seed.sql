select set_config('app.service_role', 'true', false);

insert into external_systems (tenant_id, code, name, system_type, base_url, notes)
select id, 'ACC', 'ACC 旧委托运输系统', 'ACC', null, '有源码，用于反推旧业务逻辑'
from tenants
where code = 'xqt'
on conflict (tenant_id, code) do update
set name = excluded.name,
    system_type = excluded.system_type,
    notes = excluded.notes;

insert into external_systems (tenant_id, code, name, system_type, base_url, notes)
select id, 'XQT', '新智慧 TMS/AOS', 'XQT', 'https://xqtgyl.nextsls.com', '无源码，以页面和只读接口作为复刻样本'
from tenants
where code = 'xqt'
on conflict (tenant_id, code) do update
set name = excluded.name,
    system_type = excluded.system_type,
    base_url = excluded.base_url,
    notes = excluded.notes;

insert into external_modules (tenant_id, external_system_id, module_code, module_name, page_path, api_path, method, safe_mode)
select t.id, es.id, v.module_code, v.module_name, v.page_path, v.api_path, 'POST', 'READ_ONLY'
from tenants t
join external_systems es on es.tenant_id = t.id and es.code = 'XQT'
cross join (
  values
    ('financial_detail', '财务流水', '/tms/aos/financial_detail', '/rest/tms/aos/financial_detail/lists'),
    ('shipment', '运单审计', '/tms/aos/shipment', '/rest/tms/aos/shipment/lists'),
    ('user_report', '应收报表', '/tms/aos/user_report', '/rest/tms/aos/user_report/lists'),
    ('partner_report', '应付报表', '/tms/aos/partner_report', '/rest/tms/aos/partner_report/lists'),
    ('rates', '运价维护', '/tms/aos/rates', '/rest/tms/aos/rates/lists'),
    ('invoice_detail', '客户流水', '/tms/aos/invoice_detail', '/rest/tms/aos/invoice_detail/lists'),
    ('invoice', '客户账单', '/tms/aos/invoice', '/rest/tms/aos/invoice/lists'),
    ('detail_partner', '供应商流水', '/tms/aos/detail_partner', '/rest/tms/aos/detail_partner/lists'),
    ('invoice_partner', '供应商账单', '/tms/aos/invoice_partner', '/rest/tms/aos/invoice_partner/lists'),
    ('financial_account', '账户', '/tms/aos/financial_account', '/rest/tms/aos/financial_account/lists'),
    ('financial_account_record', '账户流水', '/tms/aos/financial_account_record', '/rest/tms/aos/financial_account_record/lists'),
    ('charge_type_mod', '费用类型', '/tms/aos/charge_type_mod', '/rest/tms/aos/charge_type_mod/lists'),
    ('lock_invoice_time', '月结单', '/tms/aos/lock_invoice_time', '/rest/tms/aos/lock_invoice_time/lists'),
    ('charge_approval', '费用审批', '/tms/aos/charge_approval', '/rest/tms/aos/charge_approval/lists'),
    ('approval', '审批', '/tms/aos/approval', '/rest/tms/aos/approval/lists')
) as v(module_code, module_name, page_path, api_path)
where t.code = 'xqt'
on conflict (tenant_id, external_system_id, module_code) do update
set module_name = excluded.module_name,
    page_path = excluded.page_path,
    api_path = excluded.api_path,
    method = excluded.method,
    safe_mode = excluded.safe_mode;

select set_config('app.service_role', '', false);
