/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.event.listener

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.event.ArtifactDownloadedEvent
import com.tencent.bkrepo.common.artifact.properties.ArtifactEventProperties
import com.tencent.bkrepo.common.artifact.event.node.NodeDownloadedEvent
import com.tencent.bkrepo.common.artifact.event.node.NodeUpdateAccessDateEvent
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactClient
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.metadata.service.log.OperateLogService
import com.tencent.bkrepo.common.service.otel.util.AsyncUtils.trace
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateAccessDateRequest
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class ArtifactDownloadListener(
    private val artifactClient: ArtifactClient,
    private val operateLogService: OperateLogService,
    private val artifactEventProperties: ArtifactEventProperties,
    private val messageSupplier: MessageSupplier,
) {
    // 更新节点访问时间任务线程池
    private val updateAccessDateExecutor = ThreadPoolExecutor(
        4,
        8,
        60,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(1000),
        ThreadFactoryBuilder().setNameFormat("update-access-date-%d").build(),
        ThreadPoolExecutor.CallerRunsPolicy()
    )

    // 节点下载后将节点信息放入缓存，当缓存失效时更新accessDate
    private val cache: Cache<Triple<String, String, String>, LocalDateTime> = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .concurrencyLevel(1)
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .removalListener<Triple<String, String, String>, LocalDateTime> {
            updateAccessDateExecutor.execute(
                Runnable {
                    updateNodeLastAccessDate(
                        projectId = it.key!!.first,
                        repoName = it.key!!.second,
                        fullPath = it.key!!.third,
                        accessDate = it.value!!
                    )
                }.trace()
            )
        }
        .build()

    @EventListener(ArtifactDownloadedEvent::class)
    fun listen(event: ArtifactDownloadedEvent) {
        try {
            if (event.context.artifacts.isNullOrEmpty()) {
                recordSingleNodeDownload(event)
            } else {
                recordMultiNodeDownload(event)
            }
        } catch (e: Exception) {
            logger.error("record node download error, ${e.message}")
        }
    }

    private fun recordSingleNodeDownload(event: ArtifactDownloadedEvent) {
        val nodeDetail = ArtifactContextHolder.getNodeDetail(event.context.artifactInfo) ?: return
        val eventList = mutableListOf<NodeDownloadedEvent>()
        recordNode(nodeDetail, event.context, eventList)
        operateLogService.saveEventsAsync(eventList, HttpContextHolder.getClientAddress())
    }

    private fun addToMq(nodeInfo: NodeInfo) {
        // 当没有达到更新频率时直接返回
        if (!durationCheck(nodeInfo.lastAccessDate, nodeInfo.lastModifiedDate)) return
        val projectRepoKey = "${nodeInfo.projectId}/${nodeInfo.repoName}"
        artifactEventProperties.filterProjectRepoKey.forEach {
            val regex = Regex(it.replace("*", ".*"))
            if (regex.matches(projectRepoKey)) {
                return
            }
        }
        if (artifactEventProperties.updateAccessDate) {
            // 兼容之前逻辑
            val key = Triple(nodeInfo.projectId, nodeInfo.repoName, nodeInfo.fullPath)
            if (cache.getIfPresent(key) == null) {
                cache.put(key, LocalDateTime.now())
            }
        } else {
            if (!artifactEventProperties.reportAccessDateEvent) {
                logger.info(
                    "mock update node [${nodeInfo.fullPath}] " +
                        "access time [${nodeInfo.projectId}/${nodeInfo.repoName}]"
                )
                return
            }
            if (artifactEventProperties.topic.isNullOrEmpty()) return
            val event = buildNodeUpdateAccessDateEvent(
                projectId = nodeInfo.projectId,
                repoName = nodeInfo.repoName,
                id = nodeInfo.id!!,
            )
            messageSupplier.delegateToSupplier(
                data = event,
                topic = artifactEventProperties.topic!!,
                key = event.getFullResourceKey(),
            )
        }
    }

    private fun updateNodeLastAccessDate(
        projectId: String,
        repoName: String,
        fullPath: String,
        accessDate: LocalDateTime
    ) {
        if (!artifactEventProperties.updateAccessDate) {
            logger.info("mock update node access time [$projectId/$repoName$fullPath]")
            return
        }
        val updateRequest = NodeUpdateAccessDateRequest(projectId, repoName, fullPath, SYSTEM_USER, accessDate)
        try {
            artifactClient.updateAccessDate(updateRequest)
        } catch (ignore: Exception) {
            logger.warn("update node access time [$updateRequest] error: ${ignore.message}, cause: ${ignore.cause}")
        }
    }

    private fun durationCheck(accessDate: String?, lastModifiedDate: String): Boolean {
        val temp = accessDate ?: lastModifiedDate
        return LocalDateTime.now().minusMinutes(artifactEventProperties.accessDateDuration.toMinutes())
            .isAfter(LocalDateTime.parse(temp, DateTimeFormatter.ISO_DATE_TIME))
    }

    private fun buildNodeUpdateAccessDateEvent(
        projectId: String,
        repoName: String,
        id: String,
        accessDate: LocalDateTime = LocalDateTime.now()
    ): NodeUpdateAccessDateEvent {
        return NodeUpdateAccessDateEvent(
            projectId = projectId,
            repoName = repoName,
            resourceKey = id,
            accessDate = accessDate.format(DateTimeFormatter.ISO_DATE_TIME),
            userId = SYSTEM_USER
        )
    }

    private fun recordMultiNodeDownload(event: ArtifactDownloadedEvent) {
        if (event.context.artifacts.isNullOrEmpty()) return
        val eventList = mutableListOf<NodeDownloadedEvent>()
        for (artifact in event.context.artifacts) {
            val nodeDetail = artifactClient.getNodeDetailOrNull(
                artifact.projectId, artifact.repoName, artifact.getArtifactFullPath()
            ) ?: continue
            recordNode(nodeDetail, event.context, eventList)
        }
        operateLogService.saveEventsAsync(eventList, HttpContextHolder.getClientAddress())
    }

    private fun recordNode(
        nodeDetail: NodeDetail,
        context: ArtifactDownloadContext,
        eventList: MutableList<NodeDownloadedEvent>
    ) {
        if (nodeDetail.folder) {
            val nodeList = artifactClient.listNode(
                nodeDetail.projectId, nodeDetail.repoName, nodeDetail.fullPath, includeFolder = false, deep = true
            )
            nodeList?.forEach {
                addToMq(it)
                eventList.add(buildDownloadEvent(context, it))
            }
        } else {
            addToMq(nodeDetail.nodeInfo)
            eventList.add(buildDownloadEvent(context, nodeDetail.nodeInfo))
        }
    }

    private fun buildDownloadEvent(
        context: ArtifactDownloadContext,
        node: NodeInfo
    ): NodeDownloadedEvent {
        val request = HttpContextHolder.getRequestOrNull()
        val data = node.metadata.orEmpty().toMutableMap()
        data[MD5] = node.md5 ?: StringPool.EMPTY
        data[SHA256] = node.sha256 ?: StringPool.EMPTY
        data[SHARE_USER_ID] = context.shareUserId
        data[USER_AGENT] = request?.getHeader(HttpHeaders.USER_AGENT) ?: StringPool.EMPTY
        data[SIZE] = node.size
        return NodeDownloadedEvent(
            projectId = node.projectId,
            repoName = node.repoName,
            resourceKey = node.fullPath,
            userId = context.userId,
            data = data
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactDownloadListener::class.java)
        private const val MD5 = "md5"
        private const val SHA256 = "sha256"
        private const val SHARE_USER_ID = "shareUserId"
        private const val USER_AGENT = "userAgent"
        private const val SIZE = "size"
    }
}
