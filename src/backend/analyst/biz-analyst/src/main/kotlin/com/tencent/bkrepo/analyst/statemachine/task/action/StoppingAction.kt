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
import com.tencent.bkrepo.analyst.metrics.ScannerMetrics
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus
import com.tencent.bkrepo.analyst.statemachine.Action
import com.tencent.bkrepo.analyst.statemachine.task.ScanTaskEvent
import com.tencent.bkrepo.analyst.statemachine.task.context.StopTaskContext
import com.tencent.bkrepo.analyst.statemachine.task.context.TaskContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy

@Action
class StoppingAction(
    private val scanTaskDao: ScanTaskDao,
    private val scannerMetrics: ScannerMetrics
) : TaskAction {
    @Autowired
    @Lazy
    private lateinit var taskStateMachine: StateMachine<ScanTaskStatus, ScanTaskEvent, TaskContext>

    override fun execute(from: ScanTaskStatus, to: ScanTaskStatus, event: ScanTaskEvent, context: TaskContext) {
        require(context is StopTaskContext)
        val task = context.task
        // 设置停止中的标记，中止提交子任务
        if (scanTaskDao.updateStatus(task.id!!, ScanTaskStatus.STOPPING, task.lastModifiedDate).modifiedCount == 1L) {
            scannerMetrics.taskStatusChange(ScanTaskStatus.valueOf(task.status), ScanTaskStatus.STOPPING)
            taskStateMachine.fireEvent(ScanTaskStatus.STOPPING, ScanTaskEvent.FINISH_STOP, context)
        }
    }

    override fun support(from: ScanTaskStatus, to: ScanTaskStatus, event: ScanTaskEvent): Boolean {
        return to == ScanTaskStatus.STOPPING && event == ScanTaskEvent.STOP
    }
}
