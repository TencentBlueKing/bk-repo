/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 */
package com.tencent.bkrepo.common.ratelimiter.stream

import com.tencent.bkrepo.common.ratelimiter.algorithm.RateLimiter
import com.tencent.bkrepo.common.ratelimiter.config.BandwidthProperties
import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.service.AbstractBandwidthRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.bandwidth.DownloadBandwidthRateLimiterService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.annotation.DirtiesContext
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommonRateLimitInputStreamTest {

    @BeforeEach
    fun setUp() {
        AbstractBandwidthRateLimiterService.Companion.redisTemplate =
            mock(RedisTemplate::class.java) as RedisTemplate<String, String>
        DownloadBandwidthRateLimiterService.Companion.rateLimiterCache =
            mock(ConcurrentHashMap::class.java) as ConcurrentHashMap<String, RateLimiter>
        DownloadBandwidthRateLimiterService.Companion.rateLimitRule = mock(RateLimitRule::class.java)

    }

    @Test
    fun `test tryAcquireOrWait when rangeLength is null`() {
        val rateLimiter = mock(RateLimiter::class.java, "1")
        val stream = createTestStream()
        val method = getPrivateMethod("tryAcquireOrWait")
        val bytes = 100
        AbstractBandwidthRateLimiterService.Companion.rateLimiterProperties =
            RateLimiterProperties(bandwidthProperties = stream.rateCheckContext.bandwidthProperties)
        `when`(rateLimiter.getLimitPerSecond()).thenReturn(5000)
        `when`(DownloadBandwidthRateLimiterService.Companion.rateLimiterCache[stream.rateCheckContext.limitKey])
            .thenReturn(rateLimiter)
        `when`(rateLimiter.tryAcquire(bytes.toLong())).thenReturn(true)
        method.call(stream, bytes)
        assertEquals(0, stream.getPrivateField("applyNum").toInt())
    }

    @Test
    fun `test tryAcquireOrWait when rangeLength is zero`() {
        val rateLimiter = mock(RateLimiter::class.java, "2")
        val stream = createTestStream(rangeLength = 0)
        val method = getPrivateMethod("tryAcquireOrWait")
        val bytes = 100
        AbstractBandwidthRateLimiterService.Companion.rateLimiterProperties =
            RateLimiterProperties(bandwidthProperties = stream.rateCheckContext.bandwidthProperties)
        `when`(rateLimiter.getLimitPerSecond()).thenReturn(5000)
        `when`(DownloadBandwidthRateLimiterService.Companion.rateLimiterCache[stream.rateCheckContext.limitKey])
            .thenReturn(rateLimiter)
        `when`(rateLimiter.tryAcquire(bytes.toLong())).thenReturn(true)
        method.call(stream, bytes)
        assertEquals(0, stream.getPrivateField("applyNum").toInt())
    }

    @Test
    fun `test tryAcquireOrWait when bytes larger than limitPerSecond`() {
        val rateLimiter = mock(RateLimiter::class.java, "3")
        val stream = createTestStream(rangeLength = 5000)
        val method = getPrivateMethod("tryAcquireOrWait")
        val bytes = 200
        AbstractBandwidthRateLimiterService.Companion.rateLimiterProperties =
            RateLimiterProperties(bandwidthProperties = stream.rateCheckContext.bandwidthProperties)
        `when`(rateLimiter.getLimitPerSecond()).thenReturn(100)
        `when`(DownloadBandwidthRateLimiterService.Companion.rateLimiterCache[stream.rateCheckContext.limitKey])
            .thenReturn(rateLimiter)
        `when`(rateLimiter.tryAcquire(anyLong())).thenReturn(true)
        method.call(stream, bytes)
        assertEquals(bytes, stream.getPrivateField("applyNum").toInt())
    }

    @Test
    fun `test tryAcquireOrWait when already have enough permits`() {
        val rateLimiter = mock(RateLimiter::class.java, "4")
        val stream = createTestStream(rangeLength = 5000)
        stream.setPrivateField("bytesRead", 100)
        stream.setPrivateField("applyNum", 300)
        val method = getPrivateMethod("tryAcquireOrWait")
        val bytes = 200
        method.call(stream, bytes)
        assertEquals(300, stream.getPrivateField("applyNum").toInt())
    }

    @Test
    fun `test tryAcquireOrWait when limitPerSecond less than permitsOnce`() {
        val rateLimiter = mock(RateLimiter::class.java, "5")
        val stream = createTestStream(rangeLength = 5000, permitsOnce = 200)
        val method = getPrivateMethod("tryAcquireOrWait")
        AbstractBandwidthRateLimiterService.Companion.rateLimiterProperties =
            RateLimiterProperties(bandwidthProperties = stream.rateCheckContext.bandwidthProperties)
        `when`(rateLimiter.getLimitPerSecond()).thenReturn(100)
        `when`(DownloadBandwidthRateLimiterService.Companion.rateLimiterCache[stream.rateCheckContext.limitKey])
            .thenReturn(rateLimiter)
        `when`(rateLimiter.tryAcquire(anyLong())).thenReturn(true)
        val bytes = 50
        method.call(stream, bytes)
        assertEquals(100, stream.getPrivateField("applyNum").toInt())
    }

    @Test
    fun `test tryAcquireOrWait when permitsOnce less than bytes`() {
        val rateLimiter = mock(RateLimiter::class.java, "6")
        val stream = createTestStream(rangeLength = 5000, permitsOnce = 50)
        val method = getPrivateMethod("tryAcquireOrWait")
        AbstractBandwidthRateLimiterService.Companion.rateLimiterProperties =
            RateLimiterProperties(bandwidthProperties = stream.rateCheckContext.bandwidthProperties)
        `when`(DownloadBandwidthRateLimiterService.Companion.rateLimiterCache[stream.rateCheckContext.limitKey])
            .thenReturn(rateLimiter)
        `when`(rateLimiter.getLimitPerSecond()).thenReturn(5000)
        `when`(rateLimiter.tryAcquire(anyLong())).thenReturn(true)
        val bytes = 100
        method.call(stream, bytes)
        assertEquals(bytes, stream.getPrivateField("applyNum").toInt())
    }

    @Test
    fun `test tryAcquireOrWait with remaining file size less than realPermitOnce`() {
        val rateLimiter = mock(RateLimiter::class.java, "7")
        val stream = createTestStream(rangeLength = 5000)
        stream.setPrivateField("bytesRead", 4950)
        val method = getPrivateMethod("tryAcquireOrWait")
        AbstractBandwidthRateLimiterService.Companion.rateLimiterProperties =
            RateLimiterProperties(bandwidthProperties = stream.rateCheckContext.bandwidthProperties)
        `when`(DownloadBandwidthRateLimiterService.Companion.rateLimiterCache[stream.rateCheckContext.limitKey])
            .thenReturn(rateLimiter)
        `when`(rateLimiter.getLimitPerSecond()).thenReturn(5000)
        `when`(rateLimiter.tryAcquire(anyLong())).thenReturn(true)
        val bytes = 100
        method.call(stream, bytes)
        assertEquals((5000 - 4950), stream.getPrivateField("applyNum").toInt())
    }

    @Test
    fun `test tryAcquireOrWait with negative remaining file size`() {
        val rateLimiter = mock(RateLimiter::class.java, "8")

        val stream = createTestStream(rangeLength = 5000)
        stream.setPrivateField("bytesRead", 6000)

        val method = getPrivateMethod("tryAcquireOrWait")
        AbstractBandwidthRateLimiterService.Companion.rateLimiterProperties =
            RateLimiterProperties(bandwidthProperties = stream.rateCheckContext.bandwidthProperties)
        `when`(DownloadBandwidthRateLimiterService.Companion.rateLimiterCache[stream.rateCheckContext.limitKey])
            .thenReturn(rateLimiter)
        `when`(rateLimiter.getLimitPerSecond()).thenReturn(5000)
        `when`(rateLimiter.tryAcquire(anyLong())).thenReturn(true)
        val bytes = 100
        method.call(stream, bytes)
        assertEquals(1000, stream.getPrivateField("applyNum").toInt())

    }

    @Test
    fun `test tryAcquireOrWait when rate limiter throws exception`() {
        val rateLimiter = mock(RateLimiter::class.java, "9")

        val stream = createTestStream(rangeLength = 5000)

        val method = getPrivateMethod("tryAcquireOrWait")
        val bytes = 100
        AbstractBandwidthRateLimiterService.Companion.rateLimiterProperties =
            RateLimiterProperties(bandwidthProperties = stream.rateCheckContext.bandwidthProperties)
        `when`(DownloadBandwidthRateLimiterService.Companion.rateLimiterCache[stream.rateCheckContext.limitKey])
            .thenReturn(rateLimiter)
        `when`(rateLimiter.getLimitPerSecond()).thenReturn(5000)
        `when`(rateLimiter.tryAcquire(anyLong())).thenThrow(AcquireLockFailedException::class.java)
        method.call(stream, bytes)
        assertEquals(1000, stream.getPrivateField("applyNum").toInt())
    }

    @Test
    fun `test tryAcquireOrWait in dry run mode`() {
        val rateLimiter = mock(RateLimiter::class.java, "10")

        val stream = createTestStream(rangeLength = 5000, dryRun = true)

        val method = getPrivateMethod("tryAcquireOrWait")
        AbstractBandwidthRateLimiterService.Companion.rateLimiterProperties =
            RateLimiterProperties(bandwidthProperties = stream.rateCheckContext.bandwidthProperties, dryRun = true)
        `when`(DownloadBandwidthRateLimiterService.Companion.rateLimiterCache[stream.rateCheckContext.limitKey])
            .thenReturn(rateLimiter)
        `when`(rateLimiter.getLimitPerSecond()).thenReturn(5000)
        `when`(rateLimiter.tryAcquire(anyLong())).thenReturn(false)
        val bytes = 100
        method.call(stream, bytes)
        assertEquals(1000, stream.getPrivateField("applyNum").toInt())
    }


    @Test
    fun `should return immediately when acquire success at first time`() {
        val rateLimiter = mock(RateLimiter::class.java, "11")
        val stream = createTestStream()
        stream.setPrivateField("rateLimiter", rateLimiter)

        `when`(rateLimiter.tryAcquire(anyLong())).thenReturn(true)
        val method = getPrivateMethod("acquireOrWait")
        val result = method.call(stream, 2048, 2048, false) as Long
        assertEquals(2048, result)
    }

    @Test
    fun `should return permits when acquire lock failed`() {
        val rateLimiter = mock(RateLimiter::class.java, "12")

        val stream = createTestStream()
        stream.setPrivateField("rateLimiter", rateLimiter)

        `when`(rateLimiter.tryAcquire(anyLong())).thenThrow(AcquireLockFailedException::class.java)
        val method = getPrivateMethod("acquireOrWait")
        val result = method.call(stream, 2048, 2048, false) as Long
        assertEquals(2048, result)
    }

    @Test
    fun `should return permits when dryRun is true`() {
        val rateLimiter = mock(RateLimiter::class.java, "13")

        val stream = createTestStream(dryRun = true)
        stream.setPrivateField("rateLimiter", rateLimiter)
        `when`(rateLimiter.tryAcquire(anyLong())).thenReturn(false)
        val method = getPrivateMethod("acquireOrWait")
        val result = method.call(stream, 2048, 2048, false) as Long
        assertEquals(2048, result)
    }

    @Test
    fun `should return min permits when timeout reached`() {
        val rateLimiter = mock(RateLimiter::class.java, "14")

        val stream = createTestStream(timeout = 0)
        // 通过反射设置私有属性
        stream.setPrivateField("rateLimiter", rateLimiter)
        `when`(rateLimiter.tryAcquire(anyLong())).thenReturn(false)
        val method = getPrivateMethod("acquireOrWait")
        var result = method.call(stream, 2048, 2048, false) as Long
        assertEquals(2048, result) // 返回 minPermits
        result = method.call(stream, 1024, 2048, false) as Long
        assertEquals(1024, result) // 返回 minPermits
        result = method.call(stream, 2048, 4096, false) as Long
        assertEquals(2048, result) // 返回 minPermits
    }

    @Test
    fun `should use strategy A when failed and progress over threshold`() {
        val rateLimiter = mock(RateLimiter::class.java, "15")

        val stream = createTestStream(rangeLength = 10240)
        stream.setPrivateField("bytesRead", 9000)
        stream.setPrivateField("rateLimiter", rateLimiter)

        `when`(rateLimiter.tryAcquire(anyLong())).thenReturn(false, false, true, true)
        val method = getPrivateMethod("acquireOrWait")
        val result = method.call(stream, 2048, 2048, false) as Long
        assertEquals(2048, result)
    }

    @Test
    fun `should use strategy B when failed and progress under threshold`() {
        val rateLimiter = mock(RateLimiter::class.java, "16")

        val stream = createTestStream(rangeLength = 10240)
        stream.setPrivateField("bytesRead", 1000)
        stream.setPrivateField("rateLimiter", rateLimiter)

        `when`(rateLimiter.tryAcquire(anyLong()))
            .thenReturn(false) // 第一次失败
            .thenReturn(false) // 第二次失败
            .thenReturn(true)  // 第三次成功
        val method = getPrivateMethod("acquireOrWait")
        var result = method.call(stream, 2048, 2048, false) as Long
        assertEquals(2048, result) // 策略B从minPermits开始

        `when`(rateLimiter.tryAcquire(anyLong())).thenReturn(false, false, true, false, true)
        result = method.call(stream, 2048, 2048, false) as Long
        assertEquals(2048, result)
    }

    @Test
    fun `should handle composite request correctly`() {
        val rateLimiter = mock(RateLimiter::class.java, "17")

        val stream = createTestStream()
        stream.setPrivateField("rateLimiter", rateLimiter)
        `when`(rateLimiter.tryAcquire(anyLong())).thenReturn(true)
        val method = getPrivateMethod("acquireOrWait")
        // 组合请求需要2048但每次只能获取1024
        var result = method.call(stream, 1000, 2048, true) as Long
        assertEquals(2048, result)

        `when`(rateLimiter.tryAcquire(anyLong())).thenReturn(false, false, true, false, true)
        result = method.call(stream, 1000, 2048, true) as Long
        assertEquals(2048, result)
    }

    @Test
    fun `should return coerce value when composite request and timeout`() {
        val rateLimiter = mock(RateLimiter::class.java, "18")

        val stream = createTestStream(timeout = 0)
        stream.setPrivateField("rateLimiter", rateLimiter)
        `when`(rateLimiter.tryAcquire(anyLong())).thenReturn(false)
        val method = getPrivateMethod("acquireOrWait")
        val result = method.call(stream, 4096, 2048, true) as Long
        assertEquals(2048, result) // 返回bytes和permits的最小值
    }

    @Test
    fun `should use min permits when strategy A reduced below min`() {
        val rateLimiter = mock(RateLimiter::class.java, "19")
        val stream = createTestStream(minPermits = 1000, rangeLength = 10240)
        stream.setPrivateField("bytesRead", 9000)
        stream.setPrivateField("rateLimiter", rateLimiter)
        `when`(rateLimiter.tryAcquire(anyLong())).thenReturn(false, false, true)
        // 初始2048，第一次减半1024，第二次不能再减半(因为会低于minPermits)
        val method = getPrivateMethod("acquireOrWait")
        val result = method.call(stream, 2048, 2048, false) as Long
        assertEquals(2048, result)
    }

    @Test
    fun `should double permits when using strategy B`() {
        val rateLimiter = mock(RateLimiter::class.java, "20")
        val stream = createTestStream(minPermits = 1024, rangeLength = 10240)
        stream.setPrivateField("bytesRead", 1000)
        stream.setPrivateField("rateLimiter", rateLimiter)
        `when`(rateLimiter.tryAcquire(anyLong()))
            .thenReturn(false)
            .thenReturn(false)
            .thenReturn(true)
        val method = getPrivateMethod("acquireOrWait")
        val result = method.call(stream, 4096, 4096, false) as Long
        assertEquals(4096, result)
    }


    @Test
    fun `test type A when tryAcquirePermits is less than minPermits x 2`() {
        val rateLimiter = mock(RateLimiter::class.java, "21")

        val stream = createTestStream()
        val realAcquirePermits = 1000L
        val tryAcquirePermits = 150L // 小于 minPermits*2 (200)
        val method = getPrivateMethod("calculatePermitsOnce")
        val result = method.call(
            stream, realAcquirePermits, tryAcquirePermits, CommonRateLimitInputStream.TYPE_A
        ) as Long
        assertEquals(100L, result) // 应该返回 minPermits
    }

    @Test
    fun `test type A when tryAcquirePermits is greater than minPermits x 2`() {
        val rateLimiter = mock(RateLimiter::class.java, "22")

        val stream = createTestStream()
        val realAcquirePermits = 1000L
        val tryAcquirePermits = 300L // 大于 minPermits*2 (200)
        val method = getPrivateMethod("calculatePermitsOnce")
        val result = method.call(
            stream, realAcquirePermits, tryAcquirePermits, CommonRateLimitInputStream.TYPE_A
        ) as Long
        assertEquals(150L, result) // 应该返回 tryAcquirePermits/2
    }

    @Test
    fun `test type B when tryAcquirePermits is less than minPermits`() {
        val rateLimiter = mock(RateLimiter::class.java, "23")

        val stream = createTestStream()
        val realAcquirePermits = 1000L
        val tryAcquirePermits = 50L // 小于 minPermits (100)
        val method = getPrivateMethod("calculatePermitsOnce")
        val result = method.call(
            stream, realAcquirePermits, tryAcquirePermits, CommonRateLimitInputStream.TYPE_B
        ) as Long
        assertEquals(100L, result) // 应该返回 minPermits
    }

    @Test
    fun `test type B when tryAcquirePermits is greater than minPermits`() {
        val rateLimiter = mock(RateLimiter::class.java, "24")

        val stream = createTestStream()
        val realAcquirePermits = 1000L
        val tryAcquirePermits = 200L // 大于 minPermits (100)
        val method = getPrivateMethod("calculatePermitsOnce")
        val result = method.call(
            stream, realAcquirePermits, tryAcquirePermits, CommonRateLimitInputStream.TYPE_B
        ) as Long
        assertEquals(400L, result) // 应该返回 tryAcquirePermits*2
    }

    @Test
    fun `test unknown type should return minPermits`() {
        val rateLimiter = mock(RateLimiter::class.java, "25")

        val stream = createTestStream()
        val realAcquirePermits = 1000L
        val tryAcquirePermits = 500L
        val method = getPrivateMethod("calculatePermitsOnce")
        val result = method.call(
            stream, realAcquirePermits, tryAcquirePermits, "TYPE_C"
        ) as Long
        assertEquals(100L, result) // 应该返回 minPermits
    }

    @Test
    fun `test result should not exceed realAcquirePermits for type A`() {
        val rateLimiter = mock(RateLimiter::class.java, "26")

        val stream = createTestStream()
        val realAcquirePermits = 200L
        val tryAcquirePermits = 500L // 500/2=250 但 realAcquirePermits=200
        val method = getPrivateMethod("calculatePermitsOnce")
        val result = method.call(
            stream, realAcquirePermits, tryAcquirePermits, CommonRateLimitInputStream.TYPE_A
        ) as Long
        assertEquals(200L, result) // 应该不超过 realAcquirePermits
    }

    @Test
    fun `test result should not exceed realAcquirePermits for type B`() {
        val rateLimiter = mock(RateLimiter::class.java, "27")

        val stream = createTestStream()
        val realAcquirePermits = 300L
        val tryAcquirePermits = 200L // 200*2=400 但 realAcquirePermits=300
        val method = getPrivateMethod("calculatePermitsOnce")
        val result = method.call(
            stream, realAcquirePermits, tryAcquirePermits, CommonRateLimitInputStream.TYPE_B
        ) as Long
        assertEquals(300L, result) // 应该不超过 realAcquirePermits
    }

    @Test
    fun `test edge case when tryAcquirePermits equals minPermits x 2 for type A`() {
        val rateLimiter = mock(RateLimiter::class.java, "28")

        val stream = createTestStream()
        val realAcquirePermits = 1000L
        val tryAcquirePermits = 200L // 等于 minPermits*2
        val method = getPrivateMethod("calculatePermitsOnce")
        val result = method.call(
            stream, realAcquirePermits, tryAcquirePermits, CommonRateLimitInputStream.TYPE_A
        ) as Long
        assertEquals(100L, result) // 应该返回 minPermits
    }

    @Test
    fun `test edge case when tryAcquirePermits equals minPermits for type B`() {
        val rateLimiter = mock(RateLimiter::class.java, "29")

        val stream = createTestStream()
        val realAcquirePermits = 1000L
        val tryAcquirePermits = 100L // 等于 minPermits
        val method = getPrivateMethod("calculatePermitsOnce")
        val result = method.call(
            stream, realAcquirePermits, tryAcquirePermits, CommonRateLimitInputStream.TYPE_B
        ) as Long
        assertEquals(100L, result) // 应该返回 minPermits
    }

    @Test
    fun `test zero realAcquirePermits should return zero`() {
        val rateLimiter = mock(RateLimiter::class.java, "30")

        val stream = createTestStream()
        val realAcquirePermits = 0L
        val tryAcquirePermits = 500L
        val method = getPrivateMethod("calculatePermitsOnce")
        val result = method.call(
            stream, realAcquirePermits, tryAcquirePermits, CommonRateLimitInputStream.TYPE_A
        ) as Long
        assertEquals(0L, result) // 应该返回 0
    }

    @Test
    fun `selectRateLimitStrategy should return TYPE_A when rangeLength is null`() {
        val rateLimiter = mock(RateLimiter::class.java, "31")

        val stream = createTestStream()
        val method = getPrivateMethod("selectRateLimitStrategy")
        val result = method.call(stream) as String
        assertEquals(CommonRateLimitInputStream.TYPE_A, result)
    }

    @Test
    fun `selectRateLimitStrategy should return TYPE_A when rangeLength is less than or equal to 0`() {
        val rateLimiter = mock(RateLimiter::class.java, "32")

        val stream = createTestStream(rangeLength = 0)
        val method = getPrivateMethod("selectRateLimitStrategy")
        val result = method.call(stream) as String
        assertEquals(CommonRateLimitInputStream.TYPE_A, result)
    }

    @Test
    fun `selectRateLimitStrategy should return TYPE_A when file is small`() {
        val rateLimiter = mock(RateLimiter::class.java, "33")
        val stream = createTestStream(rangeLength = 1024)
        val method = getPrivateMethod("selectRateLimitStrategy")
        val result = method.call(stream) as String
        assertEquals(CommonRateLimitInputStream.TYPE_A, result)
    }

    @Test
    fun `selectRateLimitStrategy should return TYPE_B for large file with progress below threshold`() {
        val rateLimiter = mock(RateLimiter::class.java, "34")
        val stream = createTestStream(rangeLength = 2048)
        stream.setPrivateField("bytesRead", 1000)
        val method = getPrivateMethod("selectRateLimitStrategy")
        val result = method.call(stream) as String
        assertEquals(CommonRateLimitInputStream.TYPE_B, result)
    }

    @Test
    fun `selectRateLimitStrategy should return TYPE_A for large file with progress above threshold`() {
        val rateLimiter = mock(RateLimiter::class.java, "35")

        val stream = createTestStream(rangeLength = 2048)
        stream.setPrivateField("bytesRead", 1025)
        val method = getPrivateMethod("selectRateLimitStrategy")
        val result = method.call(stream) as String
        assertEquals(CommonRateLimitInputStream.TYPE_A, result)
    }

    @Test
    fun `selectRateLimitStrategy should return TYPE_A when progress exactly at threshold`() {
        val rateLimiter = mock(RateLimiter::class.java, "36")

        val stream = createTestStream(rangeLength = 2048)
        stream.setPrivateField("bytesRead", 1024)
        val method = getPrivateMethod("selectRateLimitStrategy")
        val result = method.call(stream) as String
        assertEquals(CommonRateLimitInputStream.TYPE_A, result)
    }



    private fun getPrivateMethod(methodName: String): kotlin.reflect.KFunction<*> {
        return CommonRateLimitInputStream::class.declaredMemberFunctions
            .first { it.name == methodName }
            .apply { isAccessible = true }
    }

    private fun CommonRateLimitInputStream.getPrivateField(field: String): Long {
        return this.javaClass.getDeclaredField(field).let {
            it.isAccessible = true
            it.get(this) as Long
        }
    }

    private fun CommonRateLimitInputStream.setPrivateField(field: String, value: Any) {
        return this.javaClass.getDeclaredField(field).let {
            it.isAccessible = true
            it.set(this, value)
        }
    }

    // 辅助函数用于anyLong()匹配
    private fun anyLong(): Long {
        return org.mockito.ArgumentMatchers.anyLong()
    }

    private fun createTestStream(
        rangeLength: Long? = null,
        minPermits: Long = 100,
        timeout: Long = 1000,
        dryRun: Boolean = false,
        permitsOnce: Long = 1000,
    ): CommonRateLimitInputStream {
        return CommonRateLimitInputStream(
            FakeInputStream(), createContext(
            rangeLength = rangeLength,
            minPermits = minPermits,
            timeout = timeout,
            dryRun = dryRun,
            permitsOnce = permitsOnce
        )
        )
    }

    private fun createContext(
        rangeLength: Long? = null,
        minPermits: Long = 100,
        timeout: Long = 1000,
        dryRun: Boolean = false,
        resourceLimit: ResourceLimit = ResourceLimit(limitDimension = LimitDimension.DOWNLOAD_BANDWIDTH.name),
        permitsOnce: Long = 1000,
    ): RateCheckContext {
        return RateCheckContext(
            bandwidthProperties = BandwidthProperties(
                permitsOnce = permitsOnce,
                minPermits = minPermits,
                waitRound = 3,
                latency = 100L,
                timeout = timeout,
                progressThreshold = 0.5,
                smallFileThreshold = 1024
            ),
            dryRun = dryRun,
            rangeLength = rangeLength,
            resourceLimit = resourceLimit,
            limitKey = "test",
            resInfo = ResInfo("test", listOf("test")),
        )
    }


}

// 用于测试的假InputStream

class FakeInputStream : InputStream() {
    override fun read(): Int = 0
}