package com.tencent.bkrepo.common.ratelimiter.utils

import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedFixedWindowRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedLeakyRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedSlidingWindowRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedTokenBucketRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.FixedWindowRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.LeakyRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.RateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.SlidingWindowRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.TokenBucketRateLimiter
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.data.redis.core.RedisTemplate
import java.time.Duration

/**
 * RateLimiterBuilder 纯单元测试（无 Spring 上下文）
 *
 * 覆盖 4 种算法 × 2 种 scope = 8 个创建路径，以及全部异常分支。
 * GLOBAL 算法使用 mock RedisTemplate，仅验证实例类型，不触发 Redis 调用。
 */
@Suppress("UNCHECKED_CAST")
class RateLimiterBuilderTest {

    private val redisTemplate = mock(RedisTemplate::class.java) as RedisTemplate<String, String>
    private val resource = "/test/resource"

    // ─── 错误输入校验 ─────────────────────────────────────────────────────────────

    @Test
    fun `createAlgorithmOfRateLimiter — throws InvalidResourceException when limit is negative`() {
        val limit = baseLimit(Algorithms.FIXED_WINDOW, WorkScope.LOCAL).copy(limit = -1)
        Assertions.assertThrows(InvalidResourceException::class.java) {
            RateLimiterBuilder.createAlgorithmOfRateLimiter(resource, limit)
        }
    }

    @Test
    fun `createAlgorithmOfRateLimiter — throws InvalidResourceException for unknown algorithm name`() {
        val limit = baseLimit(Algorithms.FIXED_WINDOW, WorkScope.LOCAL).copy(algo = "UNKNOWN_ALGO")
        Assertions.assertThrows(InvalidResourceException::class.java) {
            RateLimiterBuilder.createAlgorithmOfRateLimiter(resource, limit)
        }
    }

    @Test
    fun `createAlgorithmOfRateLimiter — throws InvalidResourceException for empty algorithm name`() {
        val limit = baseLimit(Algorithms.FIXED_WINDOW, WorkScope.LOCAL).copy(algo = "")
        Assertions.assertThrows(InvalidResourceException::class.java) {
            RateLimiterBuilder.createAlgorithmOfRateLimiter(resource, limit)
        }
    }

    @Test
    fun `createAlgorithmOfRateLimiter — throws InvalidResourceException when LEAKY_BUCKET has no capacity`() {
        val limit = baseLimit(Algorithms.LEAKY_BUCKET, WorkScope.LOCAL).copy(capacity = null)
        Assertions.assertThrows(InvalidResourceException::class.java) {
            RateLimiterBuilder.createAlgorithmOfRateLimiter(resource, limit)
        }
    }

    @Test
    fun `createAlgorithmOfRateLimiter — throws InvalidResourceException when LEAKY_BUCKET capacity is zero`() {
        val limit = baseLimit(Algorithms.LEAKY_BUCKET, WorkScope.LOCAL).copy(capacity = 0)
        Assertions.assertThrows(InvalidResourceException::class.java) {
            RateLimiterBuilder.createAlgorithmOfRateLimiter(resource, limit)
        }
    }

    @Test
    fun `createAlgorithmOfRateLimiter — throws InvalidResourceException when LEAKY_BUCKET capacity is negative`() {
        val limit = baseLimit(Algorithms.LEAKY_BUCKET, WorkScope.LOCAL).copy(capacity = -5)
        Assertions.assertThrows(InvalidResourceException::class.java) {
            RateLimiterBuilder.createAlgorithmOfRateLimiter(resource, limit)
        }
    }

    @Test
    fun `createAlgorithmOfRateLimiter — throws InvalidResourceException when GLOBAL TOKEN_BUCKET has no capacity`() {
        val limit = baseLimit(Algorithms.TOKEN_BUCKET, WorkScope.GLOBAL).copy(capacity = null)
        Assertions.assertThrows(InvalidResourceException::class.java) {
            RateLimiterBuilder.createAlgorithmOfRateLimiter(resource, limit, redisTemplate)
        }
    }

    @Test
    fun `createAlgorithmOfRateLimiter — throws InvalidResourceException when GLOBAL TOKEN_BUCKET capacity is zero`() {
        val limit = baseLimit(Algorithms.TOKEN_BUCKET, WorkScope.GLOBAL).copy(capacity = 0)
        Assertions.assertThrows(InvalidResourceException::class.java) {
            RateLimiterBuilder.createAlgorithmOfRateLimiter(resource, limit, redisTemplate)
        }
    }

    // ─── 算法 × scope 矩阵（8 种组合） ──────────────────────────────────────────

    @Test
    fun `createAlgorithmOfRateLimiter — FIXED_WINDOW LOCAL returns FixedWindowRateLimiter`() {
        val rateLimiter = RateLimiterBuilder.createAlgorithmOfRateLimiter(
            resource, baseLimit(Algorithms.FIXED_WINDOW, WorkScope.LOCAL)
        )
        Assertions.assertInstanceOf(FixedWindowRateLimiter::class.java, rateLimiter)
    }

    @Test
    fun `createAlgorithmOfRateLimiter — FIXED_WINDOW GLOBAL returns DistributedFixedWindowRateLimiter`() {
        val rateLimiter = RateLimiterBuilder.createAlgorithmOfRateLimiter(
            resource, baseLimit(Algorithms.FIXED_WINDOW, WorkScope.GLOBAL), redisTemplate
        )
        Assertions.assertInstanceOf(DistributedFixedWindowRateLimiter::class.java, rateLimiter)
    }

    @Test
    fun `createAlgorithmOfRateLimiter — SLIDING_WINDOW LOCAL returns SlidingWindowRateLimiter`() {
        val rateLimiter = RateLimiterBuilder.createAlgorithmOfRateLimiter(
            resource, baseLimit(Algorithms.SLIDING_WINDOW, WorkScope.LOCAL)
        )
        Assertions.assertInstanceOf(SlidingWindowRateLimiter::class.java, rateLimiter)
    }

    @Test
    fun `createAlgorithmOfRateLimiter — SLIDING_WINDOW GLOBAL returns DistributedSlidingWindowRateLimiter`() {
        val rateLimiter = RateLimiterBuilder.createAlgorithmOfRateLimiter(
            resource, baseLimit(Algorithms.SLIDING_WINDOW, WorkScope.GLOBAL), redisTemplate
        )
        Assertions.assertInstanceOf(DistributedSlidingWindowRateLimiter::class.java, rateLimiter)
    }

    @Test
    fun `createAlgorithmOfRateLimiter — TOKEN_BUCKET LOCAL returns TokenBucketRateLimiter`() {
        val rateLimiter = RateLimiterBuilder.createAlgorithmOfRateLimiter(
            resource, baseLimit(Algorithms.TOKEN_BUCKET, WorkScope.LOCAL)
        )
        Assertions.assertInstanceOf(TokenBucketRateLimiter::class.java, rateLimiter)
    }

    @Test
    fun `createAlgorithmOfRateLimiter — TOKEN_BUCKET GLOBAL returns DistributedTokenBucketRateLimiter`() {
        val rateLimiter = RateLimiterBuilder.createAlgorithmOfRateLimiter(
            resource, baseLimit(Algorithms.TOKEN_BUCKET, WorkScope.GLOBAL).copy(capacity = 10), redisTemplate
        )
        Assertions.assertInstanceOf(DistributedTokenBucketRateLimiter::class.java, rateLimiter)
    }

    @Test
    fun `createAlgorithmOfRateLimiter — LEAKY_BUCKET LOCAL returns LeakyRateLimiter`() {
        val rateLimiter = RateLimiterBuilder.createAlgorithmOfRateLimiter(
            resource, baseLimit(Algorithms.LEAKY_BUCKET, WorkScope.LOCAL).copy(capacity = 10)
        )
        Assertions.assertInstanceOf(LeakyRateLimiter::class.java, rateLimiter)
    }

    @Test
    fun `createAlgorithmOfRateLimiter — LEAKY_BUCKET GLOBAL returns DistributedLeakyRateLimiter`() {
        val rateLimiter = RateLimiterBuilder.createAlgorithmOfRateLimiter(
            resource, baseLimit(Algorithms.LEAKY_BUCKET, WorkScope.GLOBAL).copy(capacity = 10), redisTemplate
        )
        Assertions.assertInstanceOf(DistributedLeakyRateLimiter::class.java, rateLimiter)
    }

    // ─── limit = 0 的边界（合法值，不应抛出）──────────────────────────────────────

    @Test
    fun `createAlgorithmOfRateLimiter — limit=0 is valid and creates FixedWindowRateLimiter`() {
        val limit = baseLimit(Algorithms.FIXED_WINDOW, WorkScope.LOCAL).copy(limit = 0)
        val rateLimiter = RateLimiterBuilder.createAlgorithmOfRateLimiter(resource, limit)
        Assertions.assertInstanceOf(FixedWindowRateLimiter::class.java, rateLimiter)
    }

    // ─── getAlgorithmOfRateLimiter 缓存语义 ───────────────────────────────────────

    @Test
    fun `getAlgorithmOfRateLimiter — returns same instance for same key (cache hit)`() {
        val cache = java.util.concurrent.ConcurrentHashMap<String, RateLimiter>()
        val limit = baseLimit(Algorithms.FIXED_WINDOW, WorkScope.LOCAL)

        val first = RateLimiterBuilder.getAlgorithmOfRateLimiter(
            limitKey = "cache-key", resourceLimit = limit, rateLimiterCache = cache
        )
        val second = RateLimiterBuilder.getAlgorithmOfRateLimiter(
            limitKey = "cache-key", resourceLimit = limit, rateLimiterCache = cache
        )

        Assertions.assertSame(first, second, "same key should return cached instance")
    }

    @Test
    fun `getAlgorithmOfRateLimiter — returns different instances for different keys`() {
        val cache = java.util.concurrent.ConcurrentHashMap<String, RateLimiter>()
        val limit = baseLimit(Algorithms.FIXED_WINDOW, WorkScope.LOCAL)

        val first = RateLimiterBuilder.getAlgorithmOfRateLimiter(
            limitKey = "key-A", resourceLimit = limit, rateLimiterCache = cache
        )
        val second = RateLimiterBuilder.getAlgorithmOfRateLimiter(
            limitKey = "key-B", resourceLimit = limit, rateLimiterCache = cache
        )

        Assertions.assertNotSame(first, second)
    }

    // ─── helper ───────────────────────────────────────────────────────────────────

    private fun baseLimit(algo: Algorithms, scope: WorkScope): ResourceLimit {
        return ResourceLimit(
            algo = algo.name,
            resource = "/",
            limitDimension = LimitDimension.URL.name,
            limit = 10,
            duration = Duration.ofSeconds(1),
            scope = scope.name,
        )
    }
}
