package com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request

enum class ConsistencyLevel(private val code: Int) {
    Strong(0),
    Bounded(2),
    Eventually(3),
}