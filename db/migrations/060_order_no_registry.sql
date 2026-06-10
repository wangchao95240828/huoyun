-- ACC 风格 CompanyNo 单号生成的并发安全注册表
-- 参考 ACC inc/AdminClass.php:3724 getCompanyNo
-- 生成格式：YYYYMMDD + 3 位大写字母（A-Z 去 O，25^3=15625/天）
-- 用主键唯一约束抢占防并发：INSERT 成功即占有，失败重摇

CREATE TABLE IF NOT EXISTS order_no_registry (
  order_no    text PRIMARY KEY,
  tenant_id   uuid NOT NULL DEFAULT '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid,
  the_date    date NOT NULL,
  used_for    text NOT NULL DEFAULT 'ORDER',  -- ORDER / SHIPMENT / BILL / MAIN，对应 ACC 的 _No.Name
  created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_order_no_registry_date
  ON order_no_registry (tenant_id, the_date);
CREATE INDEX IF NOT EXISTS idx_order_no_registry_used_for
  ON order_no_registry (tenant_id, used_for, the_date);
