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

package com.tencent.bkrepo.common.service.shutdown

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.util.TraceUtils.trace
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.util.concurrent.Callable
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class ServiceShutdownHook(
    val shutdownProperties: ShutdownProperties
) : SmartLifecycle {

    override fun start() {
        return
    }

    override fun stop() {
        logger.info("start to ensure task finish, total task count: ${hookList.size}")
        val latestTimestampToWait =
            System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(shutdownProperties.maxWaitMinute)
        val futureList = hookList.map { executor.submit(it.trace()) }
        futureList.forEach {
            try {
                it.get(getTimeout(latestTimestampToWait), TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                logger.warn("wait task finish timeout")
            } catch (e: Exception) {
                logger.error("task execute failed", e)
            }
        }
    }

    override fun isRunning(): Boolean {
        return true
    }

    private fun getTimeout(latestTimestampToWait: Long): Long {
        return if (System.currentTimeMillis() > latestTimestampToWait) {
            0
        } else {
            latestTimestampToWait - System.currentTimeMillis()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ServiceShutdownHook::class.java)
        private val executor = ThreadPoolExecutor(
            1, Int.MAX_VALUE,
            0, TimeUnit.SECONDS, SynchronousQueue(),
            ThreadFactoryBuilder().setNameFormat("shutdown-hook-%d").build()
        )
        private val hookList = mutableListOf<Callable<Any>>()

        fun add(callable: Callable<Any>) {
            hookList.add(callable)
        }
    }
}
