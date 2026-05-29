-- 任务 S7 收口：BRANCH_MANAGER 双路 policy（严格匹配 + 空 branch 容错）。
--
-- 配合 AuthPrincipal.branchId 落地（commit 后续）+ users.branch_id JOIN：
--   - BRANCH_MANAGER 用户带 branchId → 严格 branch_id 匹配（真实分公司隔离）
--   - BRANCH_MANAGER 用户无 branchId（系统账号、未配置分公司） → 容错放行（避免零行 trap）
--
-- 与原 044 不同：044 强制匹配 → 任何无 branchId 的用户登录后看零行。
-- 047 保留容错口让"未配置 branch 的合法账号"不被锁死，但用户有 branch 时立即严格生效。

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
