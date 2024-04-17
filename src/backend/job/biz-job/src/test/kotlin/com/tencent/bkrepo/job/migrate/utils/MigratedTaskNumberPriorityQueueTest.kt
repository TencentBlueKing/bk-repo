/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.migrate.utils

import com.tencent.bkrepo.common.api.collection.concurrent.ConcurrentHashSet
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.random.Random

class MigratedTaskNumberPriorityQueueTest {
    @Test
    fun test() {
        val q = MigratedTaskNumberPriorityQueue()
        val executor = Executors.newFixedThreadPool(8)
        val migratedNumbers = ConcurrentHashSet<Long>()
        val futures = ArrayList<Future<*>>()
        for (i in 1 .. 100L) {
            val future = executor.submit {
                try {
                    // 模拟数据迁移
                    Thread.sleep(Random.nextLong(1, 5) * 500)
                } finally {
                    migratedNumbers.add(i)
                    q.offer(i)
                    if (i % 4 == 0L) {
                        val leftMax = q.updateLeftMax()
//                        println("i[$i], leftMax[$leftMax], queueSize[${q.size()}] \n migrated: $migratedNumbers")
                        // 确认leftMax之前所有的任务号都已迁移完
                        for (migrated in 1 .. leftMax) {
                            assertTrue(migrated in migratedNumbers)
                        }
                    }
                }
            }
            futures.add(future)
        }
        futures.forEach { it.get() }
    }
}
