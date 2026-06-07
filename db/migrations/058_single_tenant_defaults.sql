-- ═════════════════════════════════════════════════════════════════════════
-- 058_single_tenant_defaults
--
-- 背景：项目从多租户 SaaS 退化为单租户运行已 1 年（仅 1 个 tenant 行 = xqt）。
-- 全量重构 tenant_id 风险过高（217 张表 / 192 FK / 647 代码引用 / 903 migration 引用），
-- 采用渐进式策略 B：DB 层保留 tenant_id 列，给所有列加 DEFAULT，
-- 让新代码 INSERT 时可以省略 tenant_id 字段；老代码不动。
--
-- 单租户 ID：2bda8c16-7b19-4ce6-ab71-9584f5a140ed (code='xqt')
-- 可重复跑（幂等）。
-- ═════════════════════════════════════════════════════════════════════════

DO $$
DECLARE
  r record;
  default_tenant uuid := '2bda8c16-7b19-4ce6-ab71-9584f5a140ed';
  changed_count int := 0;
  skipped_count int := 0;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM tenants WHERE id = default_tenant) THEN
    RAISE EXCEPTION '默认租户 % 不存在！', default_tenant;
  END IF;

  FOR r IN
    SELECT c.table_name, c.column_default
    FROM information_schema.columns c
    JOIN information_schema.tables t USING (table_schema, table_name)
    WHERE c.column_name = 'tenant_id'
      AND c.table_schema = 'public'
      AND t.table_type = 'BASE TABLE'
    ORDER BY c.table_name
  LOOP
    -- 已有正确默认值的跳过（幂等）
    IF r.column_default IS NOT NULL AND r.column_default LIKE '%2bda8c16%' THEN
      skipped_count := skipped_count + 1;
      CONTINUE;
    END IF;
    BEGIN
      EXECUTE format(
        'ALTER TABLE %I ALTER COLUMN tenant_id SET DEFAULT %L::uuid',
        r.table_name, default_tenant
      );
      changed_count := changed_count + 1;
    EXCEPTION WHEN OTHERS THEN
      RAISE NOTICE '跳过 %: %', r.table_name, SQLERRM;
      skipped_count := skipped_count + 1;
    END;
  END LOOP;

  RAISE NOTICE 'Phase B-1: % 张表加了 DEFAULT, % 张跳过', changed_count, skipped_count;
END$$;

-- 验证：所有表都应有 DEFAULT
DO $$
DECLARE
  without_default_count int;
BEGIN
  SELECT count(*) INTO without_default_count
  FROM information_schema.columns c
  JOIN information_schema.tables t USING (table_schema, table_name)
  WHERE c.column_name='tenant_id' AND c.table_schema='public'
    AND t.table_type='BASE TABLE' AND c.column_default IS NULL;

  IF without_default_count > 0 THEN
    RAISE WARNING '仍有 % 张表的 tenant_id 没有 DEFAULT，需手工处理', without_default_count;
  ELSE
    RAISE NOTICE '✓ 全部 217 张表都有 DEFAULT，B-1 完成';
  END IF;
END$$;
