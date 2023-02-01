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

import com.tencent.bkrepo.analyst.dao.ScanTaskDao
import com.tencent.bkrepo.analyst.metrics.ScannerMetrics
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus
import com.tencent.bkrepo.analyst.statemachine.Action
import com.tencent.bkrepo.analyst.statemachine.TaskStateMachineConfiguration.Companion.STATE_MACHINE_ID_SCAN_TASK
import com.tencent.bkrepo.analyst.statemachine.TaskStateMachineConfiguration.Companion.STATE_MACHINE_ID_SUB_SCAN_TASK
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent
import com.tencent.bkrepo.analyst.statemachine.subtask.context.NotifySubtaskContext
import com.tencent.bkrepo.analyst.statemachine.task.ScanTaskEvent
import com.tencent.bkrepo.analyst.statemachine.task.context.FinishTaskContext
import com.tencent.bkrepo.analyst.statemachine.task.context.SubmitTaskContext
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.statemachine.Event
import com.tencent.bkrepo.statemachine.StateMachine
import com.tencent.bkrepo.statemachine.TransitResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Lazy
import java.time.LocalDateTime

@Action
class SubmittedAction(
    private val scanTaskDao: ScanTaskDao,
    private val scannerMetrics: ScannerMetrics,
) : TaskAction {

    @Autowired
    @Lazy
    @Qualifier(STATE_MACHINE_ID_SCAN_TASK)
    private lateinit var taskStateMachine: StateMachine

    @Autowired
    @Lazy
    @Qualifier(STATE_MACHINE_ID_SUB_SCAN_TASK)
    private lateinit var subtaskStateMachine: StateMachine

    override fun execute(source: String, target: String, event: Event): TransitResult {
        val context = event.context
        require(context is SubmitTaskContext)
        val scanTask = context.scanTask
        scanTaskDao.updateStatus(scanTask.taskId, ScanTaskStatus.SCANNING_SUBMITTED)
        scannerMetrics.incTaskCountAndGet(ScanTaskStatus.SCANNING_SUBMITTED)
        logger.info("update task[${scanTask.taskId}] status to SCANNING_SUBMITTED")

        if (context.submittedSubTaskCount == 0L) {
            // 没有提交任何子任务，直接设置为任务扫描结束
            val now = LocalDateTime.now()
            val finishTaskCtx = FinishTaskContext(context.scanTask.taskId, context.scanTask.scanPlan?.id, now, now)
            val finishEvent = Event(ScanTaskEvent.FINISH.name, finishTaskCtx)
            return taskStateMachine.sendEvent(ScanTaskStatus.SCANNING_SUBMITTED.name, finishEvent)
        } else {
            // 任务提交结束后尝试唤醒一个任务，避免全部任务都处于BLOCK状态
            scanTask.scanPlan?.projectId?.let {
                val notifySubtaskContext = NotifySubtaskContext(it)
                val notifyEvent = Event(SubtaskEvent.NOTIFY.name, notifySubtaskContext)
                subtaskStateMachine.sendEvent(SubScanTaskStatus.BLOCKED.name, notifyEvent)
            }
        }
        return TransitResult(target)
    }

    override fun support(from: String, to: String, event: String): Boolean {
        return from == ScanTaskStatus.SCANNING_SUBMITTING.name
            && to == ScanTaskStatus.SCANNING_SUBMITTED.name
            && event == ScanTaskEvent.FINISH_SUBMIT.name
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SubmittedAction::class.java)
    }
}
