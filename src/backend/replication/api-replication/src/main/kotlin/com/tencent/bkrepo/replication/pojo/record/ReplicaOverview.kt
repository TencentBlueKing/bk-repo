package com.tencent.bkrepo.replication.pojo.record

data class ReplicaOverview(
    /**
     * 成功数量
     */
    var success: Long = 0,
    /**
     * 失败数量
     */
    var failed: Long = 0,
    /**
     * 冲突数量
     */
    var conflict: Long = 0,
    /**
     * 文件成功数量
     */
    var fileSuccess: Long = 0,
    /**
     * 文件失败数量
     */
    var fileFailed: Long = 0,
)
