package com.tencent.bkrepo.job.config

import com.tencent.bkrepo.job.batch.base.JobConcurrentLevel

data class MongodbJobProperties(
    override var enabled: Boolean = true,
    /**
     * 并发级别
     * 默认序列化，即顺序执行
     * */
    var concurrentLevel: JobConcurrentLevel = JobConcurrentLevel.SERIALIZE,
    /**
     * 每秒任务执行数
     * */
    var permitsPerSecond: Double = 0.0
) : BatchJobProperties()
