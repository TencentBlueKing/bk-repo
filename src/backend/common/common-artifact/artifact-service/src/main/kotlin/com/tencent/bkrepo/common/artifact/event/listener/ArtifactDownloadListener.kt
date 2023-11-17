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
import com.tencent.bkrepo.common.artifact.event.ArtifactEventProperties
import com.tencent.bkrepo.common.artifact.event.node.NodeDownloadedEvent
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.operate.api.OperateLogService
import com.tencent.bkrepo.common.service.otel.util.AsyncUtils.trace
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateAccessDateRequest
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import java.time.LocalDateTime
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class ArtifactDownloadListener(
    private val nodeClient: NodeClient,
    private val operateLogService: OperateLogService,
    private val artifactEventProperties: ArtifactEventProperties
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

    private fun ensureCacheNodeUpdateFinish() {
        val keys = cache.asMap().keys
        logger.info("${keys.size} node will updating access date")
        val subKeysList = keys.chunked(keys.size / 10 + 1)
        subKeysList.forEach {
            cache.invalidateAll(it)
            while (updateAccessDateExecutor.activeCount > 0) {
                Thread.sleep(10 * 1000)
            }
        }
        updateAccessDateExecutor.shutdown()
        logger.info("${keys.size} node update access date finished")
    }

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
                nodeClient.listNode(projectId, repoName, node.fullPath, includeFolder = false, deep = true).data!!
            addToCache(projectId, repoName, node.fullPath)
            val eventList = nodeList.map { buildDownloadEvent(event.context, NodeDetail(it)) }
            operateLogService.saveEventsAsync(eventList, HttpContextHolder.getClientAddress())
        } else {
            addToCache(projectId, repoName, node.fullPath)
            val downloadedEvent = buildDownloadEvent(event.context, node)
            operateLogService.saveEventAsync(downloadedEvent, HttpContextHolder.getClientAddress())
        }
    }

    private fun addToCache(projectId: String, repoName: String, fullPath: String) {
        val projectRepoKey = "$projectId/$repoName"
        artifactEventProperties.filterProjectRepoKey.forEach {
            val regex = Regex(it.replace("*", ".*"))
            if (regex.matches(projectRepoKey)) {
                return
            }
        }
        val key = Triple(projectId, repoName, fullPath)
        if (cache.getIfPresent(key) == null) {
            cache.put(key, LocalDateTime.now())
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
            nodeClient.updateNodeAccessDate(updateRequest)
        } catch (ignore: Exception) {
            logger.warn("update node access time [$updateRequest] error, ${ignore.message}")
        }
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
            addToCache(it.projectId, it.repoName, it.getArtifactFullPath())
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
