-- ═════════════════════════════════════════════════════════════════════════
-- 059_acc_customer_api_keys
--
-- 给 api_credentials 表加 remark 字段，对齐 PHP CustomerAPI.php 的备注功能。
-- 幂等（IF NOT EXISTS）。
-- ═════════════════════════════════════════════════════════════════════════

ALTER TABLE api_credentials ADD COLUMN IF NOT EXISTS remark text;