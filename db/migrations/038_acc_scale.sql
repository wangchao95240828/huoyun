-- ACC acc/api/Scale.php 电子秤设备接口迁移（任务6）。
--
-- 对照 ACC：
--   - Scale 表           → scale_devices（设备配置：插件编号 + 设备硬件编码 hid）
--   - Scale_Item 表      → scale_records（称重记录：装箱单号/重量/长宽高/图片/幂等 hash）
--   - Goodscan.doReceive → 设备 POST 称重数据，md5(报文) 去重，落 Scale_Item
--   - Express 签入        → scale_records.shipment_id（制单时关联运单）

create table scale_devices (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  plugin_code text not null,            -- 接口编号（URL 路径段，对应 ACC Scale.Code）
  hid text not null,                    -- 设备硬件编码（doReceive 校验，对应 Goodscan HID）
  name text not null,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  unique (tenant_id, plugin_code)
);
alter table scale_devices enable row level security;
alter table scale_devices force row level security;
create policy scale_devices_tenant_isolation on scale_devices
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

create table scale_records (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  device_id uuid not null references scale_devices(id),
  no text not null,                     -- 装箱单号（对应 Scale_Item.No / Goodscan code）
  weight numeric(10, 3) not null check (weight > 0),
  length_mm numeric(10, 2) not null check (length_mm > 0),
  width_mm numeric(10, 2) not null check (width_mm > 0),
  height_mm numeric(10, 2) not null check (height_mm > 0),
  has_picture boolean not null default false,
  hash char(32) not null,               -- md5(原始报文)，幂等去重
  shipment_id uuid references shipments(id),  -- 签入运单（NULL = 未签入）
  received_at timestamptz not null default now(),
  unique (tenant_id, hash)
);
alter table scale_records enable row level security;
alter table scale_records force row level security;
create policy scale_records_tenant_isolation on scale_records
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 按装箱单号查 / 查未签入记录（制单签入用）
create index scale_records_no_idx on scale_records(tenant_id, no);
create index scale_records_unbound_idx
  on scale_records(tenant_id, device_id, received_at)
  where shipment_id is null;

-- 演示设备（与现有种子租户绑定，便于联调）
insert into scale_devices (tenant_id, plugin_code, hid, name)
select id, 'GOODSCAN-DEMO', 'HID-DEMO-001', 'Goodscan 演示电子秤'
from tenants
on conflict do nothing;
