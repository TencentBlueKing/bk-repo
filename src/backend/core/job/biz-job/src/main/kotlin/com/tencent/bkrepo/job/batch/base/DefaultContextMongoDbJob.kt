package com.tencent.bkrepo.job.batch.base

import com.tencent.bkrepo.job.config.properties.MongodbJobProperties

abstract class DefaultContextMongoDbJob<T : Any>(
    private val properties: MongodbJobProperties
) : MongoDbBatchJob<T, JobContext>(properties) {
    override fun createJobContext(): JobContext = JobContext()
}
