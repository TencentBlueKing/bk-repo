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

package com.tencent.bkrepo.analyst.statemachine

import com.tencent.bkrepo.analyst.configuration.ScannerProperties
import com.tencent.bkrepo.analyst.dao.ScanTaskDao
import com.tencent.bkrepo.analyst.dao.SubScanTaskDao
import com.tencent.bkrepo.analyst.service.ScannerService
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent
import com.tencent.bkrepo.analyst.statemachine.subtask.context.FinishSubtaskContext
import com.tencent.bkrepo.analyst.statemachine.subtask.context.RetryContext
import com.tencent.bkrepo.analyst.statemachine.task.ScanTaskEvent
import com.tencent.bkrepo.analyst.statemachine.task.context.ResetTaskContext
import com.tencent.bkrepo.analyst.utils.SubtaskConverter
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus.BLOCK_TIMEOUT
import com.tencent.bkrepo.statemachine.Event
import com.tencent.bkrepo.statemachine.StateMachine
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 定时拉取任务，驱动状态流转
 * 子任务：（PULLED,EXECUTING,BLOCKED） -> （FAILED,TIMEOUT,BLOCK_TIMEOUT）
 * 父任务：（PENDING,SCANNING_SUBMITTING） -> （SCANNING_SUBMITTING）
 */
@Component
class TimeoutTaskScheduler(
    private val scannerProperties: ScannerProperties,
    private val subScanTaskDao: SubScanTaskDao,
    private val scanTaskDao: ScanTaskDao,
    private val scannerService: ScannerService,
    @Qualifier(TaskStateMachineConfiguration.STATE_MACHINE_ID_SCAN_TASK)
    private val taskStateMachine: StateMachine,
    @Qualifier(TaskStateMachineConfiguration.STATE_MACHINE_ID_SUB_SCAN_TASK)
    private val subtaskStateMachine: StateMachine,
) {
    @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = FIXED_DELAY)
    @Transactional(rollbackFor = [Throwable::class])
    fun enqueueTimeoutTask() {
        val task = scanTaskDao.timeoutTask(ScannerProperties.DEFAULT_TASK_EXECUTE_TIMEOUT_SECONDS) ?: return
        logger.error("task[${task.id}] submit timeout, will be reset")
        taskStateMachine.sendEvent(task.status, Event(ScanTaskEvent.RESET.name, ResetTaskContext(task)))
    }

    /**
     * 结束处于blocked状态超时的子任务
     */
    @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = FIXED_DELAY)
    fun finishBlockTimeoutSubScanTask() {
        val blockTimeout = scannerProperties.blockTimeout.seconds
        if (blockTimeout != 0L) {
            subScanTaskDao.blockedTimeoutTasks(blockTimeout).records.forEach { subtask ->
                logger.info("subTask[${subtask.id}] of parentTask[${subtask.parentScanTaskId}] block timeout")
                val context = FinishSubtaskContext(
                    subtask = subtask,
                    targetState = BLOCK_TIMEOUT.name,
                    reason = "Blocked until timeout, createDate[${subtask.createdDate}]"
                )
                val event = SubtaskEvent.finishEventOf(BLOCK_TIMEOUT.name)
                subtaskStateMachine.sendEvent(subtask.status, Event(event.name, context))
            }
        }
    }

    @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = FIXED_DELAY)
    fun retryTimeoutSubtask() {
        subScanTaskDao.timeoutTasks().records.forEach { task ->
            logger.info("subTask[${task.id}] of parentTask[${task.parentScanTaskId}] timeout[${task.lastModifiedDate}]")
            val context = RetryContext(SubtaskConverter.convert(task, scannerService.get(task.scanner)))
            subtaskStateMachine.sendEvent(task.status, Event(SubtaskEvent.RETRY.name, context))
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TimeoutTaskScheduler::class.java)

        /**
         * 定时扫描超时任务入队
         */
        private const val FIXED_DELAY = 3000L

    }
}
