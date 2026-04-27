select set_config('app.service_role', 'true', false);

insert into ledger_accounts (tenant_id, code, name, account_type, normal_balance, currency)
select t.id, v.code, v.name, v.account_type::ledger_account_type, v.normal_balance::ledger_normal_balance, v.currency
from tenants t
cross join (
  values
    ('1001', '银行存款', 'ASSET', 'DEBIT', 'CNY'),
    ('1122', '应收账款', 'ASSET', 'DEBIT', 'CNY'),
    ('2202', '应付账款', 'LIABILITY', 'CREDIT', 'CNY'),
    ('5001', '主营业务收入', 'REVENUE', 'CREDIT', 'CNY'),
    ('5401', '主营业务成本', 'EXPENSE', 'DEBIT', 'CNY'),
    ('5601', '销售费用-提成', 'EXPENSE', 'DEBIT', 'CNY'),
    ('6601', '财务费用-汇兑损益', 'EXPENSE', 'DEBIT', 'CNY')
) as v(code, name, account_type, normal_balance, currency)
where t.code = 'xqt'
on conflict (tenant_id, code) do nothing;

select set_config('app.service_role', '', false);
