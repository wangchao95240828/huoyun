-- ACC AccWarehousesController + AccBranchesController 字段补齐（阶段 2-E + 2-F）。
--
-- 对照 docs/acc-gap-report-for-claude-2026-05-28.md §5.5 + §5.6 + 旧 ACC
-- `Warehouse.php` / `Branch.php`：
--   - warehouses：补 consignee / company / postcode
--   - organizations：补 contact / phone / address / remark
--
-- 这些都是展示性字段，无 FK 业务规则。

alter table warehouses
  add column if not exists consignee text,
  add column if not exists company text,
  add column if not exists postcode text;

alter table organizations
  add column if not exists contact text,
  add column if not exists phone text,
  add column if not exists address text,
  add column if not exists remark text;
