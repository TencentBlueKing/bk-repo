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

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.analysis.executor.api.ExecutorClient
import com.tencent.bkrepo.analyst.event.SubtaskStatusChangedEvent
import com.tencent.bkrepo.analyst.service.ExecutionClusterService
import com.tencent.bkrepo.analyst.service.ScannerService
import com.tencent.bkrepo.analyst.utils.SubtaskConverter
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.TimeUnit

@Suppress("LongParameterList")
open class SubtaskPoller(
    private val dispatcherFactory: SubtaskDispatcherFactory,
    private val executionClusterService: ExecutionClusterService,
    private val scannerService: ScannerService,
    private val executorClient: ObjectProvider<ExecutorClient>,
    private val executor: ThreadPoolTaskExecutor,
) {

    private val dispatcherCache: LoadingCache<String, SubtaskDispatcher> by lazy {
        CacheBuilder
            .newBuilder()
            .maximumSize(MAX_EXECUTION_CLUSTER_CACHE_SIZE)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build(
                object : CacheLoader<String, SubtaskDispatcher>() {
                    override fun load(key: String): SubtaskDispatcher = dispatcherFactory.create(key)
                }
            )
    }

    @Scheduled(initialDelay = POLL_INITIAL_DELAY, fixedDelay = POLL_DELAY)
    open fun dispatch() {
        executionClusterService.list().forEach {
            executor.execute {
                logger.info("cluster [${it.name}] start to dispatch subtask")
                dispatcherCache.get(it.name).dispatch()
                logger.info("cluster [${it.name}] dispatch finished")
            }
        }
    }

    /**
     * 任务执行结束后进行资源清理
     */
    @Async
    @EventListener(SubtaskStatusChangedEvent::class)
    open fun clean(event: SubtaskStatusChangedEvent) {
        val dispatcher = event.dispatcher?.let { dispatcherCache.get(it) }
        val subtaskFinished = SubScanTaskStatus.finishedStatus(event.subtask.status)
        if (subtaskFinished && dispatcher != null) {
            val scanner = scannerService.get(event.subtask.scanner)
            val result = dispatcher.clean(SubtaskConverter.convert(event.subtask, scanner), event.subtask.status)
            val subtaskId = event.subtask.latestSubScanTaskId
            logger.info("clean result[$result], subtask[$subtaskId], dispatcher[${dispatcher.name()}]")
        }

        // oldStatus为null时说明是复用扫描结果，此时不调用executor接口清理
        val reuseResult = event.oldStatus == null
        if (subtaskFinished && event.dispatcher.isNullOrEmpty() && !reuseResult) {
            // dispatcher为空时表示通过analysis-executor执行的任务，此时调用其接口进行清理
            val subtaskId = event.subtask.latestSubScanTaskId!!
            val result = executorClient.ifAvailable?.stop(subtaskId)
            logger.info("stop subtask[$subtaskId] executor result[$result]")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SubtaskPoller::class.java)
        private const val POLL_INITIAL_DELAY = 30000L
        private const val POLL_DELAY = 5000L
        private const val MAX_EXECUTION_CLUSTER_CACHE_SIZE = 10L
    }
}
