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

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.storage.core.cache.indexer.EldestRemovedListener
import com.tencent.bkrepo.common.storage.core.cache.indexer.StorageCacheIndexer
import com.tencent.bkrepo.common.storage.util.existReal
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.script.RedisScript
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/**
 * 基于Redis实现LRU，存放存储层缓存文件索引
 * 为了避免单独存放存储层缓存文件大小导致额外空间占用，固定key为sha256，value为文件大小
 *
 * 为了避免阻塞提高性能，缓存满时将会异步执行LRU策略进行缓存清理，此时依然可以继续存放数据，可能会出现缓存大小超过限制的情况
 */
class RedisLRUCacheIndexer(
    private val cacheName: String,
    private val cacheDir: Path,
    private val redisTemplate: RedisTemplate<String, String>,
    private var capacity: Int = 0,
    private val listeners: MutableList<EldestRemovedListener<String, Long>> = ArrayList(),
) : StorageCacheIndexer<String, Long> {

    private val evictExecutor = Executors.newSingleThreadExecutor(
        ThreadFactoryBuilder().setNameFormat("storage-cache-evict-redis-lru-%d").build()
    )

    /**
     * 用于通知淘汰线程开始淘汰缓存的信号量
     */
    private val evictSemaphore = Semaphore(0)


    /**
     * 记录当前缓存的总权重
     */
    private val totalWeightKey = "$cacheName:total_weight"

    /**
     * 缓存LRU队列，score为缓存写入时刻的时间戳
     */
    private val lruKey = "$cacheName:lru"

    /**
     * 存放缓存实际值
     */
    private val valuesKey = "$cacheName:values"

    private val putScript = RedisScript.of(SCRIPT_PUT, String::class.java)
    private val getScript = RedisScript.of(SCRIPT_GET, String::class.java)
    private val removeScript = RedisScript.of(SCRIPT_REM, String::class.java)

    private var maxWeight: Long = 0L

    init {
        evictExecutor.execute {
            while (true) {
                evictSemaphore.acquire()
                try {
                    evict()
                } catch (e: Exception) {
                    logger.error("evict failed", e)
                }
                evictSemaphore.drainPermits()
            }
        }
    }

    override fun put(key: String, value: Long, score: Double?): Long? {
        logger.info("put $key into $cacheName")
        val keys = listOf(lruKey, valuesKey, totalWeightKey)
        val oldVal = redisTemplate.execute(putScript, keys, score(score), key, value.toString())
        if (shouldEvict()) {
            evictSemaphore.release()
        }
        return oldVal?.toLong()
    }

    override fun get(key: String): Long? {
        return redisTemplate.execute(getScript, listOf(lruKey, valuesKey), score(), key)?.toLong()
    }

    override fun containsKey(key: String): Boolean {
        return redisTemplate.opsForHash<String, Long>().hasKey(valuesKey, key)
    }

    override fun remove(key: String): Long? {
        logger.info("remove [$key] from $cacheName")
        val keys = listOf(lruKey, valuesKey, totalWeightKey)
        return redisTemplate.execute(removeScript, keys, key)?.toLong()
    }

    override fun count(): Long {
        return redisTemplate.opsForHash<String, Long>().size(valuesKey)
    }

    override fun weight(): Long {
        return redisTemplate.opsForValue().get(totalWeightKey)?.toLong() ?: 0L
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
        return redisTemplate.opsForZSet().range(lruKey, 0L, 0L)?.firstOrNull()
    }

    override fun sync() {
        redisTemplate.opsForHash<String, Long>().scan(valuesKey, ScanOptions.scanOptions().build()).use {
            while (it.hasNext()) {
                val key = it.next().key
                if (!cacheDir.resolve(key).existReal()) {
                    logger.info("$key not exists in storage cache and will be removed")
                    remove(key)
                }
            }
        }
    }

    override fun addEldestRemovedListener(listener: EldestRemovedListener<String, Long>) {
        this.listeners.add(listener)
    }

    override fun getEldestRemovedListeners(): List<EldestRemovedListener<String, Long>> {
        return listeners
    }

    private fun evict() {
        var count = 0
        logger.info("start evict $cacheName")
        while (shouldEvict()) {
            val eldestKey = eldestKey()
            val value = eldestKey?.let { remove(it) }
            value?.let { listeners.forEach { it.onEldestRemoved(eldestKey, value) } }
            count++

            if (count > MAX_EVICT_COUNT) {
                logger.info("$cacheName exceed max evict count[$MAX_EVICT_COUNT]")
                break
            }
        }

        if (count > 0) {
            logger.info("$count key was evicted")
        }
    }

    private fun shouldEvict() =
        maxWeight > 0 && (redisTemplate.opsForValue().get(totalWeightKey)?.toLong() ?: 0L) > maxWeight

    private fun score(score: Double? = null) = score?.toString() ?: System.currentTimeMillis().toString()

    companion object {
        private val logger = LoggerFactory.getLogger(RedisLRUCacheIndexer::class.java)

        /**
         * 一次淘汰中最多淘汰的缓存条目数
         */
        private const val MAX_EVICT_COUNT = 1000

        private const val SCRIPT_PUT = """
            local z = KEYS[1]
            local h = KEYS[2]
            local w = KEYS[3]
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
            local z = KEYS[1]
            local h = KEYS[2]
            local s = ARGV[1]
            local k = ARGV[2]
            redis.call('ZADD', z, s, k)
            return redis.call('HGET', h, k)
        """

        private const val SCRIPT_REM = """
            local z = KEYS[1]
            local h = KEYS[2]
            local w = KEYS[3]
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
    }
}
