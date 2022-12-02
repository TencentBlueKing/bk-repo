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

import com.alibaba.cola.statemachine.StateMachine
import com.tencent.bkrepo.analyst.dao.ScanTaskDao
import com.tencent.bkrepo.analyst.dao.SubScanTaskDao
import com.tencent.bkrepo.analyst.metrics.ScannerMetrics
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus
import com.tencent.bkrepo.analyst.statemachine.Action
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent
import com.tencent.bkrepo.analyst.statemachine.subtask.context.FinishSubtaskContext
import com.tencent.bkrepo.analyst.statemachine.subtask.context.SubtaskContext
import com.tencent.bkrepo.analyst.statemachine.task.ScanTaskEvent
import com.tencent.bkrepo.analyst.statemachine.task.context.StopTaskContext
import com.tencent.bkrepo.analyst.statemachine.task.context.TaskContext
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.security.util.SecurityUtils
import org.springframework.beans.factory.annotation.Autowired
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
    private lateinit var subtaskStateMachine: StateMachine<SubScanTaskStatus, SubtaskEvent, SubtaskContext>

    override fun execute(from: ScanTaskStatus, to: ScanTaskStatus, event: ScanTaskEvent, context: TaskContext) {
        require(context is StopTaskContext)
        // 延迟停止任务，让剩余子任务提交完
        val userId = SecurityUtils.getUserId()
        val command = {
            subScanTaskDao.findByParentId(context.task.id!!).forEach {
                val finishSubtaskCtx = FinishSubtaskContext(
                    subtask = it, targetState = SubScanTaskStatus.STOPPED.name, modifiedBy = userId
                )
                subtaskStateMachine.fireEvent(SubScanTaskStatus.valueOf(it.status), SubtaskEvent.STOP, finishSubtaskCtx)
            }
            scanTaskDao.updateStatus(context.task.id, ScanTaskStatus.STOPPED)
            scannerMetrics.taskStatusChange(ScanTaskStatus.STOPPING, ScanTaskStatus.STOPPED)
        }
        scheduler.schedule(command, STOP_TASK_DELAY_SECONDS, TimeUnit.SECONDS)
    }

    override fun support(from: ScanTaskStatus, to: ScanTaskStatus, event: ScanTaskEvent): Boolean {
        return to == ScanTaskStatus.STOPPED
    }

    companion object {
        private val scheduler = Executors.newSingleThreadScheduledExecutor()

        /**
         * 延迟停止任务时间
         */
        private const val STOP_TASK_DELAY_SECONDS = 2L
    }
}
