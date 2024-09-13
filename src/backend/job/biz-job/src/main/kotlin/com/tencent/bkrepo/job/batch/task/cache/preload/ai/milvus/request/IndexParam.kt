package com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request

import com.fasterxml.jackson.annotation.JsonProperty

data class IndexParam(
    var fieldName: String,
    val metricType: MetricType = MetricType.COSINE,
    val indexName: String? = null,
    val params: Map<String, Any>? = null,
)

data class Params(
    @JsonProperty("index_type")
    val indexType: IndexType = IndexType.AUTOINDEX,
    /**
     * The maximum degree of the node and applies only when index_type is set to HNSW.
     */
    @JsonProperty("M")
    val m: Int,
    /**
     * The search scope. This applies only when index_type is set to HNSW
     */
    val efConstruction: Int,
    /**
     * The number of cluster units. This applies to IVF-related index types.
     */
    val nlist: Int,
)