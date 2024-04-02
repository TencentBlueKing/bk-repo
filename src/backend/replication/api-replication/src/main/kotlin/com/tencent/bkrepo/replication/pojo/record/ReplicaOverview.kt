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
    var conflict: Long = 0
)
