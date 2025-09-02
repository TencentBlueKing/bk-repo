package com.tencent.bkrepo.job.pojo

data class MigrateBlockNodeRequest(
    val oldCollectionNamePrefix: String,
    val newCollectionNamePrefix: String,
    val newShardingColumns: List<String>,
    val newShardingCount: Int
)
