-- 079: approval_requests 加 MultiStageApprovalService 需要的列

BEGIN;

-- 我的 service 用了 resource_id/requested_by/required_count/resolved_at
-- 现表用 target_id/requester_id 但缺 required_count/resolved_at + reason 必填
ALTER TABLE approval_requests
    ADD COLUMN IF NOT EXISTS required_count integer NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS resolved_at    timestamptz,
    ADD COLUMN IF NOT EXISTS resource_id    text;

-- reason 改可空（service 没传）
ALTER TABLE approval_requests ALTER COLUMN reason DROP NOT NULL;

-- target_id 改可空（service 可能传 comma-separated string 到 resource_id）
ALTER TABLE approval_requests ALTER COLUMN target_id DROP NOT NULL;

-- approval_decisions 也确保有
CREATE TABLE IF NOT EXISTS approval_decisions (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id   uuid NOT NULL REFERENCES approval_requests(id) ON DELETE CASCADE,
    decided_by   uuid NOT NULL,
    decision     text NOT NULL,
    comment      text,
    decided_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT approval_decisions_decision_check
        CHECK (decision IN ('APPROVE','REJECT'))
);

CREATE INDEX IF NOT EXISTS idx_approval_decisions_request
    ON approval_decisions (request_id);

COMMIT;
