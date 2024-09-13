package com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.response

data class MilvusResponse<T>(
    val code: Int,
    val message: String,
    val data: T
)
