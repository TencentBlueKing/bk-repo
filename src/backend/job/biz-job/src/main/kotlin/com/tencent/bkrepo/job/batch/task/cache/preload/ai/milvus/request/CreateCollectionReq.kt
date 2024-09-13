package com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request

import com.fasterxml.jackson.annotation.JsonProperty


data class CreateCollectionReq(
    val dbName: String,
    val collectionName: String,
    val dimension: Int,
    val metricType: String = MetricType.COSINE.name,
    val idType: DataType = DataType.Int64,
    @JsonProperty("autoID")
    val autoId: Boolean = false,
    val primaryFieldName: String = "id",
    val vectorFieldName: String = "vector",
    val schema: CollectionSchema? = null,
    val indexParams: List<IndexParam> = ArrayList(),
    val params: Params? = null,
) {
    data class Params(
        @JsonProperty("max_length")
        val maxLength: Int? = null,
        val enableDynamicField: Boolean? = null,
        val shardsNum: Int? = null,
        val consistencyLevel: String? = ConsistencyLevel.Bounded.name,
        val partitionsNum: Int? = null,
        val ttlSeconds: Int? = null,
    )
}
