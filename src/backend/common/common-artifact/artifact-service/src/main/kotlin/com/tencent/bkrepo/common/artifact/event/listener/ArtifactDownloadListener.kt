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

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.event.ArtifactDownloadedEvent
import com.tencent.bkrepo.common.artifact.event.ArtifactEventProperties
import com.tencent.bkrepo.common.artifact.event.node.NodeDownloadedEvent
import com.tencent.bkrepo.common.artifact.event.node.NodeUpdateAccessDateEvent
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactClient
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.operate.api.OperateLogService
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ArtifactDownloadListener(
    private val artifactClient: ArtifactClient,
    private val operateLogService: OperateLogService,
    private val artifactEventProperties: ArtifactEventProperties,
    private val messageSupplier: MessageSupplier,
) {

    @EventListener(ArtifactDownloadedEvent::class)
    fun listen(event: ArtifactDownloadedEvent) {
        if (event.context.artifacts.isNullOrEmpty()) {
            recordSingleNodeDownload(event)
        } else {
            recordMultiNodeDownload(event)
        }
    }

    private fun recordSingleNodeDownload(event: ArtifactDownloadedEvent) {
        val projectId = event.context.projectId
        val repoName = event.context.repoName
        val fullPath = event.context.artifactInfo.getArtifactFullPath()
        val userId = event.context.userId
        val node = ArtifactContextHolder.getNodeDetail(event.context.artifactInfo)
        if (node == null) {
            val downloadedEvent = NodeDownloadedEvent(
                projectId = projectId,
                repoName = repoName,
                resourceKey = fullPath,
                userId = userId,
                data = emptyMap()
            )
            operateLogService.saveEventAsync(downloadedEvent, HttpContextHolder.getClientAddress())
        } else if (node.folder) {
            val nodeList =
                artifactClient.listNode(projectId, repoName, node.fullPath, includeFolder = false, deep = true)!!
            val eventList = mutableListOf<NodeDownloadedEvent>()
            nodeList.forEach {
                val nodeDetail = artifactClient.getNodeDetailOrNull(it.projectId, it.repoName, it.fullPath)
                if (nodeDetail != null) {
                    addToMq(nodeDetail)
                    eventList.add(buildDownloadEvent(event.context, nodeDetail))
                }
            }
            operateLogService.saveEventsAsync(eventList, HttpContextHolder.getClientAddress())
        } else {
            addToMq(node)
            val downloadedEvent = buildDownloadEvent(event.context, node)
            operateLogService.saveEventAsync(downloadedEvent, HttpContextHolder.getClientAddress())
        }
    }

    private fun addToMq(nodeDetail: NodeDetail) {
        // 当没有达到更新频率时直接返回
        if (!durationCheck(nodeDetail.lastAccessDate, nodeDetail.lastModifiedDate)) return
        val projectRepoKey = "${nodeDetail.projectId}/${nodeDetail.repoName}"
        artifactEventProperties.filterProjectRepoKey.forEach {
            val regex = Regex(it.replace("*", ".*"))
            if (regex.matches(projectRepoKey)) {
                return
            }
        }
        if (!artifactEventProperties.reportAccessDateEvent) {
            logger.info(
                "mock update node [${nodeDetail.nodeInfo.id}] " +
                    "access time [${nodeDetail.projectId}/${nodeDetail.repoName}]"
            )
            return
        }
        val event = buildNodeUpdateAccessDateEvent(
            projectId = nodeDetail.projectId,
            repoName = nodeDetail.repoName,
            id = nodeDetail.nodeInfo.id!!,
        )
        messageSupplier.delegateToSupplier(
            data = event,
            topic = event.topic,
            key = event.getFullResourceKey(),
        )
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
        val userId = event.context.userId
        val eventList = event.context.artifacts!!.map {
            NodeDownloadedEvent(
                projectId = it.projectId,
                repoName = it.repoName,
                resourceKey = it.getArtifactFullPath(),
                userId = userId,
                data = emptyMap()
            )
        }
        operateLogService.saveEventsAsync(eventList, HttpContextHolder.getClientAddress())
        event.context.artifacts.forEach {
            val nodeDetail = artifactClient.getNodeDetailOrNull(it.projectId, it.repoName, it.getArtifactFullPath())
            if (nodeDetail != null) {
                addToMq(nodeDetail)
            }
        }
    }

    private fun buildDownloadEvent(
        context: ArtifactDownloadContext,
        node: NodeDetail
    ): NodeDownloadedEvent {
        val request = HttpContextHolder.getRequestOrNull()
        val data = node.metadata.toMutableMap()
        data[MD5] = node.md5 ?: StringPool.EMPTY
        data[SHA256] = node.sha256 ?: StringPool.EMPTY
        data[SHARE_USER_ID] = context.shareUserId
        data[USER_AGENT] = request?.getHeader(HttpHeaders.USER_AGENT) ?: StringPool.EMPTY
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
    }
}
