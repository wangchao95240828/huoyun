-- 082: 给 seed 渠道账号补 endpoint_url 占位 + 把 provider_code 改成 NOOP（demo 用）
-- ────────────────────────────────────────────────────────────────────
-- 081 加的 seed 把 provider_code 填了 UPS/FEDEX/DHL 但 endpoint_url/api_key
-- 都是 NULL，导致 demo submit 直接调真实 UPS 接口报 NPE。
-- 此迁移把 seed 出来的"假"账号 provider_code 改成 NOOP（兜底取号），
-- 让 demo 业务能端到端跑通而不调真实 carrier。
--
-- 生产实际渠道账号客户在「API 对接中心 → 渠道账号」自己配真凭证 +
-- 改 provider_code=UPS/FEDEX/DHL 即可走真实接口。
-- ────────────────────────────────────────────────────────────────────

BEGIN;

UPDATE acc_channel_accounts
SET provider_code = 'NOOP',
    endpoint_url  = 'noop://demo',
    remark = COALESCE(remark, '')
            || ' [seed 占位账号，provider=NOOP 用于演示。生产需要换成真 UPS/FEDEX/DHL + endpoint_url + api_key/secret]'
WHERE account_no LIKE 'ACC-%'
  AND (endpoint_url IS NULL OR endpoint_url = '')
  AND (api_key IS NULL OR api_key = '');

COMMIT;
