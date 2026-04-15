package com.tencent.bkrepo.common.ratelimiter.algorithm

import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import com.tencent.bkrepo.common.ratelimiter.redis.LuaScript
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import java.time.Duration
import java.util.UUID

/**
 * 分布式信号量限流器：基于 Redis SortedSet 实现真正的并发数控制
 *
 * 每个连接用唯一 UUID 作为 SortedSet member，score = 过期时间戳（毫秒）。
 * acquire 时先清理过期 member，再计数判断，通过则写入 UUID；
 * release 时精确 ZREM 自己的 UUID。
 *
 * 崩溃恢复：Pod 崩溃后未 release 的 UUID 在 safetyTtl（resourceLimit.duration）后自动被
 * 下一个 acquire 的 ZREMRANGEBYSCORE 清理，无需等待整个 key 过期。
 *
 * connectionUuid 是实例级 ThreadLocal：同一资源的所有请求共享此 RateLimiter 实例，
 * 每个线程独立持有自己的 UUID，preHandle/afterCompletion 同线程保证配对。
 *
 * ⚠️ 仅适用于同步 Servlet 模型（Spring MVC 阻塞线程）：
 * 若请求在 preHandle 与 afterCompletion 之间发生线程切换（如 WebFlux、异步 Controller、
 * Kotlin 协程、虚拟线程），ThreadLocal 将无法跨线程传递，导致 release 错误槽位或漏 release。
 * 禁止在 WebFlux 或任何异步/协程场景中使用本类。
 */
class DistributedSemaphoreRateLimiter(
    private val key: String,
    private val maxConcurrent: Long,
    private val safetyTtl: Duration,
    private val redisTemplate: RedisTemplate<String, String>,
    private val keepConnection: Boolean = true,
) : RateLimiter {

    private val acquireScript by lazy {
        DefaultRedisScript(LuaScript.semaphoreAcquireScript, Long::class.java)
    }

    private val releaseScript by lazy {
        DefaultRedisScript(LuaScript.semaphoreReleaseScript, Long::class.java)
    }

    // 每个线程持有自己本次 acquire 的 UUID，确保 release 精确删除自己的槽位
    private val connectionUuid = ThreadLocal<String?>()

    override fun tryAcquire(permits: Long): Boolean {
        return try {
            val uuid = UUID.randomUUID().toString()
            val nowMs = System.currentTimeMillis()
            val result = redisTemplate.execute(
                acquireScript,
                listOf(key),
                maxConcurrent.toString(),
                safetyTtl.toMillis().toString(),
                nowMs.toString(),
                uuid,
            )
            if (result == 1L) {
                connectionUuid.set(uuid)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.warn("${this.javaClass.simpleName} acquire error: ${e.message}")
            throw AcquireLockFailedException("distributed semaphore acquire failed: $e")
        }
    }

    override fun release(permits: Long) {
        val uuid = connectionUuid.get() ?: return
        connectionUuid.remove()
        try {
            redisTemplate.execute(releaseScript, listOf(key), uuid)
        } catch (e: Exception) {
            logger.warn("${this.javaClass.simpleName} release error, uuid=$uuid: ${e.message}")
        }
    }

    override fun removeCacheLimit(key: String) {
        connectionUuid.remove()
        try {
            redisTemplate.delete(key)
        } catch (e: Exception) {
            logger.warn("${this.javaClass.simpleName} removeCacheLimit error: ${e.message}")
        }
    }

    override fun getLimitPerSecond(): Long = maxConcurrent

    override fun keepConnection(): Boolean = keepConnection

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(DistributedSemaphoreRateLimiter::class.java)
    }
}
