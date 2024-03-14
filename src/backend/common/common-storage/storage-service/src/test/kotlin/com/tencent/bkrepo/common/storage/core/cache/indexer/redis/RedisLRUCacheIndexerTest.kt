package com.tencent.bkrepo.common.storage.core.cache.indexer.redis

import com.tencent.bkrepo.common.storage.core.cache.indexer.EldestRemovedListener
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class RedisLRUCacheIndexerTest : RedisCacheIndexerTest<RedisLRUCacheIndexer>() {

    @Test
    fun testLru() {
        // 初始化
        val cacheName = CACHE_NAME
        val valuesKey = "{$cacheName}:values"
        val opsForHash = redisTemplate.opsForHash<String, Long>()
        val removedKeys = ConcurrentHashMap<String, String>()
        cacheIndexer.setMaxWeight(500)
        cacheIndexer.addEldestRemovedListener(object : EldestRemovedListener<String, Long> {
            override fun onEldestRemoved(key: String, value: Long) {
                removedKeys[key] = ""
            }
        })

        cacheIndexer.put("a0", 100)
        cacheIndexer.put("a1", 100)
        cacheIndexer.put("a2", 100)
        cacheIndexer.put("a3", 100)
        cacheIndexer.put("a4", 100)
        Assertions.assertEquals(5, opsForHash.size(valuesKey))

        // a0被淘汰
        cacheIndexer.put("a5", 100)
        Thread.sleep(500) // 等待淘汰线程执行淘汰
        Assertions.assertEquals(5, opsForHash.size(valuesKey))
        Assertions.assertFalse(opsForHash.hasKey(valuesKey, "a0"))

        // a1,a2,a4被淘汰
        cacheIndexer.put("a6", 100)
        cacheIndexer.put("a7", 100)
        cacheIndexer.get("a3")
        cacheIndexer.put("a8", 100)
        Thread.sleep(500) // 等待淘汰线程执行淘汰
        Assertions.assertEquals(5, opsForHash.size(valuesKey))
        Assertions.assertTrue(opsForHash.hasKey(valuesKey, "a5"))
        Assertions.assertTrue(opsForHash.hasKey(valuesKey, "a6"))
        Assertions.assertTrue(opsForHash.hasKey(valuesKey, "a7"))
        Assertions.assertTrue(opsForHash.hasKey(valuesKey, "a3"))
        Assertions.assertTrue(opsForHash.hasKey(valuesKey, "a8"))
        Assertions.assertEquals("a5", cacheIndexer.eldestKey())
        Assertions.assertEquals(5, cacheIndexer.count())
        Assertions.assertEquals(500, cacheIndexer.weight())
        Assertions.assertEquals(setOf("a0", "a1", "a2", "a4").sorted(), removedKeys.keys.sorted())
    }

    override fun createIndexer(cacheName: String, cacheDir: Path): RedisLRUCacheIndexer {
        return RedisLRUCacheIndexer(cacheName, cacheDir, redisTemplate, 0)
    }

    /**
     * 清理redis数据
     */
    override fun clean(cacheName: String) {
        redisTemplate.delete("{$cacheName}:total_weight")
        redisTemplate.delete("{$cacheName}:lru")
        redisTemplate.delete("{$cacheName}:values")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RedisLRUCacheIndexer::class.java)
    }
}
