-- ACC 字段对齐 migration (第四批)
-- 来源：curl 抓取 ACC 真实页面 Online.php?act=Add / Product.php?act=Add / Customer.php?act=Add / Supplier.php?act=Add 字段映射
--
-- 1) shipments 加 ACC 制单完整字段 (收件人/发件人/进口商 共 30+ 字段)
-- 2) channels 加 ACC 产品配置字段 (材积/分抛/限制)
-- 3) customers/partners 加 ACC 通用字段 (Contacts/Mobile/Phone/Fax/Email/QQ/Product/Address/Grade)
-- 4) customers/partners 加结算条件字段 (DateType/FormulaDate/FormulaBill/FormulaType)
-- 5) customers/partners 加分段计费字段 (Amount1..7)

-- ─── shipments 收件人 (ACC 在线制单 → 收件人区) ───
ALTER TABLE shipments
  ADD COLUMN IF NOT EXISTS recipient_company    text,
  ADD COLUMN IF NOT EXISTS recipient_consignee  text,
  ADD COLUMN IF NOT EXISTS recipient_phone      text,
  ADD COLUMN IF NOT EXISTS recipient_province   text,
  ADD COLUMN IF NOT EXISTS recipient_city       text,
  ADD COLUMN IF NOT EXISTS recipient_tax_no     text,
  ADD COLUMN IF NOT EXISTS recipient_address    text,
  ADD COLUMN IF NOT EXISTS recipient_house_no   text,
  ADD COLUMN IF NOT EXISTS recipient_area_code  text;
COMMENT ON COLUMN shipments.recipient_area_code IS 'ACC 目的地代码 (输入后自动填国家邮编)';

-- ─── shipments 发件人 (Shipper*) ───
ALTER TABLE shipments
  ADD COLUMN IF NOT EXISTS shipper_company    text,
  ADD COLUMN IF NOT EXISTS shipper_consignee  text,
  ADD COLUMN IF NOT EXISTS shipper_phone      text,
  ADD COLUMN IF NOT EXISTS shipper_province   text,
  ADD COLUMN IF NOT EXISTS shipper_postcode   text,
  ADD COLUMN IF NOT EXISTS shipper_city       text,
  ADD COLUMN IF NOT EXISTS shipper_tax_no     text,
  ADD COLUMN IF NOT EXISTS shipper_address    text;

-- ─── shipments 进口商 (SoldTo*) ───
ALTER TABLE shipments
  ADD COLUMN IF NOT EXISTS sold_to_company    text,
  ADD COLUMN IF NOT EXISTS sold_to_consignee  text,
  ADD COLUMN IF NOT EXISTS sold_to_phone      text,
  ADD COLUMN IF NOT EXISTS sold_to_province   text,
  ADD COLUMN IF NOT EXISTS sold_to_postcode   text,
  ADD COLUMN IF NOT EXISTS sold_to_city       text,
  ADD COLUMN IF NOT EXISTS sold_to_tax_no     text,
  ADD COLUMN IF NOT EXISTS sold_to_address    text;

-- ─── shipments 货物信息 (ACC 申报明细) ───
ALTER TABLE shipments
  ADD COLUMN IF NOT EXISTS materials_en       text,
  ADD COLUMN IF NOT EXISTS materials_cn       text,
  ADD COLUMN IF NOT EXISTS battery_code       text,         -- 电池类型 code
  ADD COLUMN IF NOT EXISTS label_type         text,         -- 标签类型
  ADD COLUMN IF NOT EXISTS services           jsonb DEFAULT '[]'::jsonb;  -- ACC Services[] 多选服务
COMMENT ON COLUMN shipments.services IS 'ACC Services[] 多选附加服务 e.g. ["签收确认","保险"]';

-- ─── channels 加 ACC 产品配置 (Product.php?act=Add) ───
ALTER TABLE channels
  ADD COLUMN IF NOT EXISTS volume_modulus      numeric(10,4),  -- 材积值/换算系数
  ADD COLUMN IF NOT EXISTS weight_modulus      numeric(10,4),  -- 重量相乘系数
  ADD COLUMN IF NOT EXISTS limit_declare       numeric(14,2),  -- 最大申报
  ADD COLUMN IF NOT EXISTS limit_weight        numeric(10,3),  -- 最大重量
  ADD COLUMN IF NOT EXISTS limit_volume        numeric(10,4),  -- 最大材积
  ADD COLUMN IF NOT EXISTS min_weight_total    numeric(10,3),  -- 最低重量
  ADD COLUMN IF NOT EXISTS max_weight_warn     numeric(10,3),  -- 超重值
  ADD COLUMN IF NOT EXISTS max_length_warn     numeric(10,2),  -- 超长值
  ADD COLUMN IF NOT EXISTS limit_item_weight   numeric(10,3),  -- 单件限重
  ADD COLUMN IF NOT EXISTS min_item_weight     numeric(10,3),  -- 单件最低计重
  ADD COLUMN IF NOT EXISTS weight_ceil_unit    numeric(10,3),  -- 重量取整单位
  ADD COLUMN IF NOT EXISTS has_fuel            boolean DEFAULT false,
  ADD COLUMN IF NOT EXISTS split_ratio         numeric(6,4),   -- 分抛比例
  ADD COLUMN IF NOT EXISTS min_split           numeric(10,3),  -- 最低分抛实重
  ADD COLUMN IF NOT EXISTS weight_method       text,           -- PER_KG/PER_BOX
  ADD COLUMN IF NOT EXISTS allow_types         jsonb DEFAULT '[]'::jsonb,  -- 出货类型 Allow[]
  ADD COLUMN IF NOT EXISTS is_shipping         boolean DEFAULT true;       -- isShipping

-- ─── customers/partners ACC 通用 ───
ALTER TABLE customers
  ADD COLUMN IF NOT EXISTS contacts         text,         -- 联系人 (区分 contact_name)
  ADD COLUMN IF NOT EXISTS mobile           text,
  ADD COLUMN IF NOT EXISTS phone            text,
  ADD COLUMN IF NOT EXISTS fax              text,
  ADD COLUMN IF NOT EXISTS email            text,
  ADD COLUMN IF NOT EXISTS qq               text,
  ADD COLUMN IF NOT EXISTS main_product     text,
  ADD COLUMN IF NOT EXISTS address          text,
  ADD COLUMN IF NOT EXISTS grade            text,         -- 等级（A/B/C/VIP 等）
  ADD COLUMN IF NOT EXISTS settlement_type  text,         -- 现结/月结/账期
  ADD COLUMN IF NOT EXISTS date_type        text,         -- 结算条件类型（按周/按月/按天）
  ADD COLUMN IF NOT EXISTS formula_date     text,         -- 结算公式日期
  ADD COLUMN IF NOT EXISTS formula_bill     text,         -- 结算公式
  ADD COLUMN IF NOT EXISTS formula_type     text,         -- 结算公式类型
  ADD COLUMN IF NOT EXISTS amount_1         numeric(14,2),  -- 7 档阶梯金额 (ACC 客户/物流商通用结构)
  ADD COLUMN IF NOT EXISTS amount_2         numeric(14,2),
  ADD COLUMN IF NOT EXISTS amount_3         numeric(14,2),
  ADD COLUMN IF NOT EXISTS amount_4         numeric(14,2),
  ADD COLUMN IF NOT EXISTS amount_5         numeric(14,2),
  ADD COLUMN IF NOT EXISTS amount_6         numeric(14,2),
  ADD COLUMN IF NOT EXISTS amount_7         numeric(14,2);

ALTER TABLE partners
  ADD COLUMN IF NOT EXISTS contacts         text,
  ADD COLUMN IF NOT EXISTS mobile           text,
  ADD COLUMN IF NOT EXISTS phone            text,
  ADD COLUMN IF NOT EXISTS fax              text,
  ADD COLUMN IF NOT EXISTS email            text,
  ADD COLUMN IF NOT EXISTS qq               text,
  ADD COLUMN IF NOT EXISTS main_product     text,
  ADD COLUMN IF NOT EXISTS address          text,
  ADD COLUMN IF NOT EXISTS grade            text,
  ADD COLUMN IF NOT EXISTS settlement_type  text,
  ADD COLUMN IF NOT EXISTS date_type        text,
  ADD COLUMN IF NOT EXISTS formula_date     text,
  ADD COLUMN IF NOT EXISTS formula_bill     text,
  ADD COLUMN IF NOT EXISTS formula_type     text,
  ADD COLUMN IF NOT EXISTS credits          numeric(14,2),
  ADD COLUMN IF NOT EXISTS amount_1         numeric(14,2),
  ADD COLUMN IF NOT EXISTS amount_2         numeric(14,2),
  ADD COLUMN IF NOT EXISTS amount_3         numeric(14,2),
  ADD COLUMN IF NOT EXISTS amount_4         numeric(14,2),
  ADD COLUMN IF NOT EXISTS amount_5         numeric(14,2),
  ADD COLUMN IF NOT EXISTS amount_6         numeric(14,2),
  ADD COLUMN IF NOT EXISTS amount_7         numeric(14,2);
