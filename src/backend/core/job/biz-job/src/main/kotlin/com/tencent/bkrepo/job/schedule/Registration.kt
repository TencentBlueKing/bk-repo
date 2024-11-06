package com.tencent.bkrepo.job.schedule

import com.tencent.bkrepo.job.batch.base.BatchJob

/**
 * 任务注册中心
 * */
interface Registration {
    /**
     * 配置任务
     * */
    fun configureJobs(jobs: List<BatchJob<*>>)
}
