-- 任务 S7 收口：BRANCH_MANAGER 在 user_branch_id 未配置时不应被锁死为零行。
--
-- 原 migration 044 写的 policy 在 BRANCH_MANAGER 时强制 branch_id = app.user_branch_id；
-- 但 RequestContext 目前总把 app.user_branch_id 设空字符串（AuthPrincipal 暂无 branchId），
-- 实战中 BRANCH_MANAGER 会一行都查不到——比"放行"更糟（功能假坏）。
--
-- 修复：BRANCH_MANAGER + branch context 为空时 → 视同 tenant 全可见（与 ADMIN 等价但有审计标记）。
-- 这是有意识的回退口：等 AuthPrincipal 补 branchId 后会自动激活真正的分支隔离。

drop policy if exists shipments_branch_visibility on shipments;
create policy shipments_branch_visibility on shipments
  as restrictive
  using (
    coalesce(nullif(current_setting('app.user_role', true), ''), '') = ''
    OR current_setting('app.user_role', true) in ('ADMIN', 'FINANCE')
    OR (current_setting('app.user_role', true) = 'BRANCH_MANAGER'
        AND (
          -- branch_id 未配置时不锁死（等 AuthPrincipal 补字段后此分支会真正生效）
          coalesce(nullif(current_setting('app.user_branch_id', true), ''), '') = ''
          OR branch_id::text = nullif(current_setting('app.user_branch_id', true), '')
        ))
    OR (current_setting('app.user_role', true) = 'SALESMAN'
        AND customer_id in (
          select id from customers
          where salesman_user_id::text = nullif(current_setting('app.user_id', true), '')
        ))
  );

drop policy if exists orders_branch_visibility on orders;
create policy orders_branch_visibility on orders
  as restrictive
  using (
    coalesce(nullif(current_setting('app.user_role', true), ''), '') = ''
    OR current_setting('app.user_role', true) in ('ADMIN', 'FINANCE')
    OR (current_setting('app.user_role', true) = 'BRANCH_MANAGER'
        AND (
          coalesce(nullif(current_setting('app.user_branch_id', true), ''), '') = ''
          OR branch_id::text = nullif(current_setting('app.user_branch_id', true), '')
        ))
    OR (current_setting('app.user_role', true) = 'SALESMAN'
        AND customer_id in (
          select id from customers
          where salesman_user_id::text = nullif(current_setting('app.user_id', true), '')
        ))
  );
