-- ═════════════════════════════════════════════════════════════════════════
-- 095_borrowing_interest_log
--
-- 修 P0-B4: 借款利息计提流水 (审计).
-- 每次调 /accrue-interest 写一行, 便于追溯历史利率/算法/金额.
-- ═════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS acc_borrowing_interest_log (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL DEFAULT current_setting('app.current_tenant_id')::uuid,
    borrowing_id    uuid NOT NULL REFERENCES acc_borrowings(id) ON DELETE CASCADE,
    as_of_date      date NOT NULL,
    months_elapsed  int NOT NULL,
    mode            text NOT NULL,
    rate            numeric(10,6) NOT NULL,
    principal       numeric(14,2) NOT NULL,
    interest        numeric(14,2) NOT NULL,
    total_due       numeric(14,2) NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_borrowing_interest_log_borrowing
    ON acc_borrowing_interest_log (borrowing_id, as_of_date DESC);

COMMENT ON TABLE acc_borrowing_interest_log IS 'B4: 借款利息计提流水审计';
