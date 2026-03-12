/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.ratelimiter.stream

import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.repository.RateLimitRepository
import com.tencent.bkrepo.common.ratelimiter.service.evict.DownloadEvictRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Duration

class EvictableInputStreamTest {

    private lateinit var evictService: DownloadEvictRateLimiterService
    private lateinit var meterRegistry: MeterRegistry

    @BeforeEach
    fun setup() {
        val scheduler = ThreadPoolTaskScheduler().apply { initialize() }
        val properties = RateLimiterProperties().apply { enabled = true }
        meterRegistry = SimpleMeterRegistry()
        val rateLimiterMetrics = RateLimiterMetrics(meterRegistry)
        val rateLimitRepository = Mockito.mock(RateLimitRepository::class.java)
        val configService = RateLimiterConfigService(rateLimitRepository)
        @Suppress("UNCHECKED_CAST")
        val redisTemplate = Mockito.mock(RedisTemplate::class.java) as RedisTemplate<String, String>
        evictService = DownloadEvictRateLimiterService(
            taskScheduler = scheduler,
            rateLimiterProperties = properties,
            rateLimiterMetrics = rateLimiterMetrics,
            redisTemplate = redisTemplate,
            rateLimiterConfigService = configService,
        )
    }

    @Test
    fun `no rule — read passes through without IOException`() {
        val stream = EvictableInputStream(
            ByteArrayInputStream(byteArrayOf(1, 2, 3)),
            EvictContext("user1", "1.1.1.1", "proj", "repo"),
            evictService,
        )
        Assertions.assertDoesNotThrow { stream.read() }
        Assertions.assertDoesNotThrow { stream.read(ByteArray(2)) }
        Assertions.assertDoesNotThrow { stream.read(ByteArray(2), 0, 2) }
        stream.close()
    }

    @Test
    fun `connection within limit — read does not throw`() {
        val rule = ResourceLimit(
            algo = Algorithms.FIXED_WINDOW.name,
            resource = "/proj/repo",
            limitDimension = LimitDimension.DOWNLOAD_EVICT.name,
            limit = 3600,
            duration = Duration.ofSeconds(1),
            scope = WorkScope.LOCAL.name,
        )
        evictService.rateLimiterProperties.rules = listOf(rule)
        evictService.refreshRateLimitRule()

        val stream = EvictableInputStream(
            ByteArrayInputStream(byteArrayOf(1, 2, 3)),
            EvictContext("user1", "1.1.1.1", "proj", "repo"),
            evictService,
        )
        Assertions.assertDoesNotThrow { stream.read() }
        stream.close()
    }

    @Test
    fun `connection past limit — read throws IOException`() {
        val rule = ResourceLimit(
            algo = Algorithms.FIXED_WINDOW.name,
            resource = "/proj/repo",
            limitDimension = LimitDimension.DOWNLOAD_EVICT.name,
            limit = -1,
            duration = Duration.ofSeconds(1),
            scope = WorkScope.LOCAL.name,
            capacity = null,
        )
        evictService.rateLimiterProperties.rules = listOf(rule)
        evictService.refreshRateLimitRule()

        val stream = EvictableInputStream(
            ByteArrayInputStream(byteArrayOf(1, 2, 3)),
            EvictContext("user1", "1.1.1.1", "proj", "repo"),
            evictService,
        )
        Assertions.assertThrows(IOException::class.java) { stream.read() }
        stream.close()
    }

    @Test
    fun `capacity grace period — read does not throw even when aliveSeconds exceeds limit`() {
        // capacity=3600 means 3600s guaranteed — so even with limit=0, it won't evict
        val rule = ResourceLimit(
            algo = Algorithms.FIXED_WINDOW.name,
            resource = "/proj/repo",
            limitDimension = LimitDimension.DOWNLOAD_EVICT.name,
            limit = 0,
            duration = Duration.ofSeconds(1),
            scope = WorkScope.LOCAL.name,
            capacity = 3600,
        )
        evictService.rateLimiterProperties.rules = listOf(rule)
        evictService.refreshRateLimitRule()

        val stream = EvictableInputStream(
            ByteArrayInputStream(byteArrayOf(1, 2, 3)),
            EvictContext("user1", "1.1.1.1", "proj", "repo"),
            evictService,
        )
        Assertions.assertDoesNotThrow { stream.read() }
        stream.close()
    }

    @Test
    fun `project rule matched by priority over ip rule`() {
        val projectRule = ResourceLimit(
            algo = Algorithms.FIXED_WINDOW.name,
            resource = "/proj",
            limitDimension = LimitDimension.DOWNLOAD_EVICT.name,
            limit = -1,
            duration = Duration.ofSeconds(1),
            scope = WorkScope.LOCAL.name,
        )
        val ipRule = ResourceLimit(
            algo = Algorithms.FIXED_WINDOW.name,
            resource = "/ip/1.1.1.1",
            limitDimension = LimitDimension.DOWNLOAD_EVICT.name,
            limit = 3600,
            duration = Duration.ofSeconds(1),
            scope = WorkScope.LOCAL.name,
        )
        evictService.rateLimiterProperties.rules = listOf(projectRule, ipRule)
        evictService.refreshRateLimitRule()

        // /proj/repo matched first (higher priority), limit=0 → throws
        val stream = EvictableInputStream(
            ByteArrayInputStream(byteArrayOf(1)),
            EvictContext("user1", "1.1.1.1", "proj", "repo"),
            evictService,
        )
        Assertions.assertThrows(IOException::class.java) { stream.read() }
        stream.close()
    }

    @Test
    fun `user rule matched when no project-repo rule exists`() {
        val userRule = ResourceLimit(
            algo = Algorithms.FIXED_WINDOW.name,
            resource = "/user/user1",
            limitDimension = LimitDimension.DOWNLOAD_EVICT.name,
            limit = -1,
            duration = Duration.ofSeconds(1),
            scope = WorkScope.LOCAL.name,
        )
        evictService.rateLimiterProperties.rules = listOf(userRule)
        evictService.refreshRateLimitRule()

        val stream = EvictableInputStream(
            ByteArrayInputStream(byteArrayOf(1)),
            EvictContext("user1", "1.1.1.1", null, null),
            evictService,
        )
        Assertions.assertThrows(IOException::class.java) { stream.read() }
        stream.close()
    }
}
