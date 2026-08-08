package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.metadata.properties.BlockNodeProperties
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils

/** block_node / block_node_v2 分表名解析，与 [BlockNodeDao]、[BlockNodeProperties] 对齐。 */
object BlockNodeCollectionNaming {

    const val DEFAULT_BASE = "block_node"

    fun baseName(properties: BlockNodeProperties?): String =
        properties?.collectionName?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE

    /** 如 `block_node_`、`block_node_v2_` */
    fun shardPrefix(properties: BlockNodeProperties?): String = "${baseName(properties)}_"

    fun shardCollection(shardIdx: Int, properties: BlockNodeProperties?): String =
        shardPrefix(properties) + shardIdx

    fun shardCount(properties: BlockNodeProperties?): Int {
        val raw = properties?.shardingCount ?: DEFAULT_SHARD_COUNT
        return HashShardingUtils.shardingCountFor(raw)
    }

    fun allShardCollections(properties: BlockNodeProperties?): List<String> =
        (0 until shardCount(properties)).map { shardCollection(it, properties) }

    fun isShardCollection(collectionName: String, properties: BlockNodeProperties?): Boolean {
        val prefix = shardPrefix(properties)
        if (!collectionName.startsWith(prefix)) return false
        val suffix = collectionName.removePrefix(prefix)
        return suffix.isNotEmpty() && suffix.all { it.isDigit() }
    }

    private const val DEFAULT_SHARD_COUNT = 256
}
