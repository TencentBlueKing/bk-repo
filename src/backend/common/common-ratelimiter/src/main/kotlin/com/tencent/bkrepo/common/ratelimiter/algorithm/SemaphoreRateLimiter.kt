package com.tencent.bkrepo.common.ratelimiter.algorithm

import java.util.concurrent.atomic.AtomicLong

/**
 * 本地信号量限流器：基于 AtomicLong 实现真正的并发数控制
 * 与速率限流不同，不依赖时间窗口，通过 tryAcquire/release 配对使用
 */
class SemaphoreRateLimiter(
    private val maxConcurrent: Long,
    private val keepConnection: Boolean = true,
) : RateLimiter {

    private val current = AtomicLong(0L)

    override fun tryAcquire(permits: Long): Boolean {
        while (true) {
            val cur = current.get()
            if (cur + permits > maxConcurrent) return false
            if (current.compareAndSet(cur, cur + permits)) return true
        }
    }

    override fun release(permits: Long) {
        current.updateAndGet { maxOf(0L, it - permits) }
    }

    override fun removeCacheLimit(key: String) {
        current.set(0L)
    }

    override fun getLimitPerSecond(): Long = maxConcurrent

    override fun keepConnection(): Boolean = keepConnection
}
