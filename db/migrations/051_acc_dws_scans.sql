-- ACC 客服中心 → 收货 → DWS 实物分拣对接
--
-- DWS 推送每箱过机数据时：
--   action=check  → 查箱号是否在制单系统的 cartons 表里登记过
--   action=pickup → 写入实测重量/尺寸/照片，触发计费回写
--
-- 不直接 UPDATE cartons（cartons 是制单出货侧），改写一张独立的扫描流水表，
-- 由后续核算流程基于 acc_dws_scans 与 cartons 对账（重量差异 -> 调单）。

CREATE TABLE IF NOT EXISTS acc_dws_scans (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL,
    item_number     text NOT NULL,                -- 箱号（对应 cartons.carton_no）
    shipment_number text,                         -- 运单号（对应 shipments.shipment_no）
    shipment_id     uuid,                         -- FK shipments.id
    carton_id       uuid,                         -- FK cartons.id（匹配成功后回填）
    action          text NOT NULL,                -- 'check' / 'pickup' / 'update'
    weight_kg       numeric(10,3),
    length_cm       numeric(10,2),
    width_cm        numeric(10,2),
    height_cm       numeric(10,2),
    volume_weight   numeric(10,3),                -- 体积重 = L*W*H/6000
    chargeable_kg   numeric(10,3),                -- max(weight, volume_weight)
    pic_url         text,
    raw_payload     jsonb DEFAULT '{}',
    status          text NOT NULL DEFAULT 'OK',   -- OK / NOT_FOUND / DUP / ERROR
    info            text,                         -- 错误或提示信息
    scanned_at      timestamptz NOT NULL DEFAULT now(),
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_acc_dws_scans_item     ON acc_dws_scans(tenant_id, item_number);
CREATE INDEX IF NOT EXISTS idx_acc_dws_scans_shipment ON acc_dws_scans(shipment_id);
CREATE INDEX IF NOT EXISTS idx_acc_dws_scans_carton   ON acc_dws_scans(carton_id);
CREATE INDEX IF NOT EXISTS idx_acc_dws_scans_time     ON acc_dws_scans(scanned_at DESC);

COMMENT ON TABLE  acc_dws_scans                  IS 'DWS 实物分拣过机流水（客服中心 收货线）';
COMMENT ON COLUMN acc_dws_scans.action           IS 'check / pickup / update（DWS 推送的动作）';
COMMENT ON COLUMN acc_dws_scans.volume_weight    IS '体积重 kg = 长(cm)*宽*高/6000';
COMMENT ON COLUMN acc_dws_scans.chargeable_kg    IS '计费重 = max(实重, 体积重)';
COMMENT ON COLUMN acc_dws_scans.status           IS 'OK 入库; NOT_FOUND 箱号未找到; DUP 重复扫描; ERROR 其他失败';
