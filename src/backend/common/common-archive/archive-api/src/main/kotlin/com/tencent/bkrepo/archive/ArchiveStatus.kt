package com.tencent.bkrepo.archive

enum class ArchiveStatus {
    /**
     * 已创建
     * */
    CREATED,

    /**
     * 归档中
     * */
    ARCHIVING,

    /**
     * 已归档
     * */
    ARCHIVED,

    /**
     * 已完成
     * */
    COMPLETED,

    /**
     * 待恢复
     * */
    WAIT_TO_RESTORE,

    /**
     * 恢复中
     * */
    RESTORING,

    /**
     * 已恢复
     * */
    RESTORED,

    /**
     * 归档失败
     * */
    ARCHIVE_FAILED,

    /**
     * 恢复失败
     * */
    RESTORE_FAILED,
}
