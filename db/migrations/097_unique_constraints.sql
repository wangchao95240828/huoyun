-- ═════════════════════════════════════════════════════════════════════════
-- 097_unique_constraints
--
-- 修 P0-W1: charges + payments 表加 unique constraint 防重复提交
-- ACC PHP 业务层去重 (SELECT count WHERE) 容易 race condition, DB 层兜底更稳.
-- ═════════════════════════════════════════════════════════════════════════

-- payments (客户收款): 同租户 + reference_no 唯一 (允许 reference_no 为空时不约束)
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_ref
  ON payments (tenant_id, reference_no)
  WHERE reference_no IS NOT NULL AND reference_no <> '';

-- partner_payments (供应商付款): 同上
CREATE UNIQUE INDEX IF NOT EXISTS uq_partner_payments_ref
  ON partner_payments (tenant_id, reference_no)
  WHERE reference_no IS NOT NULL AND reference_no <> '';

-- charges (应收/应付明细): 同 shipment + charge_item + side 不能重复
-- ACC Charge.php:586 业务层去重逻辑同步到 DB 层
CREATE UNIQUE INDEX IF NOT EXISTS uq_charges_shipment_item_side
  ON charges (tenant_id, shipment_id, charge_item_id, side)
  WHERE settlement_status <> 'VOID' AND charge_item_id IS NOT NULL;

-- acc_finance_txns (调账/退款/罚款/返利): 单号唯一
CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_txns_no
  ON acc_finance_txns (tenant_id, txn_no)
  WHERE txn_no IS NOT NULL AND txn_no <> '';

COMMENT ON INDEX uq_payments_ref IS 'W1: 防客户收款重复提交';
COMMENT ON INDEX uq_charges_shipment_item_side IS 'W1: 同票同费用项同 AR/AP 唯一';
