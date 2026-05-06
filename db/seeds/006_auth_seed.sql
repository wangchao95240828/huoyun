select set_config('app.service_role', 'true', false);

insert into users (tenant_id, username, email, display_name, role_code, password_hash, status)
select
  t.id,
  'admin',
  'admin@xqt.local',
  '系统管理员',
  'ADMIN',
  'pbkdf2$sha256$210000$xqt-dev-admin-salt-2026$TCDjVY6HTDD_BemIdNIORU5HMaR7GIRot6oTcid2MYA',
  'ACTIVE'
from tenants t
where t.code = 'xqt'
on conflict (tenant_id, email) do update
set username = excluded.username,
    role_code = excluded.role_code,
    password_hash = excluded.password_hash,
    status = excluded.status;

update users
set username = coalesce(username, split_part(email, '@', 1)),
    password_hash = coalesce(password_hash, 'pbkdf2$sha256$210000$xqt-dev-admin-salt-2026$TCDjVY6HTDD_BemIdNIORU5HMaR7GIRot6oTcid2MYA'),
    status = coalesce(status, 'ACTIVE')
where tenant_id in (select id from tenants where code = 'xqt')
  and role_code in ('ADMIN', 'FINANCE_MANAGER');

insert into permissions (tenant_id, code, name, resource, action, description)
select t.id, v.code, v.name, v.resource, v.action, v.description
from tenants t
cross join (
  values
    ('auth.profile.read', '查看个人信息', 'auth.profile', 'read', '读取当前登录用户信息'),
    ('admin.user.read', '查看用户', 'admin.user', 'read', '查看用户列表和用户详情'),
    ('admin.user.write', '维护用户', 'admin.user', 'write', '创建、禁用、编辑用户和重置密码'),
    ('admin.role.read', '查看角色', 'admin.role', 'read', '查看角色和权限'),
    ('admin.role.write', '维护角色', 'admin.role', 'write', '维护角色授权'),
    ('admin.audit.read', '查看审计日志', 'admin.audit', 'read', '查看登录和操作审计'),
    ('finance.ledger.read', '查看账本', 'finance.ledger', 'read', '查看账本分录和凭证'),
    ('finance.account.read', '查看资金账户', 'finance.account', 'read', '查看账户和账户流水'),
    ('finance.account.write', '维护资金账户', 'finance.account', 'write', '维护账户和资金流水'),
    ('finance.payable.read', '查看应付', 'finance.payable', 'read', '查看供应商账单和付款'),
    ('finance.payable.write', '维护应付', 'finance.payable', 'write', '维护供应商账单、付款和核销'),
    ('finance.receivable.read', '查看应收', 'finance.receivable', 'read', '查看客户账单和收款'),
    ('finance.receivable.write', '维护应收', 'finance.receivable', 'write', '维护客户账单、收款和核销'),
    ('operation.order.read', '查看订单', 'operation.order', 'read', '查看订单、运单和箱'),
    ('operation.order.write', '维护订单', 'operation.order', 'write', '创建和维护订单、运单和箱'),
    ('warehouse.scan.write', '仓库扫描', 'warehouse.scan', 'write', '执行收货、拣货、装车和扫描操作')
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
    'auth.profile.read',
    'admin.audit.read',
    'finance.rate.read',
    'finance.invoice.read',
    'finance.invoice.write',
    'finance.bill.import',
    'finance.reconcile.review',
    'finance.ledger.read',
    'finance.ledger.post',
    'finance.account.read',
    'finance.account.write',
    'finance.payable.read',
    'finance.payable.write',
    'finance.receivable.read',
    'finance.receivable.write',
    'finance.adjust.approve'
  )
on conflict do nothing;

insert into role_permissions (tenant_id, role_id, permission_id)
select r.tenant_id, r.id, p.id
from roles r
join permissions p on p.tenant_id = r.tenant_id
where r.code = 'OPERATOR'
  and p.code in (
    'auth.profile.read',
    'operation.order.read',
    'operation.order.write',
    'operation.delivery_quote.write',
    'warehouse.scan.write'
  )
on conflict do nothing;

insert into user_roles (tenant_id, user_id, role_id)
select u.tenant_id, u.id, r.id
from users u
join roles r on r.tenant_id = u.tenant_id and r.code = u.role_code
on conflict do nothing;

select set_config('app.service_role', '', false);
