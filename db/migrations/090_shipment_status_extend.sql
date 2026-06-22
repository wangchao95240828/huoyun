-- ═════════════════════════════════════════════════════════════════════════
-- 090_shipment_status_extend
--
-- 修 P0-C1: shipments.status 只 9 元, ACC 11 元 + 子流转态, 订单状态机不闭环
-- 加 7 个状态值对齐 ACC PHP ExpressStatus 完整模型.
--
-- ACC PHP 状态:
--   待提交 (DRAFT)        ← xqt 已有
--   已提交 (ORDERED)      ← xqt 已有
--   正常处理 (IN_WAREHOUSE/MEASURED/BOOKED/IN_TRANSIT/DELIVERED) ← xqt 已有
--   待确认 (CONFIRMING)   ← 新加: 申报/单据等客户确认
--   已扣件 (DETAINED)     ← 新加: 海关/承运商扣留
--   退件中 (RETURNING)    ← 新加: 退件申请到退件入仓
--   已退件 (RETURNED)     ← 新加: 退件完成
--   赔偿中 (CLAIMING)     ← 新加: 赔偿申请到审核
--   已赔偿 (CLAIMED)      ← 新加: 赔偿入账完成
--   异常件 (EXCEPTION)    ← xqt 已有 (泛指, 现在保留作 catch-all)
--   已作废 (VOID)         ← 新加: 作废审核通过
--
-- ALTER TYPE ADD VALUE 不能在事务里, 必须 IF NOT EXISTS 单独执行.
-- ═════════════════════════════════════════════════════════════════════════

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumtypid='shipment_status'::regtype AND enumlabel='CONFIRMING') THEN
        ALTER TYPE shipment_status ADD VALUE 'CONFIRMING' AFTER 'ORDERED';
    END IF;
END $$;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumtypid='shipment_status'::regtype AND enumlabel='DETAINED') THEN
        ALTER TYPE shipment_status ADD VALUE 'DETAINED';
    END IF;
END $$;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumtypid='shipment_status'::regtype AND enumlabel='RETURNING') THEN
        ALTER TYPE shipment_status ADD VALUE 'RETURNING';
    END IF;
END $$;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumtypid='shipment_status'::regtype AND enumlabel='RETURNED') THEN
        ALTER TYPE shipment_status ADD VALUE 'RETURNED';
    END IF;
END $$;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumtypid='shipment_status'::regtype AND enumlabel='CLAIMING') THEN
        ALTER TYPE shipment_status ADD VALUE 'CLAIMING';
    END IF;
END $$;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumtypid='shipment_status'::regtype AND enumlabel='CLAIMED') THEN
        ALTER TYPE shipment_status ADD VALUE 'CLAIMED';
    END IF;
END $$;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumtypid='shipment_status'::regtype AND enumlabel='VOID') THEN
        ALTER TYPE shipment_status ADD VALUE 'VOID';
    END IF;
END $$;
