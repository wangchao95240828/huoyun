-- ACC AccSuppliersController 字段补齐（阶段 2-D）。
--
-- 对照 docs/acc-gap-report-for-claude-2026-05-28.md §5.4 + 旧 ACC `Supplier.php`：
--   - 旧 Supplier.Contact / Mobile / Phone → 进 partners 表新增 3 列
--   - 旧 Supplier.Product → 由 partners ↔ channels 关联派生（不入表，直接 SQL 聚合）
--   - 旧 Supplier.Balance → 由 AP charges + partner_payments 聚合（不入表）
--
-- 仅 partners 表加 3 列；其它通过 join 实现。

alter table partners
  add column if not exists contact_name text,
  add column if not exists contact_mobile text,
  add column if not exists contact_phone text;
