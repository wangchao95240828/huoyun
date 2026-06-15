-- 085: 3D 配载方案表 — 整柜装箱求解结果落地
-- ────────────────────────────────────────────────────────────────────
-- 一份 plan = 一个柜的方案；一行 plan_item = 一件货的放置位置。
-- 用 Python 求解器 (jerry800416/3D-bin-packing) 算完后落库，
-- 供配载中心 3D 透视图渲染 + 后续装柜/卸柜调度。
-- ────────────────────────────────────────────────────────────────────

BEGIN;

CREATE TABLE IF NOT EXISTS stowage_plans (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            uuid NOT NULL REFERENCES tenants(id),
    plan_no              text NOT NULL,
    container_code       text NOT NULL,                  -- 柜代号 如 '40HC-001'
    container_length_cm  numeric(10,2) NOT NULL,
    container_width_cm   numeric(10,2) NOT NULL,
    container_height_cm  numeric(10,2) NOT NULL,
    container_max_weight_kg numeric(12,2) NOT NULL,
    -- 客户路线（JSON 数组），如 ["A","B","C"] = A 先卸
    route                jsonb NOT NULL DEFAULT '[]'::jsonb,
    enable_lifo          boolean NOT NULL DEFAULT true,
    -- 求解结果
    status               text NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','SOLVED','APPROVED','LOADED','CLOSED')),
    fitted_count         int,
    unfitted_count       int,
    volume_utilization   numeric(6,4),                   -- 0-1
    weight_used_kg       numeric(12,2),
    gravity_center_x_cm  numeric(10,2),
    gravity_center_y_cm  numeric(10,2),
    gravity_center_z_cm  numeric(10,2),
    gravity_quadrants    jsonb,                          -- [q1,q2,q3,q4] 4 象限重量比
    warnings             jsonb DEFAULT '[]'::jsonb,
    unfitted_skus        jsonb DEFAULT '[]'::jsonb,
    -- 元数据
    remark               text,
    created_by           uuid REFERENCES users(id),
    created_at           timestamptz NOT NULL DEFAULT now(),
    solved_at            timestamptz,
    approved_by          uuid REFERENCES users(id),
    approved_at          timestamptz,
    UNIQUE (tenant_id, plan_no)
);

CREATE INDEX IF NOT EXISTS idx_stowage_plans_status
    ON stowage_plans (tenant_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS stowage_plan_items (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            uuid NOT NULL REFERENCES tenants(id),
    plan_id              uuid NOT NULL REFERENCES stowage_plans(id) ON DELETE CASCADE,
    sku                  text NOT NULL,
    customer_id          uuid REFERENCES customers(id),
    shipment_id          uuid REFERENCES shipments(id),
    carton_id            uuid REFERENCES cartons(id),
    -- 求解器输入
    input_length_cm      numeric(10,2),
    input_width_cm       numeric(10,2),
    input_height_cm      numeric(10,2),
    input_weight_kg      numeric(12,3),
    this_side_up         boolean DEFAULT false,
    fragile              boolean DEFAULT false,
    load_bearing_kg      numeric(12,2) DEFAULT 0,
    -- 求解器输出（放置坐标）
    placed               boolean NOT NULL DEFAULT false,
    x_cm                 numeric(10,2),
    y_cm                 numeric(10,2),
    z_cm                 numeric(10,2),
    rotation_type        int,                             -- 0-5
    -- 旋转后的实际有效尺寸（用于 3D 渲染）
    placed_length_cm     numeric(10,2),
    placed_width_cm      numeric(10,2),
    placed_height_cm     numeric(10,2),
    customer_priority    int DEFAULT 0,
    created_at           timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_stowage_plan_items_plan
    ON stowage_plan_items (plan_id);

COMMIT;
