/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.redis.RedisOperation
import com.tencent.bkrepo.helm.config.HelmProperties
import com.tencent.bkrepo.helm.constants.CHANGE_EVENT_COUNT_PREFIX
import com.tencent.bkrepo.helm.constants.DEFAULT_TYPE
import com.tencent.bkrepo.helm.constants.PACKAGE_DELETE_EVENT_REQUEST_TYPE
import com.tencent.bkrepo.helm.constants.REFRESH_INDEX_KEY
import com.tencent.bkrepo.helm.constants.UPLOAD_EVENT_REQUEST_TYPE
import com.tencent.bkrepo.helm.constants.VERSION_DELETE_EVENT_REQUEST_TYPE
import com.tencent.bkrepo.helm.listener.event.ChartDeleteEvent
import com.tencent.bkrepo.helm.listener.event.ChartUploadEvent
import com.tencent.bkrepo.helm.listener.event.ChartVersionDeleteEvent
import com.tencent.bkrepo.helm.listener.operation.ChartDeleteOperation
import com.tencent.bkrepo.helm.listener.operation.ChartPackageDeleteOperation
import com.tencent.bkrepo.helm.listener.operation.ChartUploadOperation
import com.tencent.bkrepo.helm.pojo.chart.ChartOperationRequest
import com.tencent.bkrepo.helm.pojo.chart.ChartPackageDeleteRequest
import com.tencent.bkrepo.helm.pojo.chart.ChartUploadRequest
import com.tencent.bkrepo.helm.pojo.chart.ChartVersionDeleteRequest
import com.tencent.bkrepo.helm.service.impl.AbstractChartService
import com.tencent.bkrepo.helm.utils.HelmMetadataUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class ChartEventListener(
    private val helmProperties: HelmProperties,
    private val redisOperation: RedisOperation,
    taskScheduler: ThreadPoolTaskScheduler,
    ) : AbstractChartService() {

    @Value("\${lock.type:}")
    private val lockType = DEFAULT_TYPE

    private val eventMap: ConcurrentHashMap<String, MutableList<String>> = ConcurrentHashMap()

    init {
        taskScheduler.scheduleWithFixedDelay(this::refreshIndex, helmProperties.refreshTime)
    }

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
            val requestStr = request.toJsonString()
            if (lockType.isEmpty() || lockType == DEFAULT_TYPE) {
                val mapKey = buildKey(projectId, repoName)
                eventMap.putIfAbsent(mapKey, mutableListOf())
                eventMap[mapKey]!!.add(requestStr)
            } else {
                redisOperation.ladd(REFRESH_INDEX_KEY, requestStr)
            }
            val incKey = buildKey(projectId, repoName, CHANGE_EVENT_COUNT_PREFIX)
            casService.increment(incKey, 1)
        }
    }

    fun refreshIndex() {
        if (lockType.isEmpty() || lockType == DEFAULT_TYPE) {
            if (eventMap.isNotEmpty()) {
                eventMap.forEach { (k, v) ->
                    if (v.size != 0) {
                        val (projectId, repoName) = splitKey(k)
                        val lock = getLock(projectId, repoName)
                        if (lock != null) {
                            try {
                                val requestStr = v.removeAt(0)
                                val request = requestStr.readJsonString<ChartOperationRequest>()
                                submitRequest(requestStr, request.type, lock)
                            } catch (e: Exception) {
                                logger.error("Failed to create refresh task for $v, error is $e")
                            }
                        }
                    }
                }
            }
        } else {
            val eventList = redisOperation.lall(REFRESH_INDEX_KEY) ?: return
            eventList.forEach {
                val request = it.readJsonString<ChartOperationRequest>()
                val lock = getLock(request.projectId, request.repoName)
                if (lock != null) {
                    try {
                        submitRequest(it, request.type, lock)
                    } catch (e: Exception) {
                        logger.error("Failed to create refresh task for $it, error is $e")
                    } finally {
                        redisOperation.lremove(REFRESH_INDEX_KEY, it)
                    }
                }
            }
        }
    }

    private fun submitRequest(json: String, type: String, lock: Any) {
        val task = when (type) {
            UPLOAD_EVENT_REQUEST_TYPE -> {
                val request = json.readJsonString<ChartUploadRequest>()
                val helmChartMetadata = HelmMetadataUtils.convertToObject(request.metadataMap!!)
                val nodeDetail = nodeClient.getNodeDetail(
                    request.projectId, request.repoName, request.fullPath
                ).data
                nodeDetail?.let {
                    helmChartMetadata.created = convertDateTime(nodeDetail.createdDate)
                    helmChartMetadata.digest = nodeDetail.sha256
                }
                if (nodeDetail != null) {
                    ChartUploadOperation(
                        request,
                        helmChartMetadata,
                        helmProperties.domain,
                        this@ChartEventListener,
                        lock
                    )
                } else {
                    null
                }
            }
            PACKAGE_DELETE_EVENT_REQUEST_TYPE -> {
                val request = json.readJsonString<ChartPackageDeleteRequest>()
                ChartPackageDeleteOperation(request, this@ChartEventListener, lock)
            }
            VERSION_DELETE_EVENT_REQUEST_TYPE -> {
                val request = json.readJsonString<ChartVersionDeleteRequest>()
                ChartDeleteOperation(request, this@ChartEventListener, lock)
            }
            else -> null
        }
        task?.let { threadPoolExecutor.submit(it) }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ChartEventListener::class.java)
    }
}
