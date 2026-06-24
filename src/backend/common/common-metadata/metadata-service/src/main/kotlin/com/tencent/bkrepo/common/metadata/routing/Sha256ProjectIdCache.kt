package com.tencent.bkrepo.common.metadata.routing

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/** §11.4 G-37：sha256→projectId 本地缓存；未命中时降级散发查询。 */
@Component
class Sha256ProjectIdCache(
    private val properties: com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties,
) {
    private data class Entry(val projectIds: Set<String>, val expiresAtMillis: Long)

    private val store = ConcurrentHashMap<String, Entry>()

    fun get(sha256: String): Set<String>? {
        val entry = store[sha256] ?: return null
        if (System.currentTimeMillis() > entry.expiresAtMillis) {
            store.remove(sha256)
            return null
        }
        return entry.projectIds
    }

    fun put(sha256: String, projectIds: Set<String>) {
        if (projectIds.isEmpty()) return
        val maxSize = properties.scatterQuery.sha256CacheMaxSize
        if (store.size >= maxSize) {
            store.keys.firstOrNull()?.let { store.remove(it) }
        }
        val ttlMinutes = properties.scatterQuery.sha256CacheTtlMinutes
        store[sha256] = Entry(
            projectIds = projectIds,
            expiresAtMillis = System.currentTimeMillis() + ttlMinutes * 60_000L,
        )
    }

    fun invalidate(sha256: String) {
        store.remove(sha256)
    }
}
