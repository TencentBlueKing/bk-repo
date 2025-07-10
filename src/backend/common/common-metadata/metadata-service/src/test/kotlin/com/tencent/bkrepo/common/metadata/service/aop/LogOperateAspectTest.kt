/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.service.aop

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.tencent.bkrepo.common.metadata.aop.LogOperateAspect
import com.tencent.bkrepo.common.metadata.service.log.OperateLogService
import com.tencent.bkrepo.common.metadata.pojo.log.OperateLog
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LogOperateAspectTest {

    @Test
    fun testConcurrentSaveLog() {
        val count = 10
        for (i in 1..count) {
            doTestConcurrentSaveLog()
            println("testConcurrentSaveLog success $i/$count")
        }
    }

    private fun doTestConcurrentSaveLog() {
        (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger).level = Level.INFO
        // 准备测试对象
        val savedOperateLogs = ArrayList<OperateLog>()
        val operateLogService = mock<OperateLogService> {
            on { saveAsync(any<Collection<OperateLog>>()) }.thenAnswer {
                val operateLogs = it.getArgument<Collection<OperateLog>>(0)
                savedOperateLogs.addAll(operateLogs)
            }
        }
        val logOperateAspect = LogOperateAspect(operateLogService)
        val executors = Executors.newWorkStealingPool()
        val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()

        // 模拟多线程保存操作日志
        val countDownLatch = CountDownLatch(OPERATE_LOG_COUNT)
        for (i in 0 until OPERATE_LOG_COUNT) {
            executors.execute {
                logOperateAspect.saveEmptyLog(i.toString())
                countDownLatch.countDown()
            }
        }
        // 模拟定时任务将日志刷入数据库
        scheduledExecutor.schedule({ logOperateAspect.forceFlush() }, 500, TimeUnit.MILLISECONDS)

        // 全部写入完毕后执行一次强制写入后再对结果进行断言
        countDownLatch.await()
        logOperateAspect.forceFlush()

        Assertions.assertEquals(OPERATE_LOG_COUNT, savedOperateLogs.size)
    }

    private fun LogOperateAspect.saveEmptyLog(userId: String) {
        saveOperateLog("", userId, "", emptyMap())
    }

    companion object {
        private const val OPERATE_LOG_COUNT = 100_000
    }
}
