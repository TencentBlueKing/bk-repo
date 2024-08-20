/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedFixedWindowRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedTest
import com.tencent.bkrepo.common.ratelimiter.algorithm.FixedWindowRateLimiter
import com.tencent.bkrepo.common.ratelimiter.exception.OverloadException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito
import org.springframework.test.annotation.DirtiesContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommonRateLimitInputStreamTest : DistributedTest() {

    lateinit var ticker: Ticker
    private val content = "1234567891"
    val keyStr = "CommonRateLimitInputStreamTest"

    @BeforeAll
    fun before() {
        ticker = Mockito.mock(Ticker::class.java)
    }

    @Test
    fun readTestOncePermitsGreaterThanLength() {
        val (context, _) = createContext(1024 * 1024)
        inputStreamReadTest(context)
    }

    @Test
    fun readTestOncePermitsLessThanLength() {
        val (context, _) = createContext(5)
        inputStreamReadTest(context)
    }

    @Test
    fun readTestOncePermitsEqualLength() {
        val (context, _) = createContext(10)
        inputStreamReadTest(context)
    }

    @Test
    fun readTestOncePermitsGreaterThanLimit() {
        val (context, _) = createContext(10, 5)
        CommonRateLimitInputStream(
            delegate = content.byteInputStream(),
            rateCheckContext = context
        ).use { `is` ->
            val buf = ByteArray(3)
            `is`.read(buf)
            Assertions.assertThrows(OverloadException::class.java) { `is`.read(buf) }
        }
    }



    @Test
    fun readTestOnMultiThreads() {
        val (context, key) = createContext(10, 100, true, keyStr)
        var successNum = 0
        var failedNum = 0
        var errorNum = 0
        val readers = Runtime.getRuntime().availableProcessors()
        val countDownLatch = CountDownLatch(readers)
        val elapsedTime = measureTimeMillis {
            repeat(readers) {
                thread {
                    try {
                        inputStreamReadTest(context)
                        successNum++
                    } catch (e: Exception) {
                        errorNum++
                    }
                    countDownLatch.countDown()
                }
            }
        }
        countDownLatch.await()
        println("elapse: ${HumanReadable.time(elapsedTime, TimeUnit.MILLISECONDS)}")
        println("successNum $successNum, failedNum $failedNum. errorNum $errorNum")
        key?.let { clean(key) }
    }

    private fun inputStreamReadTest(context: RateCheckContext) {
        CommonRateLimitInputStream(
            delegate = content.byteInputStream(),
            rateCheckContext = context
        ).use { `is` ->
            val buf = ByteArray(3)
            assertEquals(`is`.read(buf, 0, 3), 3)
            assertEquals(String(buf), "123")
            assertEquals(`is`.read().toChar(), '4')
            assertEquals(`is`.read(buf), 3)
            assertEquals(String(buf), "567")
            assertEquals(`is`.read().toChar(), '8')
            assertEquals(`is`.read().toChar(), '9')
            assertEquals(`is`.read().toChar(), '1')
            assertEquals(`is`.read(), -1)
            println("finished")
        }

    }

    private fun createContext(
        permitsOnce: Long, limit: Long = 1024 * 1024 * 100,
        distributed: Boolean = false, keyStr: String? = null,
    ): Pair<RateCheckContext, String?> {
        val (rateLimiter, key) = if (distributed) {
            Pair(DistributedFixedWindowRateLimiter(keyStr!!, limit, TimeUnit.SECONDS, redisTemplate), keyStr)
        } else {
            Pair(FixedWindowRateLimiter(limit, TimeUnit.SECONDS, Stopwatch.createStarted(ticker)), keyStr)
        }
        return Pair(
            RateCheckContext(
                rateLimiter = rateLimiter, latency = 10,
                waitRound = 3, rangeLength = content.length.toLong(),
                dryRun = false, permitsOnce = permitsOnce, limitPerSecond = limit
            ), key
        )
    }
}