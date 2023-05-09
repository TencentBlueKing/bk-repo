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

package com.tencent.bkrepo.analyst.dispatcher

import com.tencent.bkrepo.analysis.executor.api.ExecutorClient
import com.tencent.bkrepo.analyst.event.SubtaskStatusChangedEvent
import com.tencent.bkrepo.analyst.pojo.SubScanTask
import com.tencent.bkrepo.analyst.service.ScanService
import com.tencent.bkrepo.analyst.service.ScannerService
import com.tencent.bkrepo.analyst.service.TemporaryScanTokenService
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent.DISPATCH_FAILED
import com.tencent.bkrepo.analyst.statemachine.subtask.context.DispatchFailedContext
import com.tencent.bkrepo.analyst.utils.SubtaskConverter
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus.PULLED
import com.tencent.bkrepo.statemachine.Event
import com.tencent.bkrepo.statemachine.StateMachine
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled

open class SubtaskPoller(
    private val dispatcher: SubtaskDispatcher,
    private val scanService: ScanService,
    private val scannerService: ScannerService,
    private val temporaryScanTokenService: TemporaryScanTokenService,
    private val subtaskStateMachine: StateMachine,
    private val executorClient: ObjectProvider<ExecutorClient>
) {
    @Scheduled(initialDelay = POLL_INITIAL_DELAY, fixedDelay = POLL_DELAY)
    open fun dispatch() {
        var subtask: SubScanTask?
        // 不加锁，允许少量超过执行器的资源限制
        for (i in 0 until dispatcher.availableCount()) {
            subtask = scanService.pull(dispatcher.name()) ?: break
            subtask.token = temporaryScanTokenService.createToken(subtask.taskId)
            if (!dispatcher.dispatch(subtask)) {
                // 分发失败，放回队列中
                logger.warn("dispatch subtask failed, subtask[${subtask.taskId}]")
                subtaskStateMachine.sendEvent(PULLED.name, Event(DISPATCH_FAILED.name, DispatchFailedContext(subtask)))
            }
        }
    }

    /**
     * 任务执行结束后进行资源清理
     */
    @Async
    @EventListener(SubtaskStatusChangedEvent::class)
    open fun clean(event: SubtaskStatusChangedEvent) {
        if (SubScanTaskStatus.finishedStatus(event.subtask.status) && event.dispatcher == dispatcher.name()) {
            val scanner = scannerService.get(event.subtask.scanner)
            val result = dispatcher.clean(SubtaskConverter.convert(event.subtask, scanner), event.subtask.status)
            val subtaskId = event.subtask.latestSubScanTaskId
            logger.info("clean result[$result], subtask[$subtaskId], dispatcher[${dispatcher.name()}]")
        }
        if (SubScanTaskStatus.finishedStatus(event.subtask.status) && event.dispatcher.isNullOrEmpty()) {
            val subtaskId = event.subtask.latestSubScanTaskId!!
            val result = executorClient.ifAvailable?.stop(subtaskId)
            logger.info("stop subtask[$subtaskId] executor result[$result]")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SubtaskPoller::class.java)
        private const val POLL_INITIAL_DELAY = 30000L
        private const val POLL_DELAY = 5000L
    }
}
