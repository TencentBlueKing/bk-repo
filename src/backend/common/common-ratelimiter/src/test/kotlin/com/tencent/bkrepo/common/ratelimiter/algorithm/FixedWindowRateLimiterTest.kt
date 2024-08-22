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

package com.tencent.bkrepo.common.ratelimiter.algorithm

import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import com.tencent.bkrepo.common.api.util.HumanReadable
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.system.measureTimeMillis

class FixedWindowRateLimiterTest {

    @Test
    fun testTryAcquire() {
        val ticker = Mockito.mock(Ticker::class.java)
        Mockito.`when`(ticker.read()).thenReturn(0 * 1000 * 1000L)
        val ratelimiter: RateLimiter = FixedWindowRateLimiter(5, TimeUnit.SECONDS, Stopwatch.createStarted(ticker))
        Mockito.`when`(ticker.read()).thenReturn(100 * 1000 * 1000L)
        val passed1 = ratelimiter.tryAcquire(1)
        assertTrue(passed1)
        Mockito.`when`(ticker.read()).thenReturn(200 * 1000 * 1000L)
        val passed2 = ratelimiter.tryAcquire(1)
        assertTrue(passed2)
        Mockito.`when`(ticker.read()).thenReturn(300 * 1000 * 1000L)
        val passed3 = ratelimiter.tryAcquire(1)
        assertTrue(passed3)
        Mockito.`when`(ticker.read()).thenReturn(400 * 1000 * 1000L)
        val passed4 = ratelimiter.tryAcquire(1)
        assertTrue(passed4)
        Mockito.`when`(ticker.read()).thenReturn(500 * 1000 * 1000L)
        val passed5 = ratelimiter.tryAcquire(1)
        assertTrue(passed5)
        Mockito.`when`(ticker.read()).thenReturn(600 * 1000 * 1000L)
        val passed6 = ratelimiter.tryAcquire(1)
        assertFalse(passed6)
        Mockito.`when`(ticker.read()).thenReturn(1001 * 1000 * 1000L)
        val passed7 = ratelimiter.tryAcquire(1)
        assertTrue(passed7)
    }

    @Test
    fun testTryAcquireOnMultiThreads() {
        val ticker = Mockito.mock(Ticker::class.java)
        Mockito.`when`(ticker.read()).thenReturn(0 * 1000 * 1000L)
        val ratelimiter: RateLimiter = FixedWindowRateLimiter(5, TimeUnit.SECONDS, Stopwatch.createStarted(ticker))
        var successNum = 0
        var failedNum = 0
        var errorNum = 0
        val readers = Runtime.getRuntime().availableProcessors()
        val countDownLatch = CountDownLatch(readers)
        val elapsedTime = measureTimeMillis {
            repeat(readers) {
                thread {
                    try {
                        Thread.sleep((Random.nextInt(5) * 2).toLong())
                        val passed = ratelimiter.tryAcquire(1)
                        if (passed) {
                            successNum++
                        } else {
                            failedNum++
                        }
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
    }
}