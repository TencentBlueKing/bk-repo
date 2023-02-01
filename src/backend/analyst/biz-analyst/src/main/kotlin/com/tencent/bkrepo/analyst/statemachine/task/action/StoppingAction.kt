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
import com.tencent.bkrepo.analyst.statemachine.task.ScanTaskEvent
import com.tencent.bkrepo.analyst.statemachine.task.context.StopTaskContext
import com.tencent.bkrepo.statemachine.Event
import com.tencent.bkrepo.statemachine.StateMachine
import com.tencent.bkrepo.statemachine.TransitResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Lazy

@Action
class StoppingAction(
    private val scanTaskDao: ScanTaskDao,
    private val scannerMetrics: ScannerMetrics
) : TaskAction {
    @Autowired
    @Lazy
    @Qualifier(STATE_MACHINE_ID_SCAN_TASK)
    private lateinit var taskStateMachine: StateMachine

    override fun execute(source: String, target: String, event: Event): TransitResult {
        val context = event.context
        require(context is StopTaskContext)
        val task = context.task
        // 设置停止中的标记，中止提交子任务
        if (scanTaskDao.updateStatus(task.id!!, ScanTaskStatus.STOPPING, task.lastModifiedDate).modifiedCount == 1L) {
            scannerMetrics.taskStatusChange(ScanTaskStatus.valueOf(task.status), ScanTaskStatus.STOPPING)
            val stopEvent = Event(ScanTaskEvent.FINISH_STOP.name, context)
            return taskStateMachine.sendEvent(ScanTaskStatus.STOPPING.name, stopEvent)
        }
        return TransitResult(source)
    }

    override fun support(from: String, to: String, event: String): Boolean {
        return to == ScanTaskStatus.STOPPING.name && event == ScanTaskEvent.STOP.name
    }
}
