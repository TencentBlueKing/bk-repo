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

package com.tencent.bkrepo.analyst.statemachine.task.action

import com.tencent.bkrepo.analyst.dao.ScanPlanDao
import com.tencent.bkrepo.analyst.dao.ScanTaskDao
import com.tencent.bkrepo.analyst.event.ScanTaskStatusChangedEvent
import com.tencent.bkrepo.analyst.metrics.ScannerMetrics
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus
import com.tencent.bkrepo.analyst.statemachine.Action
import com.tencent.bkrepo.analyst.statemachine.task.context.FinishTaskContext
import com.tencent.bkrepo.analyst.utils.Converter
import com.tencent.bkrepo.statemachine.Event
import com.tencent.bkrepo.statemachine.TransitResult
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher

@Action
class FinishedAction(
    private val scanTaskDao: ScanTaskDao,
    private val scanPlanDao: ScanPlanDao,
    private val publisher: ApplicationEventPublisher,
    private val scannerMetrics: ScannerMetrics

) : TaskAction {
    override fun execute(source: String, target: String, event: Event): TransitResult {
        val context = event.context
        require(context is FinishTaskContext)
        with(context) {
            if (scanTaskDao.taskFinished(taskId, finishedDateTime, startDateTime).modifiedCount == 1L) {
                scannerMetrics.incTaskCountAndGet(ScanTaskStatus.FINISHED)
                val scanPlan = planId?.let { scanPlanDao.findById(it) }
                val scanTask = scanTaskDao.findById(taskId)!!
                val finishedScanTask = Converter.convert(scanTask, scanPlan)
                publisher.publishEvent(ScanTaskStatusChangedEvent(ScanTaskStatus.SCANNING_SUBMITTED, finishedScanTask))
                logger.info("scan finished, task[${taskId}]")
                return TransitResult(target)
            }
        }
        return TransitResult(source)
    }

    override fun support(from: String, to: String, event: String): Boolean {
        return to == ScanTaskStatus.FINISHED.name
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FinishedAction::class.java)
    }
}
