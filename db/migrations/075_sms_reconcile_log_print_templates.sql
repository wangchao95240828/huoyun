-- 075: SMS 自动核销日志 + 打印模板 + 自定义字段框架

BEGIN;

-- ═══ SMS 核销日志 ═══
CREATE TABLE IF NOT EXISTS acc_received_sms_reconcile_log (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sms_id      uuid NOT NULL REFERENCES acc_received_sms(id) ON DELETE CASCADE,
    invoice_id  uuid REFERENCES customer_invoices(id),
    amount      numeric(14,2) NOT NULL,
    status      text NOT NULL DEFAULT 'AUTO',
    matched_at  timestamptz NOT NULL DEFAULT now(),
    matched_by  uuid,
    note        text,
    CONSTRAINT acc_received_sms_reconcile_log_status_check CHECK (status IN ('AUTO','MANUAL','REVERSED')),
    CONSTRAINT acc_received_sms_reconcile_log_sms_invoice_unique UNIQUE (sms_id, invoice_id)
);

CREATE INDEX IF NOT EXISTS idx_acc_received_sms_reconcile_log_sms
    ON acc_received_sms_reconcile_log (sms_id);
CREATE INDEX IF NOT EXISTS idx_acc_received_sms_reconcile_log_invoice
    ON acc_received_sms_reconcile_log (invoice_id);

-- ═══ 打印模板表 ═══
CREATE TABLE IF NOT EXISTS acc_print_templates (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid NOT NULL DEFAULT '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid,
    code          text NOT NULL,
    name          text NOT NULL,
    category      text NOT NULL,                   -- LABEL / INVOICE / HANDOVER / BATTERY_LETTER
    page_size     text NOT NULL DEFAULT 'A4',     -- A4 / HALF_A4 / 4X6 / 100X150
    page_width_mm numeric(8,2),
    page_height_mm numeric(8,2),
    orientation   text NOT NULL DEFAULT 'PORTRAIT',
    template_html text NOT NULL,                    -- HTML/CSS body
    is_default    boolean NOT NULL DEFAULT false,
    is_active     boolean NOT NULL DEFAULT true,
    metadata      jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT acc_print_templates_tenant_code_unique UNIQUE (tenant_id, code),
    CONSTRAINT acc_print_templates_category_check
        CHECK (category IN ('LABEL','INVOICE','HANDOVER','BATTERY_LETTER','PACKING_LIST','MASTER_LABEL')),
    CONSTRAINT acc_print_templates_orientation_check
        CHECK (orientation IN ('PORTRAIT','LANDSCAPE'))
);

CREATE INDEX IF NOT EXISTS idx_acc_print_templates_category
    ON acc_print_templates (tenant_id, category, is_default DESC);

-- 种子标准打印模板（占位 HTML，生产可替换为 WYSIWYG 设计器输出）
INSERT INTO acc_print_templates (code, name, category, page_size, page_width_mm, page_height_mm, orientation, template_html, is_default) VALUES
    ('LABEL_4X6', '4x6 标签', 'LABEL', '4X6', 101.6, 152.4, 'PORTRAIT',
     '<div style="width:100mm;height:150mm;border:1px solid #000;padding:5mm;font-family:sans-serif"><h2>{tracking_no}</h2><div>{recipient_name}</div><div>{recipient_address}</div><div>{recipient_city}, {recipient_state} {recipient_postcode}</div><div>{recipient_country}</div><div>Weight: {weight_kg} kg</div></div>', true),
    ('LABEL_A4', 'A4 标签', 'LABEL', 'A4', 210, 297, 'PORTRAIT',
     '<div style="padding:20mm"><h1>{tracking_no}</h1><div style="font-size:18pt">{recipient_name}<br/>{recipient_address}<br/>{recipient_city}, {recipient_state} {recipient_postcode}<br/>{recipient_country}</div></div>', false),
    ('LABEL_HALF_A4', '半 A4 标签', 'LABEL', 'HALF_A4', 210, 148, 'LANDSCAPE',
     '<div style="padding:10mm"><h2>{tracking_no}</h2><div>{recipient_name}, {recipient_country}</div></div>', false),
    ('INVOICE_A4', 'A4 商业发票', 'INVOICE', 'A4', 210, 297, 'PORTRAIT',
     '<div><h1>Commercial Invoice</h1><div>Order: {order_no}</div><table><tr><th>Item</th><th>Qty</th><th>Price</th></tr>{items}</table></div>', true),
    ('PACKING_A4', 'A4 装箱单', 'PACKING_LIST', 'A4', 210, 297, 'PORTRAIT',
     '<div><h1>Packing List</h1><div>Shipment: {shipment_no}</div></div>', true),
    ('HANDOVER_A4', 'A4 交接清单', 'HANDOVER', 'A4', 210, 297, 'PORTRAIT',
     '<div><h1>Handover List</h1><table><thead><tr><th>No</th><th>Tracking</th><th>Customer</th><th>Weight</th></tr></thead><tbody>{rows}</tbody></table></div>', true),
    ('BATTERY_A4', 'A4 电池信', 'BATTERY_LETTER', 'A4', 210, 297, 'PORTRAIT',
     '<div><h1>Battery Letter</h1><p>This shipment contains lithium batteries...</p></div>', true)
ON CONFLICT (tenant_id, code) DO NOTHING;

-- ═══ 自定义字段框架 ═══
-- 客户可以为 orders/customers/shipments 等表添加自定义字段，存 jsonb metadata
CREATE TABLE IF NOT EXISTS acc_custom_fields (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid NOT NULL DEFAULT '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid,
    table_name   text NOT NULL,                    -- orders/customers/shipments/charges
    field_key    text NOT NULL,                    -- jsonb key
    field_label  text NOT NULL,
    field_type   text NOT NULL,                    -- text/number/date/select/boolean
    is_required  boolean NOT NULL DEFAULT false,
    is_unique    boolean NOT NULL DEFAULT false,
    options      jsonb,                            -- for select: [{v,l}]
    validation   jsonb,                            -- min/max/pattern/length
    sort_order   integer NOT NULL DEFAULT 0,
    is_active    boolean NOT NULL DEFAULT true,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT acc_custom_fields_tenant_table_key_unique UNIQUE (tenant_id, table_name, field_key),
    CONSTRAINT acc_custom_fields_field_type_check
        CHECK (field_type IN ('text','number','date','select','boolean','textarea'))
);

CREATE INDEX IF NOT EXISTS idx_acc_custom_fields_table
    ON acc_custom_fields (tenant_id, table_name, is_active, sort_order);

COMMIT;
