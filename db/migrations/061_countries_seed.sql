-- 灌 ACC 常用国家种子数据（27 条覆盖主流目的地）
-- 必须有，否则前端制单表单的"目的地"下拉为空，无法保存订单。
-- 用 ON CONFLICT DO NOTHING 保证幂等（按 tenant_id+code 唯一）

INSERT INTO countries (code, code3, cn_name, en_name, is_open) VALUES
  ('US', 'USA', '美国',       'United States',    true),
  ('CN', 'CHN', '中国',       'China',            true),
  ('HK', 'HKG', '中国香港',   'Hong Kong',        true),
  ('TW', 'TWN', '中国台湾',   'Taiwan',           true),
  ('GB', 'GBR', '英国',       'United Kingdom',   true),
  ('DE', 'DEU', '德国',       'Germany',          true),
  ('FR', 'FRA', '法国',       'France',           true),
  ('IT', 'ITA', '意大利',     'Italy',            true),
  ('ES', 'ESP', '西班牙',     'Spain',            true),
  ('NL', 'NLD', '荷兰',       'Netherlands',      true),
  ('JP', 'JPN', '日本',       'Japan',            true),
  ('KR', 'KOR', '韩国',       'South Korea',      true),
  ('SG', 'SGP', '新加坡',     'Singapore',        true),
  ('MY', 'MYS', '马来西亚',   'Malaysia',         true),
  ('TH', 'THA', '泰国',       'Thailand',         true),
  ('VN', 'VNM', '越南',       'Vietnam',          true),
  ('ID', 'IDN', '印度尼西亚', 'Indonesia',        true),
  ('IN', 'IND', '印度',       'India',            true),
  ('AU', 'AUS', '澳大利亚',   'Australia',        true),
  ('NZ', 'NZL', '新西兰',     'New Zealand',      true),
  ('CA', 'CAN', '加拿大',     'Canada',           true),
  ('MX', 'MEX', '墨西哥',     'Mexico',           true),
  ('BR', 'BRA', '巴西',       'Brazil',           true),
  ('AE', 'ARE', '阿联酋',     'United Arab Emirates', true),
  ('SA', 'SAU', '沙特阿拉伯', 'Saudi Arabia',     true),
  ('PL', 'POL', '波兰',       'Poland',           true),
  ('RU', 'RUS', '俄罗斯',     'Russia',           true)
ON CONFLICT DO NOTHING;
