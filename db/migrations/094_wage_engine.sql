-- ═════════════════════════════════════════════════════════════════════════
-- 094_wage_engine
--
-- 修 P0-D5: HR 工资引擎 MVP. ACC Wage.php 3150 行的核心三段:
--   total = basic + bonus + commission - deduction (社保/借支抵扣)
-- 之前 acc_wages 已建好 (basic/bonus/commission/deduction/total), 但 controller 只 CRUD,
-- 无引擎. 这版加:
--   1. acc_employees 加 basic_salary 字段 (员工默认基本工资)
--   2. acc_wage_items 明细表 (扣项透明化: 社保/公积金/借支/罚款)
--   3. POST /wages/calculate?month=YYYY-MM (主流程)
-- ═════════════════════════════════════════════════════════════════════════

ALTER TABLE acc_employees ADD COLUMN IF NOT EXISTS basic_salary numeric(12,2) DEFAULT 0;

CREATE TABLE IF NOT EXISTS acc_wage_items (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL DEFAULT current_setting('app.current_tenant_id')::uuid,
    wage_id     uuid NOT NULL REFERENCES acc_wages(id) ON DELETE CASCADE,
    -- item_type: BASIC(基本工资) / BONUS(奖金) / COMMISSION(提成, 从 acc_commissions 取)
    --            SOCIAL_INSURANCE(社保扣减) / HOUSING_FUND(公积金扣减)
    --            BORROWING_REPAY(借支抵扣, 从 acc_borrowings 取)
    --            FINE(罚款扣减) / ADJUST(其它调整) / TAX(个税)
    item_type   text NOT NULL CHECK (item_type IN (
        'BASIC','BONUS','COMMISSION','SOCIAL_INSURANCE','HOUSING_FUND',
        'BORROWING_REPAY','FINE','ADJUST','TAX')),
    -- direction: ADD(加项) / SUB(扣项)
    direction   text NOT NULL CHECK (direction IN ('ADD','SUB')),
    amount      numeric(14,2) NOT NULL DEFAULT 0,
    source_type text,  -- 'acc_commissions' / 'acc_borrowings' / 'acc_socials' 等
    source_id   uuid,  -- 关联 id 便于追溯
    remark      text,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_acc_wage_items_wage ON acc_wage_items (wage_id);

COMMENT ON TABLE acc_wage_items IS '工资明细行 - 加项/扣项透明化追溯';
COMMENT ON COLUMN acc_employees.basic_salary IS '员工月度基本工资 (D5 工资引擎默认取此, 个性化可在 wage 表覆盖)';
