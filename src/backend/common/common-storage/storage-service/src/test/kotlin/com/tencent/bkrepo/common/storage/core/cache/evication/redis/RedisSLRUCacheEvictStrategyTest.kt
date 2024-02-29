package com.tencent.bkrepo.common.storage.core.cache.evication.redis

import com.tencent.bkrepo.common.redis.RedisAutoConfiguration
import com.tencent.bkrepo.common.storage.core.cache.evication.EldestRemovedListener
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
import java.util.concurrent.ConcurrentHashMap

@DataRedisTest
@Import(TestRedisConfiguration::class)
@ImportAutoConfiguration(TestRedisConfiguration::class, RedisAutoConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisSLRUCacheEvictStrategyTest {

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, String>

    private lateinit var slru: RedisSLRUCacheEvictStrategy

    @BeforeAll
    fun before() {
        slru = createStrategy().apply {
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
        slru.put(key, value)
        slru.put("test2", value, (System.currentTimeMillis() - 100000L).toDouble())
        Assertions.assertEquals(slru.eldestKey(), "test2")

        // get
        Assertions.assertEquals(value, slru.get(key))

        // contains
        Assertions.assertTrue(slru.containsKey(key))

        // remove
        Assertions.assertEquals(value, slru.remove(key))
        Assertions.assertNull(slru.get(key))
        Assertions.assertNull(slru.remove(key))
    }

    @Test
    fun testSLRU() {
        // 初始化
        val opsForHash = redisTemplate.opsForHash<String, Long>()
        val removedKeys = ConcurrentHashMap<String, String>()
        slru.setMaxWeight(500)
        slru.addEldestRemovedListener(object : EldestRemovedListener<String, Long> {
            override fun onEldestRemoved(key: String, value: Long) {
                removedKeys[key] = ""
            }
        })

        // 第一次写入时在probation区域
        slru.put("a0", 100)
        slru.put("a1", 100)
        slru.put("a2", 100)
        slru.put("a3", 100)
        slru.put("a4", 100)
        Assertions.assertTrue(opsForHash.hasKey(probationValuesKey(), "a0"))
        Assertions.assertTrue(opsForHash.hasKey(probationValuesKey(), "a4"))

        // 晋升到protected区域
        slru.put("a0", 100)
        slru.get("a4")
        Assertions.assertTrue(opsForHash.hasKey(protectedValuesKey(), "a0"))
        Assertions.assertTrue(opsForHash.hasKey(protectedValuesKey(), "a4"))

        // probation淘汰, 淘汰后只剩(a7,a8,a9)(a0,a4)
        slru.put("a5", 100)
        slru.put("a6", 100)
        slru.put("a7", 100)
        slru.put("a8", 100)
        slru.put("a9", 100)

        Thread.sleep(500) // 缓存异步淘汰，这里需要等待缓存淘汰执行完
        Assertions.assertNull(slru.get("a1"))
        Assertions.assertNull(slru.get("a2"))
        Assertions.assertNull(slru.get("a3"))
        Assertions.assertNull(slru.get("a5"))
        Assertions.assertNull(slru.get("a6"))

        // protected淘汰
        slru.put("a7", 100)
        slru.put("a8", 100)
        slru.put("a9", 100)
        slru.put("a10", 100)
        Thread.sleep(500) // 等待缓存淘汰
        Assertions.assertNull(slru.get("a10"))

        slru.put("a11", 100)
        Thread.sleep(500) // 等待缓存淘汰
        Assertions.assertNull(slru.get("a0"))

        slru.put("a12", 100)
        Thread.sleep(500) // 等待缓存淘汰
        Assertions.assertNull(slru.get("a11"))

        // 确认最终缓存情况
        Assertions.assertEquals(1, opsForHash.size(probationValuesKey()))
        Assertions.assertEquals(4, opsForHash.size(protectedValuesKey()))
        Assertions.assertTrue(opsForHash.hasKey(probationValuesKey(), "a12"))
        Assertions.assertTrue(opsForHash.hasKey(protectedValuesKey(), "a4"))
        Assertions.assertTrue(opsForHash.hasKey(protectedValuesKey(), "a7"))
        Assertions.assertTrue(opsForHash.hasKey(protectedValuesKey(), "a8"))
        Assertions.assertTrue(opsForHash.hasKey(protectedValuesKey(), "a9"))
        Assertions.assertEquals("a12", slru.eldestKey())
        Assertions.assertEquals(5, slru.count())
        Assertions.assertEquals(500, slru.weight())
        Assertions.assertEquals(8, removedKeys.size)
        Assertions.assertEquals(
            setOf("a1", "a2", "a3", "a5", "a6", "a10", "a0", "a11").sorted(),
            removedKeys.keys.sorted()
        )
    }

    @Test
    fun testSync() {
        val cacheFile = File(CACHE_DIR.toString(), "test")
        cacheFile.parentFile.mkdirs()
        cacheFile.delete()
        cacheFile.createNewFile()
        cacheFile.outputStream().use { it.write(1) }

        slru.put("test", 1L)
        slru.put("test2", 1L)
        slru.sync()
        Assertions.assertNull(slru.get("test2"))
        Assertions.assertNotNull(slru.get("test"))

        cacheFile.delete()
    }

    private fun createStrategy(
        cacheName: String = CACHE_NAME, cacheDir: Path = CACHE_DIR
    ): RedisSLRUCacheEvictStrategy {
        return RedisSLRUCacheEvictStrategy(cacheName, cacheDir, redisTemplate, 0)
    }

    /**
     * 清理redis数据
     */
    private fun clean(cacheName: String = CACHE_NAME) {
        redisTemplate.delete(protectedValuesKey(cacheName))
        redisTemplate.delete(probationValuesKey(cacheName))

        redisTemplate.delete("$cacheName:slru:total_weight")
        redisTemplate.delete("$cacheName:slru:total_weight_protected")
        redisTemplate.delete("$cacheName:slru:total_weight_probation")

        redisTemplate.delete("$cacheName:slru:protected_lru")
        redisTemplate.delete("$cacheName:slru:probation_lru")
    }

    private fun protectedValuesKey(cacheName: String = CACHE_NAME) = "$cacheName:slru:protected_values"
    private fun probationValuesKey(cacheName: String = CACHE_NAME) = "$cacheName:slru:probation_values"

    companion object {
        private val logger = LoggerFactory.getLogger(RedisSLRUCacheEvictStrategyTest::class.java)
        private const val CACHE_NAME = "test"
        private val CACHE_DIR = System.getProperty("java.io.tmpdir").toPath().resolve("storage-cache-evict-test")
    }
}
