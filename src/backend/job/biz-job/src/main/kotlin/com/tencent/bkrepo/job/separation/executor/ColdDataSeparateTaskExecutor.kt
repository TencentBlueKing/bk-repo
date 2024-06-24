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

import com.tencent.bkrepo.job.separation.config.DataSeparationConfig
import com.tencent.bkrepo.job.separation.constant.SEPARATE
import com.tencent.bkrepo.job.separation.dao.SeparationTaskDao
import com.tencent.bkrepo.job.separation.pojo.record.SeparationContext
import com.tencent.bkrepo.job.separation.service.DataSeparator
import com.tencent.bkrepo.job.separation.util.SeparationUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadPoolExecutor

@Component
class ColdDataSeparateTaskExecutor(
    separationTaskDao: SeparationTaskDao,
    private val dataSeparationConfig: DataSeparationConfig,
    private val dataSeparator: DataSeparator
) : AbstractSeparationTaskExecutor(separationTaskDao) {

    private val separateExecutor: ThreadPoolExecutor by lazy {
        SeparationUtils.buildThreadPoolExecutor(SEPARATE_TASK_NAME_PRIFIX, dataSeparationConfig.separateTaskConcurrency)
    }

    override fun filterTask(context: SeparationContext): Boolean {
        return context.type != SEPARATE
    }

    override fun concurrencyCheck(): Boolean {
        val queueSize = separateExecutor.queue.size
        if (queueSize > dataSeparationConfig.separateTaskConcurrency) {
            logger.warn("$queueSize task of separate is running or waiting")
            return true
        }
        return false
    }

    override fun threadPoolExecutor(): ThreadPoolExecutor? = separateExecutor

    override fun doAction(context: SeparationContext) {
        with(context) {
            if (task.content.packages.isNullOrEmpty() && task.content.paths.isNullOrEmpty()) {
                dataSeparator.repoSeparator(context)
                return
            }
            if (!task.content.packages.isNullOrEmpty()) {
                task.content.packages!!.forEach {
                    dataSeparator.packageSeparator(context, it)
                }
                return
            }
            if (!task.content.paths.isNullOrEmpty()) {
                task.content.paths!!.forEach {
                    dataSeparator.nodeSeparator(context, it)
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ColdDataSeparateTaskExecutor::class.java)
        private const val SEPARATE_TASK_NAME_PRIFIX = "separate-task"
    }
}