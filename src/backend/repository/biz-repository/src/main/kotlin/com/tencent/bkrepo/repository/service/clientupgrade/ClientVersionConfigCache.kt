package com.tencent.bkrepo.repository.service.clientupgrade

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.tencent.bkrepo.common.redis.RedisOperation
import com.tencent.bkrepo.repository.model.TClientVersionConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * 三层缓存：
 *  - Global 层：key = (productId, platform, arch)，全员共享最优配置
 *  - UserScopeExists 层：key = (productId, platform, arch)
 *    标记是否存在任意用户专属配置
 *  - User 层：key = (productId, platform, arch, userId)，包含专属配置或 null sentinel
 *
 * null sentinel（__NULL__）表示"已确认无配置"，区别于缓存未命中。
 * Redis 不可用时静默降级：读返回 null，写跳过。
 */
@Component
class ClientVersionConfigCache(
    private val redisOperation: RedisOperation,
) {
    private val cacheMapper: ObjectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    /**
     * 区分"缓存未命中"（null）与"缓存了空值/negative cache"（CacheHit(null)）
     */
    data class CacheHit<T>(val value: T?)

    // ---- 全员缓存 ----

    fun getGlobal(request: GlobalCacheRequest): CacheHit<TClientVersionConfig>? =
        getByKey(globalKey(request))

    fun putGlobal(request: GlobalCacheRequest, record: TClientVersionConfig?) {
        putByKey(globalKey(request), record)
    }

    // ---- 用户配置存在性缓存 ----

    fun getUserScopeExists(request: UserScopeRequest): Boolean? {
        return try {
            when (redisOperation.get(userScopeKey(request))) {
                null -> null
                TRUE_SENTINEL -> true
                FALSE_SENTINEL -> false
                else -> null
            }
        } catch (e: Exception) {
            logger.warn(
                "[client-upgrade-cache] Get failed key={}: {}",
                userScopeKey(request),
                e.message,
            )
            null
        }
    }

    fun putUserScopeExists(request: UserScopeRequest, exists: Boolean) {
        try {
            val payload = if (exists) TRUE_SENTINEL else FALSE_SENTINEL
            redisOperation.set(
                key = userScopeKey(request),
                value = payload,
                expiredInSecond = TTL_SEC,
            )
        } catch (e: Exception) {
            logger.warn(
                "[client-upgrade-cache] Put failed key={}: {}",
                userScopeKey(request),
                e.message,
            )
        }
    }

    // ---- 用户专属缓存 ----
    // null sentinel 表示该 (productId, platform, arch, userId) 无启用配置

    fun getUser(request: UpgradeCacheRequest): CacheHit<TClientVersionConfig>? =
        getByKey(userKey(request))

    fun putUser(request: UpgradeCacheRequest, record: TClientVersionConfig?) {
        putByKey(userKey(request), record)
    }

    // ---- 内部实现 ----

    private fun getByKey(key: String): CacheHit<TClientVersionConfig>? {
        return try {
            val json = redisOperation.get(key) ?: return null
            if (json == NULL_SENTINEL) return CacheHit(null)
            CacheHit(cacheMapper.readValue(json, TClientVersionConfig::class.java))
        } catch (e: Exception) {
            logger.warn("[client-upgrade-cache] Get failed key={}: {}", key, e.message)
            null
        }
    }

    private fun putByKey(key: String, record: TClientVersionConfig?) {
        try {
            val payload = record
                ?.let { cacheMapper.writeValueAsString(it) }
                ?: NULL_SENTINEL
            redisOperation.set(key = key, value = payload, expiredInSecond = TTL_SEC)
        } catch (e: Exception) {
            logger.warn("[client-upgrade-cache] Put failed key={}: {}", key, e.message)
        }
    }

    private fun globalKey(r: GlobalCacheRequest): String = buildString {
        append(GLOBAL_PREFIX).append(':')
        append(enc(r.productId)).append(':')
        append(enc(r.platform)).append(':')
        append(enc(r.arch))
    }

    private fun userKey(r: UpgradeCacheRequest): String = buildString {
        append(USER_PREFIX).append(':')
        append(enc(r.productId)).append(':')
        append(enc(r.platform)).append(':')
        append(enc(r.arch)).append(':')
        append(enc(r.targetUserId))
    }

    private fun userScopeKey(r: UserScopeRequest): String = buildString {
        append(USER_SCOPE_EXISTS_PREFIX).append(':')
        append(enc(r.productId)).append(':')
        append(enc(r.platform)).append(':')
        append(enc(r.arch))
    }

    private fun enc(value: String): String {
        require(value.isNotBlank()) { "cache key segment cannot be blank" }
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
    }

    data class GlobalCacheRequest(
        val productId: String,
        val platform: String,
        val arch: String,
    )

    data class UpgradeCacheRequest(
        val productId: String,
        val platform: String,
        val arch: String,
        val targetUserId: String,
    )

    data class UserScopeRequest(
        val productId: String,
        val platform: String,
        val arch: String,
    )

    companion object {
        private const val NULL_SENTINEL = "__NULL__"
        private const val FALSE_SENTINEL = "0"
        private const val GLOBAL_PREFIX = "repo:client_upgrade:config:global"
        private const val TRUE_SENTINEL = "1"
        private const val USER_PREFIX = "repo:client_upgrade:config:user"
        private const val USER_SCOPE_EXISTS_PREFIX =
            "repo:client_upgrade:scope:user-exists"
        private val TTL_SEC = TimeUnit.HOURS.toSeconds(1)
        private val logger = LoggerFactory.getLogger(ClientVersionConfigCache::class.java)
    }
}
