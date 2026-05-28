-- 任务 S7：员工/分公司/销售 数据可见性 RLS
--
-- 在已有 tenant_id RLS 基础上，叠加 RESTRICTIVE 策略：
--   - SALESMAN 只能看自己负责的客户
--   - BRANCH_MANAGER 只能看本分公司 shipments
--   - ADMIN / FINANCE 全 tenant 可见（不受限）
--
-- 关键：所有策略均为 RESTRICTIVE，且 "app.user_role IS NULL" 时视为不受限（无 user context）
-- → 现有未设置 user context 的链路（脚本、内部任务）行为不变，向后兼容。

-- ─── customers：销售可见性 ───
create policy customers_sales_visibility on customers
  as restrictive
  using (
    -- 没有 user context 时不限制（脚本 / 内部任务向后兼容）
    coalesce(nullif(current_setting('app.user_role', true), ''), '') = ''
    OR current_setting('app.user_role', true) in ('ADMIN', 'FINANCE')
    OR salesman_user_id::text = nullif(current_setting('app.user_id', true), '')
  );

-- ─── shipments：分公司可见性 ───
create policy shipments_branch_visibility on shipments
  as restrictive
  using (
    coalesce(nullif(current_setting('app.user_role', true), ''), '') = ''
    OR current_setting('app.user_role', true) in ('ADMIN', 'FINANCE')
    OR (current_setting('app.user_role', true) = 'BRANCH_MANAGER'
        AND branch_id::text = nullif(current_setting('app.user_branch_id', true), ''))
    OR (current_setting('app.user_role', true) = 'SALESMAN'
        AND customer_id in (
          select id from customers
          where salesman_user_id::text = nullif(current_setting('app.user_id', true), '')
        ))
  );

-- ─── orders：分公司可见性（按 branch_id）───
create policy orders_branch_visibility on orders
  as restrictive
  using (
    coalesce(nullif(current_setting('app.user_role', true), ''), '') = ''
    OR current_setting('app.user_role', true) in ('ADMIN', 'FINANCE')
    OR (current_setting('app.user_role', true) = 'BRANCH_MANAGER'
        AND branch_id::text = nullif(current_setting('app.user_branch_id', true), ''))
    OR (current_setting('app.user_role', true) = 'SALESMAN'
        AND customer_id in (
          select id from customers
          where salesman_user_id::text = nullif(current_setting('app.user_id', true), '')
        ))
  );
