-- ═════════════════════════════════════════════════════════════════════════
-- 091_acc_ask_replies
--
-- 修 P0-C8: 问题件回复线索表完全缺. ACC Ask_Reply 表是问题件多人协作的核心,
-- 每一条 reply 带 To (TO_SERVICE/OPERATING/CUSTOMER/SUPPLIER/CLOSED) 路由
-- 客户可见性 + handle 联动 (申请扣件→Detain, 申请退件→Back, 申请赔偿→Reparation).
--
-- xqt-saas 之前 acc_asks 只有 content 单字段, 无对话线索. 加 acc_ask_replies +
-- 给 acc_asks 加 to/ask_type 字段对齐 ACC.
-- ═════════════════════════════════════════════════════════════════════════

-- 1. 问题件回复线索表
CREATE TABLE IF NOT EXISTS acc_ask_replies (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL DEFAULT current_setting('app.current_tenant_id')::uuid,
    ask_id          uuid NOT NULL REFERENCES acc_asks(id) ON DELETE CASCADE,
    -- 回复来源: STAFF(内部客服) / CUSTOMER(客户) / SUPPLIER(供应商) / SYSTEM(自动)
    source          text NOT NULL CHECK (source IN ('STAFF','CUSTOMER','SUPPLIER','SYSTEM')),
    add_name        text,
    content         text NOT NULL,
    -- 路由: 这条 reply 把问题件 To 路由改成什么 (CUSTOMER/SUPPLIER/STAFF/CLOSED)
    to_role         text CHECK (to_role IN ('CUSTOMER','SUPPLIER','STAFF','CLOSED')),
    -- 客户是否可见(部分 reply 内部讨论, 客户看不到)
    is_show         boolean NOT NULL DEFAULT true,
    -- handle: 0=普通回复 / 1=申请扣件 / 2=解除扣件 / 3=申请退件 / 4=申请赔偿 / 5=撤销赔偿 / 6=关闭
    handle          smallint NOT NULL DEFAULT 0,
    handle_ref_id   uuid,  -- handle 联动产生的关联 (detain_id / return_id / reparation_id)
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_acc_ask_replies_ask ON acc_ask_replies (ask_id, created_at);

-- 2. acc_asks 加 to_role / ask_type 字段 (对齐 ACC 16 档 Type 矩阵)
ALTER TABLE acc_asks
  ADD COLUMN IF NOT EXISTS to_role text DEFAULT 'STAFF'
    CHECK (to_role IN ('CUSTOMER','SUPPLIER','STAFF','CLOSED'));

-- 3. 加 ask_type 标准化 (ACC PHP 用 Type 数字, 我们用 text 易读)
--   QUERY=查件 / ABNORMAL=异常 / DETAIN=扣件 / RETURN=退件 / REPARATION=赔偿 /
--   ADDRESS=地址有误 / LOST=丢失 / DAMAGE=破损 / DELAY=延误 / COMPLAINT=投诉 /
--   CUSTOMS=海关 / WEIGHT=重量异议 / FEE=费用异议 / OTHER=其它
-- (ask_type 列原本就有, 现在不约束枚举值, 兼容历史数据)

COMMENT ON TABLE acc_ask_replies IS '问题件回复线索 - ACC Ask_Reply 对齐';
COMMENT ON COLUMN acc_ask_replies.handle IS '0=普通/1=申扣件/2=解扣件/3=申退件/4=申赔偿/5=撤赔偿/6=关闭';
COMMENT ON COLUMN acc_ask_replies.handle_ref_id IS 'handle 联动产生的 detain/return/reparation id';
COMMENT ON COLUMN acc_asks.to_role IS '问题件当前路由角色 (谁需要处理): CUSTOMER/SUPPLIER/STAFF/CLOSED';
