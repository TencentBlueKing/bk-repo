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

import com.tencent.bkrepo.analyst.dao.ArchiveSubScanTaskDao
import com.tencent.bkrepo.analyst.dao.ScanPlanDao
import com.tencent.bkrepo.analyst.dao.ScanTaskDao
import com.tencent.bkrepo.analyst.dao.SubScanTaskDao
import com.tencent.bkrepo.analyst.metrics.ScannerMetrics
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus
import com.tencent.bkrepo.analyst.statemachine.Action
import com.tencent.bkrepo.analyst.statemachine.TaskStateMachineConfiguration.Companion.STATE_MACHINE_ID_SCAN_TASK
import com.tencent.bkrepo.analyst.statemachine.task.ScanTaskEvent
import com.tencent.bkrepo.analyst.statemachine.task.context.ResetTaskContext
import com.tencent.bkrepo.analyst.statemachine.task.context.SubmitTaskContext
import com.tencent.bkrepo.analyst.utils.Converter
import com.tencent.bkrepo.statemachine.Event
import com.tencent.bkrepo.statemachine.StateMachine
import com.tencent.bkrepo.statemachine.TransitResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Lazy
import org.springframework.transaction.annotation.Transactional

@Action
class ResetAction(
    private val scanTaskDao: ScanTaskDao,
    private val subScanTaskDao: SubScanTaskDao,
    private val scanPlanDao: ScanPlanDao,
    private val archiveSubScanTaskDao: ArchiveSubScanTaskDao,
    private val scannerMetrics: ScannerMetrics
) : TaskAction {

    @Lazy
    @Autowired
    @Qualifier(STATE_MACHINE_ID_SCAN_TASK)
    private lateinit var taskStateMachine: StateMachine

    @Transactional(rollbackFor = [Throwable::class])
    override fun execute(source: String, target: String, event: Event): TransitResult {
        val context = event.context
        require(context is ResetTaskContext)
        val task = context.task
        // 任务超时后移除所有子任务，重置状态后重新提交执行
        val resetTask = scanTaskDao.resetTask(task.id!!, task.lastModifiedDate)
        if (resetTask != null) {
            subScanTaskDao.deleteByParentTaskId(task.id)
            archiveSubScanTaskDao.deleteByParentTaskId(task.id)
            scannerMetrics.taskStatusChange(ScanTaskStatus.valueOf(task.status), ScanTaskStatus.PENDING)
            val plan = task.planId?.let { scanPlanDao.get(it) }
            val submitTaskContext = SubmitTaskContext(Converter.convert(resetTask, plan))
            val submitEvent = Event(ScanTaskEvent.SUBMIT.name, submitTaskContext)
            return taskStateMachine.sendEvent(ScanTaskStatus.PENDING.name, submitEvent)
        }
        return TransitResult(source)
    }

    override fun support(from: String, to: String, event: String): Boolean {
        return (from == ScanTaskStatus.SCANNING_SUBMITTING.name || from == ScanTaskStatus.PENDING.name)
            && to == ScanTaskStatus.PENDING.name
            && event == ScanTaskEvent.RESET.name
    }
}
