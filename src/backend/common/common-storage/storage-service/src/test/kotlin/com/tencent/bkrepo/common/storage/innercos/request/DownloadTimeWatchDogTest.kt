/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.storage.innercos.request

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class DownloadTimeWatchDogTest {
    @DisplayName("测试创建多个同名WatchDog")
    @Test
    fun testMulti() {
        val name = "123"
        val pool = ThreadPoolExecutor(
            10, 10, 0, TimeUnit.SECONDS,
            ArrayBlockingQueue(10)
        )
        val d1 = DownloadTimeWatchDog(name, pool, 0, 0)
        val d2 = DownloadTimeWatchDog(name, pool, 0, 0)
        val d3 = DownloadTimeWatchDog(name, pool, 0, 0)
        Assertions.assertFalse(DownloadTimeWatchDog.taskFutureMap.contains(d1.taskFuture))
        Assertions.assertFalse(DownloadTimeWatchDog.taskFutureMap.contains(d2.taskFuture))
        Assertions.assertTrue(DownloadTimeWatchDog.taskFutureMap.contains(d3.taskFuture))
    }

    @DisplayName("测试并发创建")
    @Test
    fun testConcurrent() {
        val name = "123"
        val pool = ThreadPoolExecutor(
            10, 10, 0, TimeUnit.SECONDS,
            ArrayBlockingQueue(10)
        )
        repeat(10) { pool.execute { DownloadTimeWatchDog(name, pool, 0, 0) } }
        Thread.sleep(200)
        Assertions.assertTrue(DownloadTimeWatchDog.taskFutureMap.size == 1)
    }
}
