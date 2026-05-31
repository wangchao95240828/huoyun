-- ACC 基础信息 → 运费管理 → 文件管理
-- 用于上传/管理价格表、合同、面单模板等业务附件。
-- 与 documentcharges 模块（运单证件附加费）不同，这里是后台基础信息维护。

CREATE TABLE IF NOT EXISTS acc_files (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid NOT NULL,
    file_name     text NOT NULL,
    file_type     text,                  -- price_table / contract / template / other
    mime_type     text,
    size_bytes    bigint DEFAULT 0,
    storage_url   text,                  -- 对象存储 URL 或本地路径
    uploader_name text,
    uploader_id   uuid,
    remark        text,
    status        text DEFAULT 'ACTIVE', -- ACTIVE / DISABLED
    audit_status  text DEFAULT 'UNAUDITED',
    audited_at    timestamptz,
    audit_name    text,
    metadata      jsonb DEFAULT '{}',
    created_at    timestamptz DEFAULT now(),
    updated_at    timestamptz DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_acc_files_tenant      ON acc_files(tenant_id);
CREATE INDEX IF NOT EXISTS idx_acc_files_type        ON acc_files(file_type);
CREATE INDEX IF NOT EXISTS idx_acc_files_audit       ON acc_files(audit_status);
CREATE INDEX IF NOT EXISTS idx_acc_files_uploader    ON acc_files(uploader_id);

COMMENT ON TABLE  acc_files IS 'ACC 基础信息 - 文件管理（合同/价格表/模板等附件）';
