/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.artifact.cache.redis

import com.tencent.bkrepo.common.artifact.cache.EldestRemovedListener
import com.tencent.bkrepo.common.artifact.cache.OrderedCache
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.RedisScript

/**
 * 基于Redis实现的SLRU，缓存满后将异步清理
 */
class RedisSLRUCache(
    cacheName: String,
    private val redisTemplate: RedisTemplate<String, String>,
    private val capacity: Int = 0,
    private val listeners: MutableList<EldestRemovedListener<String, Long>> = ArrayList(),
) : OrderedCache<String, Long> {

    /**
     * 记录当前缓存的总权重
     */
    private val totalWeightKey = "$cacheName:slru:total_weight"
    private val protectedTotalWeightKey = "$cacheName:slru:total_weight_protected"
    private val probationTotalWeightKey = "$cacheName:slru:total_weight_probation"

    /**
     * 缓存LRU队列，score为缓存写入时刻的时间戳
     */
    private val protectedLruKey = "$cacheName:slru:protected_lru"
    private val probationLruKey = "$cacheName:slru:probation_lru"

    /**
     * 存放缓存实际值
     */
    private val protectedHashKey = "$cacheName:slru:protected_values"
    private val probationHashKey = "$cacheName:slru:probation_values"

    private val putScript = RedisScript.of(SCRIPT_PUT, String::class.java)
    private val evictProtectedScript = RedisScript.of(SCRIPT_EVICT_PROTECTED, String::class.java)
    private val evictProbationScript = RedisScript.of(SCRIPT_EVICT_PROBATION, List::class.java)
    private val getScript = RedisScript.of(SCRIPT_GET, String::class.java)
    private val removeScript = RedisScript.of(SCRIPT_REMOVE, String::class.java)

    private var maxWeight: Long = 0L
    private var protectedMaxWeight: Long = 0L
    private var probationMaxWeight: Long = 0L

    override fun put(key: String, value: Long): Long? {
        logger.info("put [$key] to cache, size[$value]")
        val keys = listOf(
            protectedLruKey, protectedHashKey, protectedTotalWeightKey,
            probationLruKey, probationHashKey, probationTotalWeightKey, totalWeightKey
        )
        val oldVal = redisTemplate.execute(putScript, keys, score(), key, value.toString())?.toLong()
        checkAndEvictEldest()
        return oldVal
    }

    override fun get(key: String): Long? {
        val keys = listOf(
            protectedLruKey, protectedHashKey, protectedTotalWeightKey,
            probationLruKey, probationHashKey, probationTotalWeightKey
        )
        return redisTemplate.execute(getScript, keys, score(), key)?.toLong()
    }

    override fun containsKey(key: String): Boolean {
        val ops = redisTemplate.opsForHash<String, Long>()
        return ops.hasKey(protectedHashKey, key) || ops.hasKey(probationHashKey, key)
    }

    override fun remove(key: String): Long? {
        val keys = listOf(
            protectedLruKey, protectedHashKey, protectedTotalWeightKey,
            probationLruKey, probationHashKey, probationTotalWeightKey, totalWeightKey
        )
        return redisTemplate.execute(removeScript, keys, key)?.toLong()
    }

    override fun count(): Long {
        val ops = redisTemplate.opsForHash<String, Long>()
        return ops.size(protectedHashKey) + ops.size(probationHashKey)
    }

    override fun weight(): Long {
        return redisTemplate.opsForValue().get(totalWeightKey)?.toLong() ?: 0L
    }

    @Synchronized
    override fun setMaxWeight(max: Long) {
        this.maxWeight = max
        this.protectedMaxWeight = (max * RedisLRUCache.FACTOR_PROTECTED).toLong()
        this.probationMaxWeight = (max * RedisLRUCache.FACTOR_PROBATION).toLong()
    }

    override fun getMaxWeight(): Long {
        return maxWeight
    }

    override fun setCapacity(capacity: Int) {
        throw UnsupportedOperationException()
    }

    override fun getCapacity(): Int {
        return capacity
    }

    override fun eldestKey(): String? {
        return redisTemplate.opsForZSet().range(probationLruKey, 0L, 0L)?.firstOrNull()
            ?: redisTemplate.opsForZSet().range(protectedLruKey, 0L, 0L)?.firstOrNull()
    }

    override fun getEldestRemovedListeners(): List<EldestRemovedListener<String, Long>> {
        return listeners
    }

    @Synchronized
    override fun addEldestRemovedListener(listener: EldestRemovedListener<String, Long>) {
        listeners.add(listener)
    }

    override fun setKeyWeightSupplier(supplier: (k: String, v: Long) -> Long) {
        throw UnsupportedOperationException()
    }

    private fun checkAndEvictEldest() {
        var count = 0
        while ((redisTemplate.opsForValue().get(totalWeightKey)?.toLong() ?: 0L) > maxWeight) {
            count++
            evictProtected()
            evictProbation()
        }
        if (count > 0) {
            logger.info("$count key was evicted")
        }
    }

    private fun evictProtected() {
        if ((redisTemplate.opsForValue().get(protectedTotalWeightKey)?.toLong() ?: 0L) > protectedMaxWeight) {
            val keys = listOf(
                protectedLruKey, protectedHashKey, protectedTotalWeightKey,
                probationLruKey, probationHashKey, probationTotalWeightKey
            )
            redisTemplate.execute(evictProtectedScript, keys, score())
        }
    }

    private fun evictProbation() {
        if ((redisTemplate.opsForValue().get(probationTotalWeightKey)?.toLong() ?: 0L) > probationMaxWeight) {
            val keys = listOf(probationLruKey, probationHashKey, probationTotalWeightKey, totalWeightKey)
            redisTemplate.execute(evictProbationScript, keys)?.let { evicted ->
                require(evicted.size == 2)
                logger.info("$evicted was evicted")
                listeners.forEach { it.onEldestRemoved(evicted[0].toString(), evicted[1].toString().toLong()) }
            }
        }
    }

    private fun score() = System.currentTimeMillis().toString()

    companion object {
        private val logger = LoggerFactory.getLogger(RedisSLRUCache::class.java)

        private const val SCRIPT_PUT = """
            local z1 = KEYS[1]
            local h1 = KEYS[2]
            local w1 = KEYS[3]
            local z2 = KEYS[4]
            local h2 = KEYS[5]
            local w2 = KEYS[6]
            local w = KEYS[7]
            local s = ARGV[1]
            local k = ARGV[2]
            local v = ARGV[3]
            
            if (redis.call('HEXISTS', h1, k) == 1) then
              redis.call('ZADD', z1, s, k)
              redis.call('HSET', h1, k, v)
              return v
            end
            
            if (redis.call('HEXISTS', h2, k) == 1) then
              redis.call('ZREM', z2, k)
              redis.call('HDEL', h2, k)
              redis.call('DECRBY', w2, v)
              redis.call('ZADD', z1, s, k)
              redis.call('HSET', h1, k, v)
              redis.call('INCRBY', w1, v)
              return v
            end
            
            redis.call('ZADD', z2, s, k)
            redis.call('HSET', h2, k, v)
            redis.call('INCRBY', w2, v)
            redis.call('INCRBY', w, v)
            return nil
        """

        private const val SCRIPT_EVICT_PROTECTED = """
            local z1 = KEYS[1]
            local h1 = KEYS[2]
            local w1 = KEYS[3]
            local z2 = KEYS[4]
            local h2 = KEYS[5]
            local w2 = KEYS[6]
            local s = ARGV[1]
            local keys = redis.call('ZRANGE', z1, 0, 0)
            if keys[1] then
              local k = keys[1]
              redis.call('ZREM', z1, k)
              local oldWeight = redis.call('HGET', h1, k)
              redis.call('HDEL', h1, k)
              redis.call('DECRBY', w1, oldWeight)
              redis.call('ZADD', z2, s, k)
              redis.call('HSET', h2, k, oldWeight)
              redis.call('INCRBY', w2, oldWeight)
            end
        """

        private const val SCRIPT_EVICT_PROBATION = """
            local z2 = KEYS[1]
            local h2 = KEYS[2]
            local w2 = KEYS[3]
            local w = KEYS[4]
            local keys = redis.call('ZRANGE', z2, 0, 0)
            if keys[1] then
              local k = keys[1]
              redis.call('ZREM', z2, k)
              local oldWeight = redis.call('HGET', h2, k)
              redis.call('HDEL', h2, k)
              redis.call('DECRBY', w2, oldWeight)
              redis.call('DECRBY', w, oldWeight)
              return {k, oldWeight}
            end
            return nil
        """

        private const val SCRIPT_GET = """
            local z1 = KEYS[1]
            local h1 = KEYS[2]
            local w1 = KEYS[3]
            local z2 = KEYS[4]
            local h2 = KEYS[5]
            local w2 = KEYS[6]
            local s = ARGV[1]
            local k = ARGV[2]
            
            local v = redis.call('HGET', h1, k)
            if v then
              redis.call('ZADD', z1, s, k)
              return v
            end
            
            v = redis.call('HGET', h2, k)
            if v then
              redis.call('ZREM', z2, k)
              redis.call('HDEL', h2, k)
              redis.call('DECRBY', w2, v)
              
              redis.call('ZADD', z1, s, k)
              redis.call('HSET', h1, k, v)
              redis.call('INCRBY', w1, v)
              return v
            end
            
            return nil
        """

        private const val SCRIPT_REMOVE = """
            local z1 = KEYS[1]
            local h1 = KEYS[2]
            local w1 = KEYS[3]
            local z2 = KEYS[4]
            local h2 = KEYS[5]
            local w2 = KEYS[6]
            local w = KEYS[7]
            local k = ARGV[1]
        
            local oldWeight = redis.call('HGET', h1, k)
            if oldWeight then
              redis.call('ZREM', z1, k)
              redis.call('HDEL', h1, k)
              redis.call('DECRBY', w1, oldWeight)
              redis.call('DECRBY', w, oldWeight)
              return oldWeight
            end
            
            oldWeight = redis.call('HGET', h2, k)
            if oldWeight then
              redis.call('ZREM', z2, k)
              redis.call('HDEL', h2, k)
              redis.call('DECRBY', w2, oldWeight)
              redis.call('DECRBY', w, oldWeight)
              return oldWeight
            end
            
            return nil
        """
    }
}
