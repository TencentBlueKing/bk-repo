package com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request

enum class MetricType {
    INVALID,

    // Only for float vectors
    L2,
    IP,
    COSINE,

    // Only for binary vectors
    HAMMING,
    JACCARD,
}
