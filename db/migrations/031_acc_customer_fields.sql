-- ACC AccCustomersController 等 controller 字段补齐（阶段 2-C）。
--
-- 对照 docs/acc-gap-report-for-claude-2026-05-28.md §4 + 旧 ACC `Customer.php`：
--   - 旧 Customer.Group       → customers.customer_group_id
--   - 旧 Customer.SaleAccount  → customers.salesman_user_id（指向 auth.users）
--
-- 字段都允许 NULL，旧数据迁入时填 NULL；新建客户时由 UI 选填。
-- 这两列在前端只是展示用，无强制 FK 业务规则。

alter table customers
  add column if not exists customer_group_id uuid references customer_groups(id),
  add column if not exists salesman_user_id uuid;

-- 索引：客户列表常按 group 筛选
create index if not exists customers_group_idx
  on customers(tenant_id, customer_group_id)
  where customer_group_id is not null;

create index if not exists customers_salesman_idx
  on customers(tenant_id, salesman_user_id)
  where salesman_user_id is not null;
