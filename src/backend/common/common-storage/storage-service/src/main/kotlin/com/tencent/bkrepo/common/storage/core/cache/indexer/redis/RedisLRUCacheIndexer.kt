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

package com.tencent.bkrepo.common.storage.core.cache.indexer.redis

import com.tencent.bkrepo.common.storage.core.cache.indexer.listener.EldestRemovedListener
import com.tencent.bkrepo.common.storage.core.locator.FileLocator
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import java.nio.file.Path

/**
 * 基于Redis实现LRU，存放存储层缓存文件索引
 * 为了避免单独存放存储层缓存文件大小导致额外空间占用，固定key为sha256，value为文件大小
 *
 * 为了避免阻塞提高性能，缓存满时将会异步执行LRU策略进行缓存清理，此时依然可以继续存放数据，可能会出现缓存大小超过限制的情况
 */
class RedisLRUCacheIndexer(
    cacheName: String,
    cacheDir: Path,
    fileLocator: FileLocator,
    redisTemplate: RedisTemplate<String, String>,
    private var capacity: Int = 0,
    private val listeners: MutableList<EldestRemovedListener<String, Long>> = ArrayList(),
    /**
     * 作为hash tag使用
     */
    hashTag: String? = null,
    evict: Boolean = true,
) : RedisCacheIndexer(cacheName, cacheDir, fileLocator, redisTemplate, hashTag, evict) {
    /**
     * 记录当前缓存的总权重
     */
    private val totalWeightKey = "$keyPrefix:total_weight"

    /**
     * 缓存LRU队列，score为缓存写入时刻的时间戳
     */
    private val lruKey = "$keyPrefix:lru"

    /**
     * 存放缓存实际值
     */
    private val valuesKey = "$keyPrefix:values"

    private val putScript = RedisScript.of(SCRIPT_PUT, String::class.java)
    private val getScript = RedisScript.of(SCRIPT_GET, String::class.java)
    private val removeScript = RedisScript.of(SCRIPT_REM, String::class.java)
    private val containsScript = RedisScript.of(SCRIPT_CONTAINS, Long::class.java)
    private val sizeScript = RedisScript.of(SCRIPT_SIZE, Long::class.java)
    private val eldestScript = RedisScript.of(SCRIPT_ELDEST, String::class.java)

    private var maxWeight: Long = 0L

    @Suppress("UNNECESSARY_SAFE_CALL")
    override fun put(key: String, value: Long, score: Double?): Long? {
        logger.info("put $key into $cacheName")
        val keys = listOf(firstKey, lruKey, valuesKey, totalWeightKey)
        val oldVal = redisTemplate.execute(putScript, keys, score(score), key, value.toString())
        if (evict && shouldEvict()) {
            evictSemaphore.release()
        }
        return oldVal?.toLong()
    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    override fun get(key: String): Long? {
        return redisTemplate.execute(getScript, listOf(firstKey, lruKey, valuesKey), score(), key)?.toLong()
    }

    override fun containsKey(key: String): Boolean {
        return redisTemplate.execute(containsScript, listOf(firstKey, valuesKey), key) == 1L
    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    override fun remove(key: String): Long? {
        logger.info("remove [$key] from $cacheName")
        val keys = listOf(firstKey, lruKey, valuesKey, totalWeightKey)
        return redisTemplate.execute(removeScript, keys, key)?.toLong()
    }

    override fun count(): Long {
        return redisTemplate.execute(sizeScript, listOf(firstKey, valuesKey))
    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    override fun weight(): Long {
        return redisTemplate.execute(simpleGetScript, listOf(firstKey, totalWeightKey))?.toLong() ?: 0L
    }

    override fun setMaxWeight(max: Long) {
        this.maxWeight = max
    }

    override fun getMaxWeight(): Long = maxWeight

    override fun setCapacity(capacity: Int) {
        throw UnsupportedOperationException()
    }

    override fun getCapacity(): Int = capacity

    override fun setKeyWeightSupplier(supplier: (k: String, v: Long) -> Long) {
        throw UnsupportedOperationException()
    }

    override fun eldestKey(): String? {
        return redisTemplate.execute(eldestScript, listOf(firstKey, lruKey))
    }

    override fun sync(): Int {
        return sync(valuesKey)
    }

    override fun addEldestRemovedListener(listener: EldestRemovedListener<String, Long>) {
        this.listeners.add(listener)
    }

    override fun getEldestRemovedListeners(): List<EldestRemovedListener<String, Long>> {
        return listeners
    }

    override fun evictEldest() {
        val eldestKey = eldestKey()
        val value = eldestKey?.let { remove(it) }
        value?.let { listeners.forEach { it.onEldestRemoved(eldestKey, value) } }
    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    override fun shouldEvict(): Boolean {
        val weight = redisTemplate.execute(simpleGetScript, listOf(firstKey, totalWeightKey))?.toLong() ?: 0L
        return maxWeight in 1 until weight
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RedisLRUCacheIndexer::class.java)

        private const val SCRIPT_PUT = """
            local z = KEYS[2]
            local h = KEYS[3]
            local w = KEYS[4]
            local s = ARGV[1]
            local k = ARGV[2]
            local v = ARGV[3]
            
            redis.call('ZADD', z, s, k)
            local oldWeight = redis.call('HGET', h, k)
            if oldWeight then
              redis.call('DECRBY', w, oldWeight)
            end
            redis.call('HSET', h, k, v)
            redis.call('INCRBY', w, v)
            return oldWeight
        """

        private const val SCRIPT_GET = """
            local z = KEYS[2]
            local h = KEYS[3]
            local s = ARGV[1]
            local k = ARGV[2]
            redis.call('ZADD', z, s, k)
            return redis.call('HGET', h, k)
        """

        private const val SCRIPT_REM = """
            local z = KEYS[2]
            local h = KEYS[3]
            local w = KEYS[4]
            local k = ARGV[1]
            local oldWeight = redis.call('HGET', h, k)
            if oldWeight == nil then
              return nil
            end
            redis.call('DECRBY', w, oldWeight)
            redis.call('HDEL', h, k)
            redis.call('ZREM', z, k)
            return oldWeight
        """

        private const val SCRIPT_CONTAINS = "return redis.call('HEXISTS', KEYS[2], ARGV[1]) == 1 or 0"
        private const val SCRIPT_SIZE = "return redis.call('HLEN', KEYS[2])"
        private const val SCRIPT_ELDEST = "return redis.call('ZRANGE', KEYS[2], 0, 0)[1]"
    }
}
