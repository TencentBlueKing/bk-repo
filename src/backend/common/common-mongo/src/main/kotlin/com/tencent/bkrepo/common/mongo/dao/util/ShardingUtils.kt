package com.tencent.bkrepo.common.mongo.dao.util

import org.slf4j.LoggerFactory

/**
 * Sharding 工具类
 */
object ShardingUtils {

    private const val MAXIMUM_CAPACITY = 1 shl 10
    private val logger = LoggerFactory.getLogger(ShardingUtils::class.java)

    /**
     * 计算[i]对应的合适的sharding数量，规则是得到大于等于[i]的2的次幂，最小值为1，最大值为[MAXIMUM_CAPACITY]
     */
    fun shardingCountFor(i: Int): Int {
        require(i >= 0) { "Illegal initial sharding count : $i" }
        var result = if (i > MAXIMUM_CAPACITY) MAXIMUM_CAPACITY else i
        result = tableSizeFor(result)
        if (i != result) {
            logger.warn("Bad initial sharding count: [$i], converted to: [$result]")
        }
        return result
    }

    /**
     * 计算[value]对应的sharding sequence
     *
     * [shardingCount]表示分表数量，计算出的结果范围为[0, shardingCount)
     */
    fun shardingSequenceFor(value: Any, shardingCount: Int): Int {
        val hashCode = value.hashCode()
        return hashCode and shardingCount - 1
    }

    private fun tableSizeFor(cap: Int): Int {
        // 减一的目的在于如果cap本身就是2的次幂，保证结果是原值，不减一的话，结果就成了cap * 2
        var n = cap - 1
        // 从最高位的1往低位复制
        n = n or n.ushr(1)
        n = n or n.ushr(2)
        n = n or n.ushr(4)
        n = n or n.ushr(8)
        n = n or n.ushr(16)
        // 到这里，从最高位的1到第0位都是1了，再加上1就是2的次幂
        return if (n < 0) 1 else if (n >= MAXIMUM_CAPACITY) MAXIMUM_CAPACITY else n + 1
    }
}