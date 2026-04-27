select set_config('app.service_role', 'true', false);

insert into roles (tenant_id, code, name, description, system_role)
select t.id, v.code, v.name, v.description, true
from tenants t
cross join (
  values
    ('ADMIN', '系统管理员', '拥有租户内全部权限'),
    ('FINANCE_MANAGER', '财务主管', '负责应收、应付、对账、过账和审批'),
    ('FINANCE_CLERK', '财务专员', '负责账单导入、对账异常处理和账单生成'),
    ('OPERATOR', '操作', '负责运单、派送比价和风险确认')
) as v(code, name, description)
where t.code = 'xqt'
on conflict (tenant_id, code) do nothing;

insert into permissions (tenant_id, code, name, resource, action, description)
select t.id, v.code, v.name, v.resource, v.action, v.description
from tenants t
cross join (
  values
    ('finance.rate.read', '查看费率规则', 'finance.rate', 'read', '查看价卡和规则版本'),
    ('finance.rate.write', '维护费率规则', 'finance.rate', 'write', '维护价卡、附加费和燃油规则'),
    ('finance.invoice.read', '查看客户账单', 'finance.invoice', 'read', '查看客户账单和费用行'),
    ('finance.invoice.write', '生成客户账单', 'finance.invoice', 'write', '生成、锁定和调整客户账单'),
    ('finance.bill.import', '导入渠道账单', 'finance.carrier_bill', 'import', '上传和映射渠道账单'),
    ('finance.reconcile.review', '处理对账异常', 'finance.reconciliation', 'review', '确认、申诉或关闭对账差异'),
    ('finance.ledger.post', '正式过账', 'finance.ledger', 'post', '将费用、收款和成本过账到账本'),
    ('finance.adjust.approve', '审批财务调整', 'finance.adjustment', 'approve', '审批改价、冲销和绕 SOP'),
    ('operation.delivery_quote.write', '派送比价', 'operation.delivery_quote', 'write', '生成快递/LTL 比价并确认风险')
) as v(code, name, resource, action, description)
where t.code = 'xqt'
on conflict (tenant_id, code) do nothing;

insert into role_permissions (tenant_id, role_id, permission_id)
select r.tenant_id, r.id, p.id
from roles r
join permissions p on p.tenant_id = r.tenant_id
where r.code = 'ADMIN'
on conflict do nothing;

insert into role_permissions (tenant_id, role_id, permission_id)
select r.tenant_id, r.id, p.id
from roles r
join permissions p on p.tenant_id = r.tenant_id
where r.code = 'FINANCE_MANAGER'
  and p.code in (
    'finance.rate.read',
    'finance.invoice.read',
    'finance.invoice.write',
    'finance.bill.import',
    'finance.reconcile.review',
    'finance.ledger.post',
    'finance.adjust.approve'
  )
on conflict do nothing;

insert into role_permissions (tenant_id, role_id, permission_id)
select r.tenant_id, r.id, p.id
from roles r
join permissions p on p.tenant_id = r.tenant_id
where r.code = 'FINANCE_CLERK'
  and p.code in (
    'finance.rate.read',
    'finance.invoice.read',
    'finance.invoice.write',
    'finance.bill.import',
    'finance.reconcile.review'
  )
on conflict do nothing;

insert into role_permissions (tenant_id, role_id, permission_id)
select r.tenant_id, r.id, p.id
from roles r
join permissions p on p.tenant_id = r.tenant_id
where r.code = 'OPERATOR'
  and p.code in ('operation.delivery_quote.write')
on conflict do nothing;

insert into user_roles (tenant_id, user_id, role_id)
select u.tenant_id, u.id, r.id
from users u
join roles r on r.tenant_id = u.tenant_id and r.code = u.role_code
on conflict do nothing;

insert into approval_policies (tenant_id, code, name, resource, action, min_approvals, condition_json)
select t.id, v.code, v.name, v.resource, v.action, v.min_approvals, v.condition_json::jsonb
from tenants t
cross join (
  values
    ('FIN_ADJUST_OVER_1000', '大额财务调整审批', 'finance.adjustment', 'approve', 1, '{"amount_gte":1000}'),
    ('BYPASS_SOP_FINANCE', '财务绕 SOP 审批', 'sop.bypass', 'approve', 1, '{}')
) as v(code, name, resource, action, min_approvals, condition_json)
where t.code = 'xqt'
on conflict (tenant_id, code) do nothing;

select set_config('app.service_role', '', false);
