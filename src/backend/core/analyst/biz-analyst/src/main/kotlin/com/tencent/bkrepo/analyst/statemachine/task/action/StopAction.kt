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
import com.tencent.bkrepo.analyst.dao.SubScanTaskDao
import com.tencent.bkrepo.analyst.metrics.ScannerMetrics
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus
import com.tencent.bkrepo.analyst.statemachine.Action
import com.tencent.bkrepo.analyst.statemachine.TaskStateMachineConfiguration.Companion.STATE_MACHINE_ID_SUB_SCAN_TASK
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent
import com.tencent.bkrepo.analyst.statemachine.subtask.context.FinishSubtaskContext
import com.tencent.bkrepo.analyst.statemachine.task.context.StopTaskContext
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.statemachine.Event
import com.tencent.bkrepo.statemachine.StateMachine
import com.tencent.bkrepo.statemachine.TransitResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Lazy
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Action
class StopAction(
    private val scanTaskDao: ScanTaskDao,
    private val scannerMetrics: ScannerMetrics,
    private val subScanTaskDao: SubScanTaskDao,
) : TaskAction {

    @Autowired
    @Lazy
    @Qualifier(STATE_MACHINE_ID_SUB_SCAN_TASK)
    private lateinit var subtaskStateMachine: StateMachine

    override fun execute(source: String, target: String, event: Event): TransitResult {
        val context = event.context
        require(context is StopTaskContext)
        // 延迟停止任务，让剩余子任务提交完
        val userId = SecurityUtils.getUserId()
        val command = {
            subScanTaskDao.findByParentId(context.task.id!!).forEach {
                val finishSubtaskCtx = FinishSubtaskContext(
                    subtask = it, targetState = SubScanTaskStatus.STOPPED.name, modifiedBy = userId
                )
                subtaskStateMachine.sendEvent(it.status, Event(SubtaskEvent.STOP.name, finishSubtaskCtx))
            }
            scanTaskDao.updateStatus(context.task.id, ScanTaskStatus.STOPPED)
            scannerMetrics.taskStatusChange(ScanTaskStatus.STOPPING, ScanTaskStatus.STOPPED)
        }
        scheduler.schedule(command, STOP_TASK_DELAY_SECONDS, TimeUnit.SECONDS)
        return TransitResult(source)
    }

    override fun support(from: String, to: String, event: String): Boolean {
        return to == ScanTaskStatus.STOPPED.name
    }

    companion object {
        private val scheduler = Executors.newSingleThreadScheduledExecutor()

        /**
         * 延迟停止任务时间
         */
        private const val STOP_TASK_DELAY_SECONDS = 2L
    }
}
