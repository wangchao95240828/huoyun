-- ACC 客服中心 → 收货 主表
--
-- 收货 (inbound) 跟制单 (orders/shipments/cartons) 是两条独立业务线：
--   制单：用我们 UPS/Fedex 账号代发，不过机，等 carrier 账单回填重量
--   收货：客户发货到我们仓库，必须过 DWS，按表价立即计费
--
-- 之前误把 DWS 接到 cartons 表，现纠正：DWS 只读写 acc_inbound_parcels。

CREATE TABLE IF NOT EXISTS acc_inbound_parcels (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid NOT NULL,
    customer_id      uuid,                    -- 客户（货主）
    branch_id        uuid,                    -- 收货分公司
    parcel_no        text NOT NULL,           -- 箱号 = DWS item_number
    tracking_no      text,                    -- 客户原始单号 / 内部 tracking
    waybill_no       text,                    -- 内部运单号（多箱合并时用）
    -- 预报数据（客户提前填，可空）
    expected_weight  numeric(10,3),
    -- DWS 实测数据
    actual_weight    numeric(10,3),
    length_cm        numeric(10,2),
    width_cm         numeric(10,2),
    height_cm        numeric(10,2),
    volume_weight    numeric(10,3),
    chargeable_kg    numeric(10,3),
    cbm              numeric(10,4),
    -- 计费相关
    channel_id       uuid,                    -- 渠道
    destination_country char(2),
    destination_postal_code text,
    zone             text,                    -- 分区代码
    rate_amount      numeric(14,2),           -- 表价计算出的费用
    currency         char(3) DEFAULT 'CNY',
    charge_id        uuid,                    -- 生成的应收 charge 关联
    status           text NOT NULL DEFAULT 'PENDING',
        -- PENDING  预报已录入，等过机
        -- SCANNED  DWS 已扫描（实测落库）
        -- CHARGED  已按表价生成应收
        -- SHIPPED  已出货
        -- CANCELLED 取消
    pic_url          text,
    received_at      timestamptz,             -- 第一次 DWS 扫描时间
    audit_status     text NOT NULL DEFAULT 'PENDING',
    audited_at       timestamptz,
    audit_name       text,
    add_name         text,
    metadata         jsonb DEFAULT '{}',
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT acc_inbound_parcels_status_check CHECK
        (status = ANY (ARRAY['PENDING','SCANNED','CHARGED','SHIPPED','CANCELLED'])),
    CONSTRAINT acc_inbound_parcels_audit_check CHECK
        (audit_status = ANY (ARRAY['PENDING','AUDITED','UNAUDITED']))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_acc_inbound_parcels_no
    ON acc_inbound_parcels(tenant_id, parcel_no);
CREATE INDEX IF NOT EXISTS idx_acc_inbound_parcels_customer
    ON acc_inbound_parcels(tenant_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_acc_inbound_parcels_status
    ON acc_inbound_parcels(status);
CREATE INDEX IF NOT EXISTS idx_acc_inbound_parcels_received_at
    ON acc_inbound_parcels(received_at DESC);

COMMENT ON TABLE  acc_inbound_parcels             IS 'ACC 收货入仓主表（DWS 实测落地）';
COMMENT ON COLUMN acc_inbound_parcels.parcel_no   IS '箱号，DWS 推送时按此查';
COMMENT ON COLUMN acc_inbound_parcels.expected_weight IS '客户预报重量（与实测对比用于差异分析）';
COMMENT ON COLUMN acc_inbound_parcels.rate_amount IS '按表价（zone+weight+channel+fuel）计算出的应收金额';
COMMENT ON COLUMN acc_inbound_parcels.status      IS 'PENDING(预报) → SCANNED(过机) → CHARGED(已计费) → SHIPPED → CANCELLED';

-- acc_dws_scans 关联从 cartons 改到 inbound_parcels（保留 carton_id 字段做兼容）
ALTER TABLE acc_dws_scans
    ADD COLUMN IF NOT EXISTS inbound_parcel_id uuid;
CREATE INDEX IF NOT EXISTS idx_acc_dws_scans_inbound
    ON acc_dws_scans(inbound_parcel_id);
COMMENT ON COLUMN acc_dws_scans.inbound_parcel_id IS '匹配成功的 inbound_parcel id（替代 carton_id）';
