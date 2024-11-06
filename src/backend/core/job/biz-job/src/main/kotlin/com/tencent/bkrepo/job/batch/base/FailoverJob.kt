package com.tencent.bkrepo.job.batch.base

/**
 * 支持故障转移的job
 * */
interface FailoverJob {
    /**
     * 故障转移
     * */
    fun failover()

    /**
     * 是否发生故障转移
     * */
    fun isFailover(): Boolean

    /**
     * 恢复现场
     */
    fun recover()
}
