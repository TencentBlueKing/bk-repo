package com.tencent.bkrepo.common.mongo.dao.util

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ShardingUtilsTest {

    @Test
    fun testShardingCount() {
        assertThrows<IllegalArgumentException> { ShardingUtils.shardingCountFor(-1) }
        Assertions.assertEquals(1, ShardingUtils.shardingCountFor(0))
        Assertions.assertEquals(1, ShardingUtils.shardingCountFor(1))
        Assertions.assertEquals(2, ShardingUtils.shardingCountFor(2))
        Assertions.assertEquals(4, ShardingUtils.shardingCountFor(3))
        Assertions.assertEquals(256, ShardingUtils.shardingCountFor(255))
        Assertions.assertEquals(256, ShardingUtils.shardingCountFor(256))
        Assertions.assertEquals(512, ShardingUtils.shardingCountFor(257))
        Assertions.assertEquals(1024, ShardingUtils.shardingCountFor(2000))
    }

    @Test
    fun testShardingSequence() {
        Assertions.assertEquals(0, ShardingUtils.shardingSequenceFor(0, 256))
        Assertions.assertEquals(255, ShardingUtils.shardingSequenceFor(255, 256))
        Assertions.assertEquals(0, ShardingUtils.shardingSequenceFor(256, 256))

        Assertions.assertEquals(0, ShardingUtils.shardingSequenceFor(0, 1))
        Assertions.assertEquals(0, ShardingUtils.shardingSequenceFor(1, 1))
        Assertions.assertEquals(0, ShardingUtils.shardingSequenceFor(2, 1))
    }
}