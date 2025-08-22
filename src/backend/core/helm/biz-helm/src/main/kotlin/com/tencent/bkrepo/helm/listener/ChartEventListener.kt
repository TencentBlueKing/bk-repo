/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.helm.listener

import com.tencent.bkrepo.common.api.util.AsyncUtils.trace
import com.tencent.bkrepo.helm.listener.event.ChartDeleteEvent
import com.tencent.bkrepo.helm.listener.event.ChartUploadEvent
import com.tencent.bkrepo.helm.listener.event.ChartVersionDeleteEvent
import com.tencent.bkrepo.helm.listener.operation.IndexRefreshTask
import com.tencent.bkrepo.helm.pojo.chart.ChartOperationRequest
import com.tencent.bkrepo.helm.service.impl.AbstractChartService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class ChartEventListener : AbstractChartService() {


    /**
     * 删除chart版本，更新index.yaml文件
     */
    @EventListener(ChartVersionDeleteEvent::class)
    fun handle(event: ChartVersionDeleteEvent) {
        with(event.request) {
            logger.info("handling chart version delete event for [$name@$version] in repo [$projectId/$repoName]")
            handleEvent(event.request)
        }
    }

    /**
     * 删除chart的package，更新index.yaml文件
     */
    @EventListener(ChartDeleteEvent::class)
    fun handle(event: ChartDeleteEvent) {
        with(event.requestPackage) {
            logger.info("Handling package delete event for [$name] in repo [$projectId/$repoName]")
            handleEvent(event.requestPackage)
        }
    }

    /**
     * Chart文件上传成功后，进行后续操作，如创建package/packageVersion
     */
    @EventListener(ChartUploadEvent::class)
    fun handle(event: ChartUploadEvent) {
        with(event.uploadRequest) {
            logger.info("Handling package upload event for [$name] in repo [$projectId/$repoName]")
            metadataMap?.let {
                handleEvent(event.uploadRequest)
            }
        }
    }

    private fun handleEvent(request: ChartOperationRequest) {
        with(request) {
            helmChartEventRecordDao.updateEventTimeByProjectIdAndRepoName(
                projectId, repoName, LocalDateTime.now()
            )
        }

    }

    @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = INIT_DELAY)
    fun refreshIndex() {
        val records = helmChartEventRecordDao.findAllRecordsNeedToRefresh()
        for (record in records) {
                val lock = getLock(record.projectId, record.repoName) ?: continue
                try {
                    val exist = helmChartEventRecordDao.checkIndexExpiredStatus(
                        record.projectId, record.repoName
                    )
                    if (!exist) {
                        logger.info(
                            "Index.yaml is already the latest in repo [${record.projectId}/${record.repoName}]!"
                        )
                        unlock(record.projectId, record.repoName, lock)
                        continue
                    }
                    val task = IndexRefreshTask(
                        projectId = record.projectId,
                        repoName = record.repoName,
                        chartService = this,
                        lock = lock
                    )
                    threadPoolExecutor.submit(task.trace())
                } catch (e: Exception) {
                    logger.warn("Failed to create refresh task " +
                                     "for repo [${record.projectId}/${record.repoName}], error is $e")
                }
        }
    }


    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ChartEventListener::class.java)
        private const val FIXED_DELAY = 10000L
        private const val INIT_DELAY = 60000L

    }
}
