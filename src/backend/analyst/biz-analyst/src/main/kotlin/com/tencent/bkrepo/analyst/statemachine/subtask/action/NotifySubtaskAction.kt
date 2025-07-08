/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

import com.tencent.bkrepo.analyst.configuration.ScannerProperties
import com.tencent.bkrepo.analyst.dao.SubScanTaskDao
import com.tencent.bkrepo.analyst.metrics.ScannerMetrics
import com.tencent.bkrepo.analyst.service.ProjectScanConfigurationService
import com.tencent.bkrepo.analyst.statemachine.Action
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent
import com.tencent.bkrepo.analyst.statemachine.subtask.context.NotifySubtaskContext
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.lock.service.LockOperation
import com.tencent.bkrepo.statemachine.Event
import com.tencent.bkrepo.statemachine.TransitResult

@Action
class NotifySubtaskAction(
    private val subScanTaskDao: SubScanTaskDao,
    private val projectScanConfigurationService: ProjectScanConfigurationService,
    private val scannerMetrics: ScannerMetrics,
    private val scannerProperties: ScannerProperties,
    private val lockOperation: LockOperation
) : SubtaskAction {
    override fun execute(source: String, target: String, event: Event): TransitResult {
        val context = event.context
        require(context is NotifySubtaskContext)
        val projectId = context.projectId
        // 加锁避免多个进程唤醒项目子任务导致唤醒的任务数量超过配额
        // 此处不要求锁绝对可靠，极端情况下有两个进程同时持有锁时只会导致project执行的任务数量超过配额
        val lockKey = notifySubtaskLockKey(projectId)
        lockOperation.doWithLock(lockKey) {
            val subtaskCountLimit = projectScanConfigurationService
                .findProjectOrGlobalScanConfiguration(projectId)
                ?.subScanTaskCountLimit
                ?: scannerProperties.defaultProjectSubScanTaskCountLimit
            val countToUpdate = (subtaskCountLimit - subScanTaskDao.scanningCount(projectId)).toInt()
            if (countToUpdate > 0) {
                val notifiedCount = subScanTaskDao.notify(projectId, countToUpdate)?.modifiedCount?.toInt() ?: 0
                scannerMetrics.decSubtaskCountAndGet(SubScanTaskStatus.BLOCKED, notifiedCount.toDouble())
                scannerMetrics.incSubtaskCountAndGet(SubScanTaskStatus.CREATED, notifiedCount.toDouble())
            }
        }
        return TransitResult(target)
    }

    private fun notifySubtaskLockKey(projectId: String) = "scanner:lock:notify:$projectId:subtask"

    override fun support(from: String, to: String, event: String): Boolean {
        return from == SubScanTaskStatus.BLOCKED.name
            && to == SubScanTaskStatus.CREATED.name
            && event == SubtaskEvent.NOTIFY.name
    }
}
