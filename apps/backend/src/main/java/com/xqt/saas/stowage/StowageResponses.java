package com.xqt.saas.stowage;

public final class StowageResponses {
    private StowageResponses() {
    }

    /** 对应 ACC act=Sync 响应 `$this->Data['data'] = $Stowage`，即新建/更新后的 Stowage ID。 */
    public record SyncResult(
        String stowageId,
        String stowageNo,
        int linkedItems,
        int detachedItems,
        boolean created
    ) {
    }
}
