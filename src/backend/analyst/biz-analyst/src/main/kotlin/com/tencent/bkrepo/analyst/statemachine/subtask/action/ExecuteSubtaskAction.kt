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
import com.tencent.bkrepo.analyst.dao.ScanTaskDao
import com.tencent.bkrepo.analyst.dao.SubScanTaskDao
import com.tencent.bkrepo.analyst.event.SubtaskStatusChangedEvent
import com.tencent.bkrepo.analyst.metrics.ScannerMetrics
import com.tencent.bkrepo.analyst.statemachine.Action
import com.tencent.bkrepo.analyst.statemachine.subtask.context.ExecuteSubtaskContext
import com.tencent.bkrepo.analyst.utils.SubtaskConverter
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.statemachine.Event
import com.tencent.bkrepo.statemachine.TransitResult
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Action
class ExecuteSubtaskAction(
    private val scanTaskDao: ScanTaskDao,
    private val subScanTaskDao: SubScanTaskDao,
    private val archiveSubScanTaskDao: ArchiveSubScanTaskDao,
    private val scannerMetrics: ScannerMetrics,
    private val publisher: ApplicationEventPublisher
) : SubtaskAction {

    @Transactional(rollbackFor = [Throwable::class])
    override fun execute(source: String, target: String, event: Event): TransitResult {
        val context = event.context
        require(context is ExecuteSubtaskContext)
        val subtask = context.subtask
        val oldStatus = SubScanTaskStatus.valueOf(subtask.status)
        val updateResult = subScanTaskDao.updateStatus(
            subtask.id!!, SubScanTaskStatus.EXECUTING, oldStatus, subtask.lastModifiedDate
        )
        if (updateResult.modifiedCount == 1L) {
            archiveSubScanTaskDao.save(
                SubtaskConverter.convertToArchiveSubtask(subtask, SubScanTaskStatus.EXECUTING.name)
            )
            scannerMetrics.subtaskStatusChange(oldStatus, SubScanTaskStatus.EXECUTING)
            // 更新任务实际开始扫描的时间
            scanTaskDao.updateStartedDateTimeIfNotExists(subtask.parentScanTaskId, LocalDateTime.now())
            publisher.publishEvent(
                SubtaskStatusChangedEvent(
                    SubScanTaskStatus.valueOf(subtask.status),
                    SubtaskConverter.convertToPlanSubtask(subtask, SubScanTaskStatus.EXECUTING.name)
                )
            )
            return TransitResult(target)
        }
        return TransitResult(source)
    }

    override fun support(from: String, to: String, event: String): Boolean {
        return to == SubScanTaskStatus.EXECUTING.name
    }
}
