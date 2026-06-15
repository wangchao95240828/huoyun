-- 083: 演示用测试客户和资金账户
-- ────────────────────────────────────────────────────────────────────
-- 给 5 个不同业务场景的测试客户 + 对应预付余额账户，
-- 让操作员能在生产环境里跑端到端业务流（不影响真客户）。
-- 客户名都以"测试客户-" 开头以便识别和清理。
-- ────────────────────────────────────────────────────────────────────

BEGIN;

-- 1) 美国跨境电商 (USD prepay, 高频)
INSERT INTO customers (tenant_id, code, name, contacts, mobile, email,
                       address, default_currency, credit_limit)
SELECT '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid,
       'TEST-US-001', '测试客户-美国电商A',
       'John Smith', '+1-415-555-0101', 'demo+us@example.com',
       'San Francisco, CA, US', 'USD', NULL
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE code='TEST-US-001');

-- 2) 欧洲电商 (EUR 月结 + 授信)
INSERT INTO customers (tenant_id, code, name, contacts, mobile, email,
                       address, default_currency, credit_limit)
SELECT '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid,
       'TEST-EU-001', '测试客户-欧洲电商B',
       'Hans Mueller', '+49-30-555-0102', 'demo+eu@example.com',
       'Berlin, DE', 'EUR', 50000.00
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE code='TEST-EU-001');

-- 3) 国内出口代理 (CNY 月结 + 低授信)
INSERT INTO customers (tenant_id, code, name, contacts, mobile, email,
                       address, default_currency, credit_limit)
SELECT '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid,
       'TEST-CN-001', '测试客户-国内出口代理',
       '王经理', '13888888888', 'demo+cn@example.com',
       '深圳市福田区', 'CNY', 10000.00
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE code='TEST-CN-001');

-- 4) 大客户测试 (高授信, 用于测试授信不足)
INSERT INTO customers (tenant_id, code, name, contacts, mobile, email,
                       address, default_currency, credit_limit)
SELECT '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid,
       'TEST-VIP-001', '测试客户-大客户VIP',
       '李总', '13999999999', 'demo+vip@example.com',
       '上海市浦东新区', 'CNY', 1000000.00
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE code='TEST-VIP-001');

-- 5) 新客户 (无授信, 用于测试 prepay 必须)
INSERT INTO customers (tenant_id, code, name, contacts, mobile, email,
                       address, default_currency, credit_limit)
SELECT '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid,
       'TEST-NEW-001', '测试客户-新客户预付',
       '张三', '13777777777', 'demo+new@example.com',
       '广州市天河区', 'CNY', 0
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE code='TEST-NEW-001');

-- 给前 3 个客户配预付余额账户
INSERT INTO financial_accounts (tenant_id, owner_type, owner_id, account_name,
                                 account_type, currency, balance, status,
                                 source, metadata, is_show)
SELECT c.tenant_id, 'CUSTOMER', c.id, c.name || ' ' || cur.code || ' 预付余额',
       'VIRTUAL', cur.code, cur.balance, 'ACTIVE',
       'LOCAL', '{"purpose":"customer-api.balance","seed":true,"demo":true}'::jsonb,
       true
  FROM customers c
  JOIN (VALUES ('USD', 5000.0::numeric), ('EUR', 10000.0::numeric), ('CNY', 50000.0::numeric))
       AS cur(code, balance) ON true
 WHERE c.code IN ('TEST-US-001','TEST-EU-001','TEST-CN-001','TEST-VIP-001','TEST-NEW-001')
   AND NOT EXISTS (
     SELECT 1 FROM financial_accounts fa
      WHERE fa.owner_id = c.id AND fa.currency = cur.code
        AND fa.metadata->>'purpose' = 'customer-api.balance'
   );

-- NEW-001 客户清空预付，强制场景测 prepay 必须
UPDATE financial_accounts SET balance = 0
 WHERE owner_id = (SELECT id FROM customers WHERE code='TEST-NEW-001')
   AND metadata->>'purpose' = 'customer-api.balance';

COMMIT;

-- 期望:
--   customers 3+5 = 8 行 (含 TEST-* 5 个)
--   financial_accounts 多出 15 行 (5客户 × 3 币种)
