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

package com.tencent.bkrepo.job.separation.executor

import com.tencent.bkrepo.common.service.otel.util.AsyncUtils.trace
import com.tencent.bkrepo.job.separation.dao.SeparationTaskDao
import com.tencent.bkrepo.job.separation.pojo.record.SeparationContext
import com.tencent.bkrepo.job.separation.pojo.task.SeparationCount
import com.tencent.bkrepo.job.separation.pojo.task.SeparationTaskState
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.ThreadPoolExecutor

abstract class AbstractSeparationTaskExecutor(
    private val separationTaskDao: SeparationTaskDao
) : SeparationTaskExecutor {

    open fun filterTask(context: SeparationContext): Boolean = false

    open fun concurrencyCheck(): Boolean = false

    override fun execute(context: SeparationContext) {
        threadPoolExecutor()?.execute(Runnable { runTask(context) }.trace())
    }

    open fun threadPoolExecutor(): ThreadPoolExecutor? = null

    open fun doAction(context: SeparationContext) {}

    private fun runTask(context: SeparationContext) {
        with(context) {
            if (filterTask(context)) return
            if (concurrencyCheck()) return
            try {
                logger.info("start to run task $task")
                beforeExecute(context)
                doAction(context)
            } catch (exception: Exception) {
                logger.error("$task separate exception: $exception")
            } finally {
                afterExecute(context)
                logger.info("task $task has been finished!")
            }
        }
    }

    open fun beforeExecute(context: SeparationContext) {
        separationTaskDao.updateState(
            context.taskId, SeparationTaskState.RUNNING, startDate = LocalDateTime.now()
        )
    }

    open fun afterExecute(context: SeparationContext) {
        with(context) {
            val count = SeparationCount(separationProgress.success, separationProgress.failed)
            separationTaskDao.updateState(
                taskId,
                SeparationTaskState.FINISHED,
                count,
                endDate = LocalDateTime.now()
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractSeparationTaskExecutor::class.java)
    }
}