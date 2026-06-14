-- 081: 业务种子数据 — 渠道账号 / 仓库 / 进口商模板 / 报关地址
-- ────────────────────────────────────────────────────────────────────
-- 替换上线前的占位数据。所有 INSERT 用 NOT EXISTS / ON CONFLICT 幂等。
-- 后续业务需要可以在 /api/acc/{...} 管理页继续添加。
-- ────────────────────────────────────────────────────────────────────

BEGIN;

-- ════════ 1. 渠道账号 (acc_channel_accounts) ════════
-- 为每个 channels.code 配 1 个示例账号，partner_id 留空
INSERT INTO acc_channel_accounts (
    tenant_id, channel_id, account_no, account_name, provider_code,
    is_active, audit_status, remark
)
SELECT '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid,
       ch.id,
       'ACC-' || ch.code,
       ch.name || ' 主账号',
       CASE
         WHEN ch.code LIKE 'UPS-%' THEN 'UPS'
         WHEN ch.code LIKE '%FEDEX%' THEN 'FEDEX'
         WHEN ch.code LIKE 'EU-%' THEN 'DHL'
         ELSE 'GENERIC'
       END,
       true, 'AUDITED',
       '系统种子，账号代码与 channel code 对应'
  FROM channels ch
 WHERE ch.tenant_id = '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid
   AND NOT EXISTS (
       SELECT 1 FROM acc_channel_accounts a
        WHERE a.channel_id = ch.id
          AND a.account_no = 'ACC-' || ch.code
   );

-- ════════ 2. 仓库 (warehouses) ════════
INSERT INTO warehouses (
    tenant_id, code, name, warehouse_type, country_code, province, city,
    address, status, audit_status, consignee, company, postcode
)
SELECT '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid,
       v.code, v.name, v.warehouse_type, v.country_code, v.province, v.city,
       v.address, 'ACTIVE', 'AUDITED', v.consignee, v.company, v.postcode
  FROM (VALUES
    ('WH-SZ',  '深圳福永总仓',  'DOMESTIC', 'CN', '广东省', '深圳市', '宝安区福永街道塘尾工业区 A栋',    '收货部',   '新航线物流（深圳）', '518103'),
    ('WH-HK',  '香港中转仓',    'TRANSIT',  'HK', NULL,    '香港',   '荃湾区荃湾大厦 12 楼',         '中转部',   '新航线物流（香港）', '999077'),
    ('WH-SH',  '上海浦东仓',    'DOMESTIC', 'CN', '上海市', '上海市', '浦东新区航城西路 88 号',        '收货部',   '新航线物流（上海）', '201300'),
    ('WH-LAX', 'Los Angeles 海外仓', 'OVERSEAS', 'US', 'CA', 'Los Angeles', '1234 Industrial Pkwy, Compton CA',   'Receiving', 'XQT Logistics USA Inc',  '90220'),
    ('WH-LON', 'London 海外仓',     'OVERSEAS', 'GB', NULL, 'London',      '20 Acre Lane, Lambeth London',       'Receiving', 'XQT Logistics UK Ltd',   'SW2 5SG')
  ) AS v(code, name, warehouse_type, country_code, province, city, address, consignee, company, postcode)
 WHERE NOT EXISTS (
       SELECT 1 FROM warehouses w
        WHERE w.tenant_id = '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid
          AND w.code = v.code
   );

-- ════════ 3. 进口商模板 (acc_importer_templates) ════════
INSERT INTO acc_importer_templates (
    tenant_id, name, country, tax_id, address, contact_name, contact_phone
)
SELECT '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid,
       v.name, v.country, v.tax_id, v.address, v.contact_name, v.contact_phone
  FROM (VALUES
    ('US 通用 IOR',          'US', 'EIN-99-1234567', '300 Spectrum Center Dr, Irvine CA 92618',        'IOR Manager',     '+1-949-555-0188'),
    ('UK 通用 IOR',          'GB', 'GB987654321',     '15 Brunel Building, London W2 1AT',              'Compliance Dept', '+44-20-7946-0123'),
    ('德国 EORI 标准 IOR',   'DE', 'DE123456789',     'Friedrichstraße 90, 10117 Berlin',                'Zoll Manager',    '+49-30-555-0199'),
    ('澳大利亚 ABN 标准 IOR', 'AU', '12345678901',     '1 Macquarie Place, Sydney NSW 2000',             'Customs Officer', '+61-2-9555-0144'),
    ('加拿大 BN 标准 IOR',    'CA', '123456789RT0001', '100 King St W, Toronto ON M5X 1A9',             'Import Manager',  '+1-416-555-0166')
  ) AS v(name, country, tax_id, address, contact_name, contact_phone)
 WHERE NOT EXISTS (
       SELECT 1 FROM acc_importer_templates t
        WHERE t.tenant_id = '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid
          AND t.name = v.name
   );

COMMIT;

-- 期望数量：channel-accounts 1+9=10、warehouses 2+5=7、importer-templates 1+5=6
