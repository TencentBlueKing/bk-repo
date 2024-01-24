/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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
import com.tencent.bkrepo.analyst.dao.ArchiveSubScanTaskDao
import com.tencent.bkrepo.analyst.dao.SubScanTaskDao
import com.tencent.bkrepo.analyst.metrics.ScannerMetrics
import com.tencent.bkrepo.analyst.model.TSubScanTask
import com.tencent.bkrepo.analyst.statemachine.Action
import com.tencent.bkrepo.analyst.statemachine.TaskStateMachineConfiguration
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent
import com.tencent.bkrepo.analyst.statemachine.subtask.context.FinishSubtaskContext
import com.tencent.bkrepo.analyst.statemachine.subtask.context.RetryContext
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus.CREATED
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus.EXECUTING
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus.PULLED
import com.tencent.bkrepo.statemachine.Event
import com.tencent.bkrepo.statemachine.StateMachine
import com.tencent.bkrepo.statemachine.TransitResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Lazy
import java.time.Duration
import java.time.LocalDateTime

@Action
class RetryAction(
    private val subScanTaskDao: SubScanTaskDao,
    private val archiveSubScanTaskDao: ArchiveSubScanTaskDao,
    private val scannerMetrics: ScannerMetrics,
    private val scannerProperties: ScannerProperties,
) : SubtaskAction {

    @Autowired
    @Lazy
    @Qualifier(TaskStateMachineConfiguration.STATE_MACHINE_ID_SUB_SCAN_TASK)
    private lateinit var subtaskStateMachine: StateMachine

    override fun execute(source: String, target: String, event: Event): TransitResult {
        val context = event.context
        require(context is RetryContext)
        val oldStatus = SubScanTaskStatus.valueOf(source)
        val subtask = context.subtask

        val task = subScanTaskDao.findById(subtask.taskId)!!
        val exceedMaxTaskDuration =
            Duration.between(task.createdDate, LocalDateTime.now()) > scannerProperties.maxTaskDuration
        return if (task.executedTimes + 1 > DEFAULT_MAX_EXECUTE_TIMES || exceedMaxTaskDuration) {
            // 超过最大重试次数或达到最大允许执行时间，直接结束任务
            logger.info(
                "subTask[${task.id}] of parentTask[${task.parentScanTaskId}] " +
                        "exceed max execute times or timeout[${task.lastModifiedDate}]"
            )
            finishSubtask(task)
        } else if (subScanTaskDao.updateStatus(subtask.taskId, CREATED, oldStatus).modifiedCount == 1L) {
            archiveSubScanTaskDao.updateStatus(subtask.taskId, CREATED.name)
            scannerMetrics.subtaskStatusChange(oldStatus, CREATED)
            TransitResult(CREATED.name, true)
        } else {
            // 任务已经被修改，返回原状态
            TransitResult(source, false)
        }
    }

    private fun finishSubtask(subtask: TSubScanTask): TransitResult {
        with(subtask) {
            val targetState = if (status == EXECUTING.name || status == PULLED.name) {
                SubScanTaskStatus.TIMEOUT.name
            } else {
                SubScanTaskStatus.FAILED.name
            }
            val reason = "Exceed max times or timeout,executedTimes[$executedTimes],createdDateTime[${createdDate}]," +
                    "heartbeatDateTime[$heartbeatDateTime],timeoutDateTime[$timeoutDateTime]"
            val context = FinishSubtaskContext(subtask = subtask, targetState = targetState, reason = reason)
            val event = SubtaskEvent.finishEventOf(targetState)
            return subtaskStateMachine.sendEvent(subtask.status, Event(event.name, context))
        }
    }

    override fun support(from: String, to: String, event: String): Boolean {
        return (from == PULLED.name || from == EXECUTING.name) && event == SubtaskEvent.RETRY.name
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RetryAction::class.java)

        /**
         * 最大允许重复执行次数
         */
        private const val DEFAULT_MAX_EXECUTE_TIMES = 3
    }
}
