package com.tencent.bkrepo.repository.pojo.schedule

enum class ScheduledDownloadConflictStrategy {
    /**
     * 跳过冲突的文件
     */
    SKIP,

    /**
     * 覆盖当前文件
     */
    OVERWRITE, // 覆盖

    /**
     * 重命名冲突文件
     */
    RENAME,

    /**
     * 直接失败
     */
    FAILED;
}
