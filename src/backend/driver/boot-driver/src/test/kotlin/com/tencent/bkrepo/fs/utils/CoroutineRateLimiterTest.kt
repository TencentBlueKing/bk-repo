package com.tencent.bkrepo.fs.utils

import com.tencent.bkrepo.fs.server.utils.CoroutineRateLimiter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@DisplayName("CoroutineRateLimiter 令牌桶限速器")
class CoroutineRateLimiterTest {

    @Test
    fun `should not limit when disabled or permits invalid`() = runBlocking {
        listOf(0, -1).forEach { qps ->
            val limiter = CoroutineRateLimiter(qps)
            val elapsedMs = measureMs { repeat(1000) { limiter.acquire(1) } }
            assertTrue(elapsedMs < 500, "qps=$qps should not throttle, but took ${elapsedMs}ms")
        }

        val defaultLimiter = CoroutineRateLimiter()
        val defaultElapsedMs = measureMs { repeat(1000) { defaultLimiter.acquire(1) } }
        assertTrue(defaultElapsedMs < 500, "Default limiter should not throttle, but took ${defaultElapsedMs}ms")

        val limiter = CoroutineRateLimiter(1)
        val invalidPermitsElapsedMs = measureMs {
            limiter.acquire(0)
            limiter.acquire(-1)
        }
        assertTrue(invalidPermitsElapsedMs < 100, "Invalid permits should return immediately")
    }

    @Test
    fun `acquire should enforce token bucket rate with prepay model`() = runBlocking {
        val burstLimiter = CoroutineRateLimiter(100)
        delay(1100)
        val burstElapsedMs = measureMs { burstLimiter.acquire(100) }
        assertTrue(burstElapsedMs < 200, "Burst should be instant, but took ${burstElapsedMs}ms")

        val throttleLimiter = CoroutineRateLimiter(50)
        throttleLimiter.acquire(50)
        val throttleElapsedMs = measureMs { throttleLimiter.acquire(1) }
        assertTrue(throttleElapsedMs >= 10, "Should wait for token refill, but only waited ${throttleElapsedMs}ms")

        val qps = 20
        val totalPermits = 60
        val sustainedLimiter = CoroutineRateLimiter(qps)
        val sustainedElapsedMs = measureMs { repeat(totalPermits) { sustainedLimiter.acquire(1) } }
        val expectedMinMs = ((totalPermits - qps).toLong() * 1000) / qps - 200
        assertTrue(
            sustainedElapsedMs >= expectedMinMs,
            "Acquiring $totalPermits permits at $qps QPS should take at least ${expectedMinMs}ms," +
                    " but took ${sustainedElapsedMs}ms",
        )

        val prepayLimiter = CoroutineRateLimiter(10)
        delay(1100)
        val prepayElapsedMs = measureMs { prepayLimiter.acquire(15) }
        assertTrue(prepayElapsedMs < 200, "Prepay model should return immediately, but took ${prepayElapsedMs}ms")
    }

    @Test
    fun `setRate should update limit behavior`() = runBlocking {
        val raiseRateLimiter = CoroutineRateLimiter(10)
        delay(1100)
        raiseRateLimiter.acquire(10)
        raiseRateLimiter.setRate(1000)
        delay(10)
        val raiseRateElapsedMs = measureMs { raiseRateLimiter.acquire(1) }
        assertTrue(raiseRateElapsedMs < 100, "Raised rate should allow fast acquire, but took ${raiseRateElapsedMs}ms")

        val disableLimiter = CoroutineRateLimiter(10)
        disableLimiter.acquire(10)
        disableLimiter.setRate(0)
        val disableElapsedMs = measureMs { repeat(100) { disableLimiter.acquire(1) } }
        assertTrue(disableElapsedMs < 200, "setRate(0) should disable limiting, but took ${disableElapsedMs}ms")

        val preserveLimiter = CoroutineRateLimiter(100)
        delay(1100)
        preserveLimiter.setRate(50)
        val preserveElapsedMs = measureMs { preserveLimiter.acquire(50) }
        assertTrue(preserveElapsedMs < 200, "Preserved tokens should satisfy acquire, but took ${preserveElapsedMs}ms")
    }

    @Test
    fun `tryAcquire without timeout should follow prepay semantics`() = runBlocking {
        val limiter = CoroutineRateLimiter(10)
        delay(1100)
        assertTrue(limiter.tryAcquire(5))
        assertTrue(limiter.tryAcquire(5))
        assertTrue(limiter.tryAcquire(3), "Prepay: no prior debt should succeed even when stored insufficient")

        val debtLimiter = CoroutineRateLimiter(10)
        delay(1100)
        debtLimiter.acquire(15)
        assertFalse(debtLimiter.tryAcquire(1), "Should fail when prior acquire created debt")
        delay(1100)
        assertTrue(debtLimiter.tryAcquire(1), "Failed tryAcquire should not modify state")

        val zeroRateLimiter = CoroutineRateLimiter(0)
        repeat(100) { assertTrue(zeroRateLimiter.tryAcquire(1)) }

        val invalidPermitsLimiter = CoroutineRateLimiter(1)
        assertTrue(invalidPermitsLimiter.tryAcquire(0))
        assertTrue(invalidPermitsLimiter.tryAcquire(-1))
    }

    @Test
    fun `tryAcquire with timeout should respect deadline`() = runBlocking {
        val timeoutLimiter = CoroutineRateLimiter(1)
        timeoutLimiter.acquire(1)
        assertFalse(
            timeoutLimiter.tryAcquire(permits = 10, timeout = 200.milliseconds),
            "Should return false when debt exceeds timeout",
        )

        val successLimiter = CoroutineRateLimiter(100)
        successLimiter.acquire(100)
        val successElapsedMs = measureMs {
            assertTrue(successLimiter.tryAcquire(permits = 5, timeout = 2.seconds))
        }
        assertTrue(successElapsedMs < 2000, "Should acquire within timeout, but took ${successElapsedMs}ms")

        val unchangedLimiter = CoroutineRateLimiter(10)
        unchangedLimiter.acquire(10)
        assertFalse(unchangedLimiter.tryAcquire(permits = 1, timeout = 50.milliseconds))
        delay(1100)
        assertTrue(unchangedLimiter.tryAcquire(1), "State should be unchanged after failed tryAcquire")
    }

    @Test
    fun `should be fair and correct under concurrency`() = runBlocking {
        val qps = 100
        val limiter = CoroutineRateLimiter(qps)
        val acquiredCount = AtomicInteger(0)
        val concurrency = 10
        val permitsPerCoroutine = 20
        val concurrentElapsedMs = measureMs {
            (1..concurrency).map {
                async {
                    repeat(permitsPerCoroutine) {
                        limiter.acquire(1)
                        acquiredCount.incrementAndGet()
                    }
                }
            }.awaitAll()
        }
        val totalPermits = concurrency * permitsPerCoroutine
        assertEquals(totalPermits, acquiredCount.get())
        val expectedMinMs = ((totalPermits - qps).toLong() * 1000) / qps - 300
        assertTrue(
            concurrentElapsedMs >= expectedMinMs,
            "Concurrent acquire should respect rate, expected at least ${expectedMinMs}ms," +
                    " but took ${concurrentElapsedMs}ms",
        )

        val fifoLimiter = CoroutineRateLimiter(10)
        delay(1100)
        fifoLimiter.acquire(10)
        val completionOrder = CopyOnWriteArrayList<Int>()
        (1..5).map { index ->
            async {
                fifoLimiter.acquire(1)
                completionOrder.add(index)
            }
        }.awaitAll()
        assertEquals(listOf(1, 2, 3, 4, 5), completionOrder)

        val starvationLimiter = CoroutineRateLimiter(50)
        val completed = AtomicInteger(0)
        (1..10).map {
            async {
                repeat(5) { starvationLimiter.acquire(1) }
                completed.incrementAndGet()
            }
        }.awaitAll()
        assertEquals(10, completed.get())

        val debtLimiter = CoroutineRateLimiter(100)
        debtLimiter.acquire(100)
        val debtElapsedMs = measureMs { debtLimiter.acquire(10) }
        assertTrue(
            debtElapsedMs >= 800,
            "Next caller should wait for previous debt, but only waited ${debtElapsedMs}ms"
        )
        assertTrue(debtElapsedMs < 2000, "Debt wait should not exceed ~1s, but took ${debtElapsedMs}ms")
    }

    private inline fun measureMs(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }
}
