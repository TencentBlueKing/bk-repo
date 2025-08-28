package com.tencent.bkrepo.common.metadata.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "block-node")
data class BlockNodeProperties(
    var collectionName: String = "",
    var shardingColumns: List<String> = emptyList(),
    var shardingCount: Int? = null,
)
