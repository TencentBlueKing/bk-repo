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

import com.tencent.bkrepo.job.RESTORE
import com.tencent.bkrepo.job.separation.config.DataSeparationConfig
import com.tencent.bkrepo.job.separation.dao.SeparationTaskDao
import com.tencent.bkrepo.job.separation.pojo.record.SeparationContext
import com.tencent.bkrepo.job.separation.service.DataRestorer
import com.tencent.bkrepo.job.separation.util.SeparationUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadPoolExecutor

@Component
class ColdDataRestoreTaskExecutor(
    separationTaskDao: SeparationTaskDao,
    private val dataSeparationConfig: DataSeparationConfig,
    private val dataRestorer: DataRestorer
) : AbstractSeparationTaskExecutor(separationTaskDao) {


    private val restoreExecutor: ThreadPoolExecutor by lazy {
        SeparationUtils.buildThreadPoolExecutor(RESTORE_TASK_NAME_PREFIX, dataSeparationConfig.restoreTaskConcurrency)
    }

    override fun filterTask(context: SeparationContext): Boolean {
        return context.type != RESTORE
    }

    override fun concurrencyCheck(): Boolean {
        val queueSize = restoreExecutor.queue.size
        if (queueSize > dataSeparationConfig.restoreTaskConcurrency) {
            logger.warn("$queueSize tasks are restoring")
            return true
        }
        return false
    }

    override fun threadPoolExecutor(): ThreadPoolExecutor? = restoreExecutor
    override fun doAction(context: SeparationContext) {
        with(context) {
            if (task.content.packages.isNullOrEmpty() && task.content.paths.isNullOrEmpty()) {
                dataRestorer.repoRestorer(context)
                return
            }
            if (!task.content.packages.isNullOrEmpty()) {
                task.content.packages!!.forEach {
                    dataRestorer.packageRestorer(context, it)
                }
                return
            }
            if (!task.content.paths.isNullOrEmpty()) {
                task.content.paths!!.forEach {
                    dataRestorer.nodeRestorer(context, it)
                }
            }
        }
    }


    companion object {
        private val logger = LoggerFactory.getLogger(ColdDataRestoreTaskExecutor::class.java)
        private const val RESTORE_TASK_NAME_PREFIX = "restore-task"
    }
}