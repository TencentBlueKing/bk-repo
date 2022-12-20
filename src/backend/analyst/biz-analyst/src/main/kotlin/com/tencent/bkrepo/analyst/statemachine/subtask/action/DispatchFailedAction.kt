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

package com.tencent.bkrepo.analyst.statemachine.subtask.action

import com.tencent.bkrepo.analyst.dao.ArchiveSubScanTaskDao
import com.tencent.bkrepo.analyst.dao.SubScanTaskDao
import com.tencent.bkrepo.analyst.metrics.ScannerMetrics
import com.tencent.bkrepo.analyst.statemachine.Action
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent.DISPATCH_FAILED
import com.tencent.bkrepo.analyst.statemachine.subtask.context.DispatchFailedContext
import com.tencent.bkrepo.analyst.statemachine.subtask.context.SubtaskContext
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus

@Action
class DispatchFailedAction(
    private val subScanTaskDao: SubScanTaskDao,
    private val archiveSubScanTaskDao: ArchiveSubScanTaskDao,
    private val scannerMetrics: ScannerMetrics
) : SubtaskAction {
    override fun execute(from: SubScanTaskStatus, to: SubScanTaskStatus, event: SubtaskEvent, context: SubtaskContext) {
        require(context is DispatchFailedContext)
        val oldStatus = SubScanTaskStatus.valueOf(from.name)
        val subtask = context.subtask
        subScanTaskDao.updateStatus(subtask.taskId, SubScanTaskStatus.CREATED, oldStatus)
        archiveSubScanTaskDao.updateStatus(subtask.taskId, SubScanTaskStatus.CREATED.name)
        scannerMetrics.subtaskStatusChange(oldStatus, SubScanTaskStatus.CREATED)
    }

    override fun support(from: SubScanTaskStatus, to: SubScanTaskStatus, event: SubtaskEvent): Boolean {
        return from == SubScanTaskStatus.PULLED && to == SubScanTaskStatus.CREATED && event == DISPATCH_FAILED
    }
}
