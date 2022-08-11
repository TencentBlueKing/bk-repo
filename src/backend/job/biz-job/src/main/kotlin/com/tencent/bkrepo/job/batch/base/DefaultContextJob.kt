package com.tencent.bkrepo.job.batch.base

import com.tencent.bkrepo.job.config.properties.BatchJobProperties

abstract class DefaultContextJob(
    override val batchJobProperties: BatchJobProperties
) : CenterNodeJob<JobContext>(batchJobProperties) {
    override fun createJobContext(): JobContext = JobContext()
}
