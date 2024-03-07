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
import com.tencent.bkrepo.common.storage.util.existReal
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

abstract class RedisCacheIndexer(
    protected val cacheName: String,
    protected val cacheDir: Path,
    protected val redisTemplate: RedisTemplate<String, String>,
    /**
     * 作为hash tag使用，未设置时使用cacheName作为hash tag
     */
    protected val hashTag: String? = null
) {

    // 需要增加hash tag后缀以支持Redis集群模式
    protected val keyPrefix = if (hashTag == null) {
        "{$cacheName}"
    } else {
        "{$hashTag}:$cacheName"
    }

    /**
     * 部分redis集群用第一个key计算slot，需要指定key用于固定使用单个slot
     */
    protected val firstKey = "{${hashTag ?: cacheName}}"

    /**
     * 用于执行缓存淘汰的线程池
     */
    private val evictExecutor = Executors.newSingleThreadExecutor(
        ThreadFactoryBuilder().setNameFormat("storage-cache-indexer-evict-redis-%d").build()
    )

    /**
     * 用于通知淘汰线程开始淘汰缓存的信号量
     */
    protected val evictSemaphore = Semaphore(0)
    protected val simpleGetScript = RedisScript.of(SCRIPT_SIMPLE_GET, String::class.java)
    protected val hscanScript = RedisScript.of(SCRIPT_HSCAN, List::class.java)


    init {
        // 无限循环执行缓存清理，缓存满时通过信号量通知开始淘汰
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

    @Suppress("UNCHECKED_CAST")
    protected fun sync(hashKey: String) {
        var cursor = 0L
        do {
            val result = redisTemplate.execute(hscanScript, listOf(firstKey, hashKey), cursor.toString())
            require(result.size == 2)
            cursor = (result[0] as String).toLong()
            val data = result[1] as List<String>
            for (i in data.indices step 2) {
                val key = data[i]
                if (!cacheDir.resolve(key).existReal()) {
                    logger.info("$key cache file was not exists and will be removed")
                    remove(key)
                }
            }
        } while (cursor != 0L)
    }

    private fun evict() {
        logger.info("start evict $cacheName")
        var count = 0
        while (shouldEvict()) {
            count++
            evictEldest()
            if (count > MAX_EVICT_COUNT) {
                logger.info("evict $cacheName exceed max count[${MAX_EVICT_COUNT}]")
                break
            }
        }
        if (count > 0) {
            logger.info("$count key was evicted")
        }
    }

    protected open fun score(score: Double? = null) = score?.toString() ?: System.currentTimeMillis().toString()

    protected abstract fun evictEldest()
    protected abstract fun shouldEvict(): Boolean
    abstract fun remove(key: String): Long?

    companion object {
        private val logger = LoggerFactory.getLogger(RedisCacheIndexer::class.java)

        /**
         * 一次淘汰中最多淘汰的缓存条目数
         */
        private const val MAX_EVICT_COUNT = 1000

        private const val SCRIPT_SIMPLE_GET = "return redis.call('GET', KEYS[2])"
        private const val SCRIPT_HSCAN = "return redis.call('HSCAN', KEYS[2], ARGV[1])"
    }
}
