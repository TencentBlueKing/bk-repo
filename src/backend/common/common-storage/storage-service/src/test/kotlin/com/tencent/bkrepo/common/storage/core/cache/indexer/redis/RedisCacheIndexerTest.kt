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

import com.tencent.bkrepo.common.redis.RedisAutoConfiguration
import com.tencent.bkrepo.common.storage.core.cache.indexer.StorageCacheIndexer
import com.tencent.bkrepo.common.storage.core.locator.HashFileLocator
import com.tencent.bkrepo.common.storage.util.toPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import java.io.File
import java.nio.file.Path

@DataRedisTest
@Import(TestRedisConfiguration::class)
@ImportAutoConfiguration(TestRedisConfiguration::class, RedisAutoConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class RedisCacheIndexerTest<T: StorageCacheIndexer<String, Long>> {

    @Autowired
    protected lateinit var redisTemplate: RedisTemplate<String, String>

    protected lateinit var cacheIndexer: T

    protected val fileLocator = HashFileLocator()

    @BeforeAll
    fun before() {
        cacheIndexer = createIndexer().apply {
            setMaxWeight(0)
        }
    }

    @AfterEach
    fun afterEach() {
        clean()
    }

    @Test
    fun testBasicOperation() {
        val key = "test"
        val value = 1000L
        // put
        Assertions.assertEquals(cacheIndexer.eldestKey(), null)
        cacheIndexer.put(key, value)
        Assertions.assertEquals(cacheIndexer.eldestKey(), key)
        Assertions.assertEquals(value, cacheIndexer.put(key, value))
        cacheIndexer.put("${key}2", value, (System.currentTimeMillis() - 100000L).toDouble())
        Assertions.assertEquals(cacheIndexer.eldestKey(), "${key}2")

        // get
        Assertions.assertEquals(value, cacheIndexer.get(key))

        // contains
        Assertions.assertTrue(cacheIndexer.containsKey(key))
        Assertions.assertFalse(cacheIndexer.containsKey("${key}3"))

        // remove
        Assertions.assertEquals(value, cacheIndexer.remove(key))
        Assertions.assertNull(cacheIndexer.get(key))
        Assertions.assertNull(cacheIndexer.remove(key))
    }

    @Test
    fun testSync() {
        val cacheFile = File(CACHE_DIR.toString(), "/te/st/test")
        cacheFile.parentFile.mkdirs()
        cacheFile.delete()
        cacheFile.createNewFile()
        cacheFile.outputStream().use { it.write(1) }

        cacheIndexer.put("test", 1L)
        cacheIndexer.put("test2", 1L)
        cacheIndexer.sync()
        Assertions.assertNull(cacheIndexer.get("test2"))
        Assertions.assertNotNull(cacheIndexer.get("test"))

        cacheFile.delete()
    }

    abstract fun createIndexer(cacheName: String = CACHE_NAME, cacheDir: Path = CACHE_DIR): T

    abstract fun clean(cacheName: String = CACHE_NAME)

    companion object {
        private val logger = LoggerFactory.getLogger(RedisSLRUCacheIndexerTest::class.java)
        const val CACHE_NAME = "test"
        val CACHE_DIR = System.getProperty("java.io.tmpdir").toPath().resolve("storage-cache-evict-test")
    }
}
