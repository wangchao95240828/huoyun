-- ═════════════════════════════════════════════════════════════════════════
-- 089_channel_account_surcharges
--
-- 修 P0-A1 线上必崩 BUG: acc_channel_accounts 缺 7 列, 但 RateRepository.java:264
-- SELECT 时直接 SELECT battery_a/b/c_fee, overweight_fee, overlength_fee,
-- processing_fee, surcharge_currency — 跑制单计费时必抛 "column does not exist".
--
-- 对应 ACC PHP Channel_Account 表的 BatteryA/BatteryB/BatteryC/Overweight/
-- Overlength/Processing 字段.
-- ═════════════════════════════════════════════════════════════════════════

ALTER TABLE acc_channel_accounts
  ADD COLUMN IF NOT EXISTS battery_a_fee     numeric(12,4) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS battery_b_fee     numeric(12,4) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS battery_c_fee     numeric(12,4) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS overweight_fee    numeric(12,4) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS overlength_fee    numeric(12,4) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS processing_fee    numeric(12,4) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS surcharge_currency char(3)      NOT NULL DEFAULT 'CNY';

COMMENT ON COLUMN acc_channel_accounts.battery_a_fee   IS 'A 电池附加费/票, ACC Channel_Account.BatteryA';
COMMENT ON COLUMN acc_channel_accounts.battery_b_fee   IS 'B 电池附加费/票, ACC Channel_Account.BatteryB';
COMMENT ON COLUMN acc_channel_accounts.battery_c_fee   IS 'C 电池附加费/票, ACC Channel_Account.BatteryC';
COMMENT ON COLUMN acc_channel_accounts.overweight_fee  IS '超重附加费/票, ACC Channel_Account.Overweight';
COMMENT ON COLUMN acc_channel_accounts.overlength_fee  IS '超长附加费/票, ACC Channel_Account.Overlength';
COMMENT ON COLUMN acc_channel_accounts.processing_fee  IS '操作附加费/票, ACC Channel_Account.Processing';
COMMENT ON COLUMN acc_channel_accounts.surcharge_currency IS '附加费币种, 默认 CNY';
