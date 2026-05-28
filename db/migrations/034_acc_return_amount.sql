-- AccReturnsController.amount 真实字段（阶段 2-G）。
--
-- 对照 docs/acc-gap-report-for-claude-2026-05-28.md §4 / §5.8 + 旧 ACC `Back.php`：
--   - 旧 ReturnOrder.Refund / Compensate → 进 return_orders 表
--
-- 业务上 amount = 退款金额（正）+ 补收金额（负），二者通常互斥；
-- 这里分两列，前端汇总展示为 `amount = refund - compensate` 即可。

alter table return_orders
  add column if not exists refund_amount numeric(14,2) not null default 0,
  add column if not exists compensate_amount numeric(14,2) not null default 0,
  add column if not exists currency char(3) not null default 'CNY';
