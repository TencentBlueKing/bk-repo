package com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request

import com.fasterxml.jackson.annotation.JsonProperty

data class IndexParam(
    var fieldName: String,
    val metricType: String = MetricType.COSINE.name,
    val indexName: String? = null,
    val params: Params? = null,
)

data class Params(
    @JsonProperty("index_type")
    val indexType: String = IndexType.AUTOINDEX.name,
    /**
     * The maximum degree of the node and applies only when index_type is set to HNSW.
     */
    @JsonProperty("M")
    val m: Int? = null,
    /**
     * The search scope. This applies only when index_type is set to HNSW
     */
    val efConstruction: Int? = null,
    /**
     * The number of cluster units. This applies to IVF-related index types.
     */
    val nlist: Int? = null,
)
