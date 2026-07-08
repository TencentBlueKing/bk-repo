package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.metadata.properties.BlockNodeProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlockNodeCollectionNamingTest {

    @Test
    fun `default naming`() {
        assertEquals("block_node_", BlockNodeCollectionNaming.shardPrefix(null))
        assertEquals("block_node_0", BlockNodeCollectionNaming.shardCollection(0, null))
        assertTrue(BlockNodeCollectionNaming.isShardCollection("block_node_188", null))
        assertFalse(BlockNodeCollectionNaming.isShardCollection("block_node_v2_0", null))
    }

    @Test
    fun `v2 naming`() {
        val props = BlockNodeProperties(collectionName = "block_node_v2")
        assertEquals("block_node_v2_", BlockNodeCollectionNaming.shardPrefix(props))
        assertEquals("block_node_v2_3", BlockNodeCollectionNaming.shardCollection(3, props))
        assertTrue(BlockNodeCollectionNaming.isShardCollection("block_node_v2_0", props))
        assertFalse(BlockNodeCollectionNaming.isShardCollection("block_node_0", props))
    }
}
