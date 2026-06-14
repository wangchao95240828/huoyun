-- 080: 修复 api_credentials secret_hash 列存了 sha256 hash 的历史脏数据
-- ────────────────────────────────────────────────────────────────────
-- 背景：
--   - migration 016 seed 直接把明文 APIKey 存入 secret_hash 列（与 ACC 签名兼容）。
--   - AccApiCredentialsController 在 2026-06 之前错把 sha256(secret) 存到该列，
--     导致客户拿到的 sk_* 明文永远签不出对的签名（SIGN_MISMATCH）。
--   - 已修复 controller 改回存明文。
-- 本迁移：
--   1. 把"看起来像 sha256/PBKDF2 base64"的存量 secret_hash 行标记 REVOKED，
--      强制管理员重新生成（仍可在 /api/acc/api-credentials/{id}/reset-secret 一键重置）。
--   2. 写入 remark 提示原因。
-- 检测规则：
--   - 长度 44 且尾部以 '=' 结尾  → 标准 sha256 base64
--   - 以 'pbkdf2$' 开头           → pbkdf2 hash
--   - 以 'sk_' 开头 不动           → 控制器新规格明文
--   - 32 字符纯 ASCII（含 seed）   → 不动
-- ────────────────────────────────────────────────────────────────────

BEGIN;

UPDATE api_credentials
SET status = 'REVOKED',
    remark = COALESCE(remark, '')
            || ' [auto-revoked 2026-06-14: stored secret_hash was an SHA-256 hash, '
            || 'must reset via /api/acc/api-credentials/{id}/reset-secret then re-share to customer]'
WHERE status = 'ACTIVE'
  AND (
        secret_hash ~ '^[A-Za-z0-9+/]{43}=$'        -- base64 sha256
     OR secret_hash LIKE 'pbkdf2$%'                  -- pbkdf2 prefix
  )
  -- 留出 seed 已经能用的明文行
  AND secret_hash NOT LIKE 'sk_%'
  AND secret_hash NOT IN (
        'DEMOAPIKEY32CHARS00000000000DEMO',
        'sk_plain_for_test',
        'dummy'
  );

COMMIT;
