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

import com.tencent.bkrepo.common.storage.core.cache.indexer.EldestRemovedListener
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class RedisSLRUCacheIndexerTest : RedisCacheIndexerTest<RedisSLRUCacheIndexer>() {

    @Test
    fun testSLRU() {
        // 初始化
        val opsForHash = redisTemplate.opsForHash<String, Long>()
        val removedKeys = ConcurrentHashMap<String, String>()
        cacheIndexer.setMaxWeight(500)
        cacheIndexer.addEldestRemovedListener(object : EldestRemovedListener<String, Long> {
            override fun onEldestRemoved(key: String, value: Long) {
                removedKeys[key] = ""
            }
        })

        // 第一次写入时在probation区域
        cacheIndexer.put("a0", 100)
        cacheIndexer.put("a1", 100)
        cacheIndexer.put("a2", 100)
        cacheIndexer.put("a3", 100)
        cacheIndexer.put("a4", 100)
        Assertions.assertTrue(opsForHash.hasKey(probationValuesKey(), "a0"))
        Assertions.assertTrue(opsForHash.hasKey(probationValuesKey(), "a4"))

        // 晋升到protected区域
        cacheIndexer.put("a0", 100)
        cacheIndexer.get("a4")
        Assertions.assertTrue(opsForHash.hasKey(protectedValuesKey(), "a0"))
        Assertions.assertTrue(opsForHash.hasKey(protectedValuesKey(), "a4"))

        // probation淘汰, 淘汰后只剩(a7,a8,a9)(a0,a4)
        cacheIndexer.put("a5", 100)
        cacheIndexer.put("a6", 100)
        cacheIndexer.put("a7", 100)
        cacheIndexer.put("a8", 100)
        cacheIndexer.put("a9", 100)

        Thread.sleep(500) // 缓存异步淘汰，这里需要等待缓存淘汰执行完
        Assertions.assertNull(cacheIndexer.get("a1"))
        Assertions.assertNull(cacheIndexer.get("a2"))
        Assertions.assertNull(cacheIndexer.get("a3"))
        Assertions.assertNull(cacheIndexer.get("a5"))
        Assertions.assertNull(cacheIndexer.get("a6"))

        // protected淘汰
        cacheIndexer.put("a7", 100)
        cacheIndexer.put("a8", 100)
        cacheIndexer.put("a9", 100)
        cacheIndexer.put("a10", 100)
        Thread.sleep(500) // 等待缓存淘汰
        Assertions.assertNull(cacheIndexer.get("a10"))

        cacheIndexer.put("a11", 100)
        Thread.sleep(500) // 等待缓存淘汰
        Assertions.assertNull(cacheIndexer.get("a0"))

        cacheIndexer.put("a12", 100)
        Thread.sleep(500) // 等待缓存淘汰
        Assertions.assertNull(cacheIndexer.get("a11"))

        // 确认最终缓存情况
        Assertions.assertEquals(1, opsForHash.size(probationValuesKey()))
        Assertions.assertEquals(4, opsForHash.size(protectedValuesKey()))
        Assertions.assertTrue(opsForHash.hasKey(probationValuesKey(), "a12"))
        Assertions.assertTrue(opsForHash.hasKey(protectedValuesKey(), "a4"))
        Assertions.assertTrue(opsForHash.hasKey(protectedValuesKey(), "a7"))
        Assertions.assertTrue(opsForHash.hasKey(protectedValuesKey(), "a8"))
        Assertions.assertTrue(opsForHash.hasKey(protectedValuesKey(), "a9"))
        Assertions.assertEquals("a12", cacheIndexer.eldestKey())
        Assertions.assertEquals(5, cacheIndexer.count())
        Assertions.assertEquals(500, cacheIndexer.weight())
        Assertions.assertEquals(8, removedKeys.size)
        Assertions.assertEquals(
            setOf("a1", "a2", "a3", "a5", "a6", "a10", "a0", "a11").sorted(),
            removedKeys.keys.sorted()
        )
    }

    override fun createIndexer(cacheName: String, cacheDir: Path): RedisSLRUCacheIndexer {
        return RedisSLRUCacheIndexer(cacheName, cacheDir, redisTemplate, 0)
    }

    /**
     * 清理redis数据
     */
    override fun clean(cacheName: String) {
        redisTemplate.delete(protectedValuesKey(cacheName))
        redisTemplate.delete(probationValuesKey(cacheName))

        redisTemplate.delete("{$cacheName}:slru:total_weight")
        redisTemplate.delete("{$cacheName}:slru:total_weight_protected")
        redisTemplate.delete("{$cacheName}:slru:total_weight_probation")

        redisTemplate.delete("{$cacheName}:slru:protected_lru")
        redisTemplate.delete("{$cacheName}:slru:probation_lru")
    }

    private fun protectedValuesKey(cacheName: String = CACHE_NAME) = "{$cacheName}:slru:protected_values"
    private fun probationValuesKey(cacheName: String = CACHE_NAME) = "{$cacheName}:slru:probation_values"

    companion object {
        private val logger = LoggerFactory.getLogger(RedisSLRUCacheIndexerTest::class.java)
    }
}
