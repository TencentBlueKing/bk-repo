package com.tencent.bkrepo.common.metadata.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "block-node")
data class BlockNodeProperties(
    /**
     * 表名
     */
    var collectionName: String = "",
    /**
     * 分表字段名
     */
    var shardingColumns: List<String> = emptyList(),
    /**
     * 分表数量，非2的次幂时将自动转换为2的次幂
     */
    var shardingCount: Int? = null,
)
