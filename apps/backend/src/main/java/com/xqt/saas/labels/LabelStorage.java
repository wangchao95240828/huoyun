package com.xqt.saas.labels;

/**
 * 面单文件存储抽象。ACC 旧逻辑里写盘到 `<INC_PATH>/../label/YYYY-MM-DD/label_<Code>_<No>.pdf`
 * 同时把元数据写 `Online_File`（Hash/Ext/Time）；URL 直接拼 `BasePath + /label/...` 给客户端下载。
 *
 * 新平台用 hash-命名 + 日期目录布局，URL 由 storage 自己决定，业务层只看 StoredFile 元数据。
 * 默认实现 {@link LocalFileLabelStorage} 写本地磁盘；后续可替换为 S3/OSS 实现。
 */
public interface LabelStorage {
    StoredFile save(String tenantId, byte[] content, String fileExt);

    byte[] load(StoredFile file);

    /** 返回相对/绝对下载 URL，给客户端 method=1 时使用。 */
    String publicUrl(StoredFile file);

    record StoredFile(
        String fileHash,
        String fileExt,
        String storagePath,
        int fileSize,
        String createdDate
    ) {
    }
}
