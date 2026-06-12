package com.tencent.bkrepo.fs.server.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * 协程场景下的令牌桶限速器，参考 Guava SmoothBursty RateLimiter 设计。
 *
 * 以固定速率向桶中补充令牌，请求时消耗令牌；令牌不足时通过挂起协程等待，不阻塞线程。
 * 采用预付模型：当前调用方只等待前人债务，自身消耗产生的新债务由下一个调用方承担。
 * 通过 [Mutex.withLock]（FIFO 挂起锁）保证严格按调用顺序获得许可，不会饿死。
 * 桶容量等于 permitsPerSecond，最多积攒 1 秒的突发量。
 *
 * @param permitsPerSecond 每秒允许的许可数，小于等于 0 表示不限制
 */
class CoroutineRateLimiter(permitsPerSecond: Int = 0) {
    private val mutex = Mutex()
    private var storedPermits = 0.0
    private var maxPermits = 0.0
    private var intervalNanos = 0.0
    private var nextFreeNanos = System.nanoTime()

    init {
        applyRate(permitsPerSecond)
    }

    /**
     * 动态调整速率，立即生效。
     *
     * @param permitsPerSecond 每秒允许的许可数，小于等于 0 表示不限制
     */
    suspend fun setRate(permitsPerSecond: Int) {
        mutex.withLock {
            resync(System.nanoTime())
            applyRate(permitsPerSecond)
        }
    }

    /**
     * 获取指定数量的许可，无限等待直到获取成功。
     * 当速率未设置或小于等于 0 时，立即放行。
     *
     * @param permits 需要的许可数
     */
    suspend fun acquire(permits: Int = 1) {
        tryAcquire(permits, Duration.INFINITE)
    }

    /**
     * 尝试在指定超时内获取许可。
     * 超时从调用时刻开始计算，包含等锁时间和债务等待时间。
     * 成功时预留许可并等待债务后返回 `true`；超时返回 `false`，不修改任何状态。
     * 当速率未设置或小于等于 0 时，始终立即返回 `true`。
     *
     * - `timeout = Duration.ZERO`：非阻塞尝试，无前人债务即放行，否则立即返回 `false`
     * - `timeout = Duration.INFINITE`：等同于 [acquire]，无限等待，始终返回 `true`
     *
     * @param permits 需要的许可数
     * @param timeout 最大等待时长，默认 [Duration.ZERO]
     * @return 是否成功获取许可
     */
    suspend fun tryAcquire(permits: Int = 1, timeout: Duration = Duration.ZERO): Boolean {
        if (permits <= 0) {
            return true
        }
        val deadlineNanos = if (timeout.isInfinite()) {
            Long.MAX_VALUE
        } else {
            System.nanoTime() + timeout.inWholeNanoseconds
        }
        val waitNanos = reserve(permits, deadlineNanos) ?: return false
        if (waitNanos > 0) {
            delay(waitNanos.nanoseconds)
        }
        return true
    }

    /**
     * FIFO 预留（Guava 预付模型）：
     * 在挂起锁内原子计算等待时间并推进 [nextFreeNanos]。
     * [deadlineNanos] 为 [Long.MAX_VALUE] 时无限等待；
     * 否则拿到锁后检查前人债务等待时间是否超出剩余预算，超时则不修改状态，返回 null。
     */
    private suspend fun reserve(permits: Int, deadlineNanos: Long): Long? {
        return mutex.withLock {
            if (intervalNanos <= 0.0) {
                return@withLock 0L
            }
            val now = System.nanoTime()
            resync(now)
            val waitNanos = maxOf(nextFreeNanos - now, 0L)
            if (deadlineNanos != Long.MAX_VALUE && waitNanos > deadlineNanos - now) {
                return@withLock null
            }
            val storedToSpend = minOf(permits.toDouble(), storedPermits)
            val freshPermits = permits - storedToSpend
            val freshWaitNanos = (freshPermits * intervalNanos).toLong()
            nextFreeNanos = maxOf(nextFreeNanos, now) + freshWaitNanos
            storedPermits -= storedToSpend
            waitNanos
        }
    }

    private fun resync(now: Long) {
        if (now > nextFreeNanos && intervalNanos > 0.0) {
            val newPermits = (now - nextFreeNanos) / intervalNanos
            storedPermits = minOf(maxPermits, storedPermits + newPermits)
            nextFreeNanos = now
        }
    }

    private fun applyRate(permitsPerSecond: Int) {
        if (permitsPerSecond > 0) {
            intervalNanos = NANOS_PER_SECOND / permitsPerSecond
            maxPermits = permitsPerSecond.toDouble()
            storedPermits = storedPermits.coerceAtMost(maxPermits)
        } else {
            intervalNanos = 0.0
            maxPermits = 0.0
        }
    }

    companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
