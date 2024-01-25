package com.tencent.bkrepo.common.artifact.cache.local

import com.tencent.bkrepo.common.artifact.cache.EldestRemovedListener
import org.junit.jupiter.api.Test

class LocalWTinyLFUCacheTest {
    @Test
    fun test() {
        val capacity = 10000
        val counter = LocalCountMinSketchCounter()
        val cache = LocalWTinyLFUCache(capacity, counter)

        cache.addEldestRemovedListener(object : EldestRemovedListener<String, Any?> {
            override fun onEldestRemoved(key: String, value: Any?) {
                println("remove: $key")
            }
        })

        (0..20000).forEach {
            val key = "key-${it%10000}"
            cache.put(key, null)
            cache.get(key)
        }

        println(cache.count())
    }
}
