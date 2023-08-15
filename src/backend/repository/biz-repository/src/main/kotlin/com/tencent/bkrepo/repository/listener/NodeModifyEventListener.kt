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

package com.tencent.bkrepo.repository.listener

import com.google.common.cache.CacheBuilder
import com.google.common.cache.RemovalCause
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.LOG
import com.tencent.bkrepo.common.artifact.constant.REPORT
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.artifact.event.node.NodeCopiedEvent
import com.tencent.bkrepo.common.artifact.event.node.NodeMovedEvent
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.service.node.NodeService
import com.tencent.bkrepo.repository.util.NodeQueryHelper
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit


/**
 * 节点事件监听，用于folder size统计
 */
@Component
class NodeModifyEventListener(
    private val nodeService: NodeService,
    private val nodeDao: NodeDao
    )  {

    private val cache = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .removalListener<Triple<String, String, String>, Pair<Long, Long>> {
            if (it.cause == RemovalCause.REPLACED) return@removalListener
            logger.info("remove ${it.key}, ${it.value}, cause ${it.cause}, Thread ${Thread.currentThread().name}")
            nodeDao.updateFolderSize(
                projectId = it.key!!.first,
                repoName = it.key!!.second,
                fullPath = it.key!!.third,
                size = it.value.first,
                nodeNum = it.value.second
            )
        }
        .build<Triple<String, String, String>, Pair<Long, Long>>()

    /**
     * 允许接收的事件类型
     */
    private val acceptTypes = setOf(
        EventType.NODE_COPIED,
        EventType.NODE_CREATED,
        EventType.NODE_DELETED,
        EventType.NODE_MOVED
    )

    /**
     * 统计generic仓库目录节点大小
     */
    @EventListener(ArtifactEvent::class)
    fun handle(event: ArtifactEvent) {
        if (!acceptTypes.contains(event.type)) {
            return
        }
        //过滤 report 和log 仓库
        if (event.repoName == REPORT || event.repoName == LOG) return
        try {
            updateModifiedFolderCache(event)
        } catch (ignore: Exception) {
        }
    }


    /**
     * 将有变更的目录节点存放在缓存中
     */
    private fun updateModifiedFolderCache(event: ArtifactEvent) {
        logger.info("event type ${event.type}")
        val (createdArtifactInfo, deletedArtifactInfo) = when (event.type) {
            EventType.NODE_MOVED -> {
                val movedEvent = event as NodeMovedEvent
                val createdArtifactInfo = ArtifactInfo(
                    projectId = movedEvent.dstProjectId,
                    repoName = movedEvent.dstRepoName,
                    artifactUri = movedEvent.dstFullPath
                )
                val deletedArtifactInfo = ArtifactInfo(
                    projectId = movedEvent.projectId,
                    repoName = movedEvent.repoName,
                    artifactUri = movedEvent.resourceKey
                )
                Pair(createdArtifactInfo, deletedArtifactInfo)
            }
            EventType.NODE_DELETED -> {
                val deletedArtifactInfo = ArtifactInfo(
                    projectId = event.projectId,
                    repoName = event.repoName,
                    artifactUri = event.resourceKey
                )
                Pair(null, deletedArtifactInfo)
            }
            EventType.NODE_COPIED -> {
                val copyEvent = event as NodeCopiedEvent
                val createdArtifactInfo = ArtifactInfo(
                    projectId = copyEvent.dstProjectId,
                    repoName = copyEvent.dstRepoName,
                    artifactUri = copyEvent.dstFullPath
                )
                Pair(createdArtifactInfo, null)
            }
            EventType.NODE_CREATED -> {
                val createdArtifactInfo = ArtifactInfo(
                    projectId = event.projectId,
                    repoName = event.repoName,
                    artifactUri = event.resourceKey
                )
                Pair(createdArtifactInfo, null)
            }
            else -> throw UnsupportedOperationException()
        }
        findModifiedFolders(
            createdArtifactInfo = createdArtifactInfo,
            deletedArtifactInfo = deletedArtifactInfo
        )
    }

    private fun findModifiedFolders(
        createdArtifactInfo: ArtifactInfo? = null,
        deletedArtifactInfo: ArtifactInfo? = null
    ) {
        if (createdArtifactInfo != null) {
            findModifiedFolders(createdArtifactInfo, false)
        }
        if (deletedArtifactInfo != null) {
            findModifiedFolders(deletedArtifactInfo, true)
        }
    }

    private fun findModifiedFolders(artifactInfo: ArtifactInfo, deleted: Boolean) {
        val node = if (deleted) {
            nodeService.getDeletedNodeDetail(artifactInfo).firstOrNull() ?: return
        } else {
            // 查询节点信息，当节点新增，然后删除后可能会找不到节点
            nodeService.getNodeDetail(artifactInfo)
                ?: nodeService.getDeletedNodeDetail(artifactInfo).firstOrNull() ?: return
        }
        logger.info("start to stat modified node size with fullPath ${node.fullPath}" +
                        " in repo ${node.projectId}|${node.repoName}")
        if (node.folder) {
            findAndCacheSubFolders(
                artifactInfo = artifactInfo,
                deleted = node.nodeInfo.deleted,
                deletedFlag = deleted
            )
        } else {
            updateCache(
                projectId = artifactInfo.projectId,
                repoName = artifactInfo.repoName,
                fullPath = artifactInfo.getArtifactFullPath(),
                size = node.size,
                deleted = deleted
            )
        }
    }

    @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = FIXED_DELAY)
    fun checkCacheValue() {
        cache.invalidateAll()
    }

    private fun updateCache(
        projectId: String,
        repoName: String,
        fullPath: String,
        size: Long,
        deleted: Boolean = false
    ) {

        // 更新当前节点所有上级目录统计信息
        PathUtils.resolveAncestorFolder(fullPath).forEach{
            val key = Triple(projectId, repoName, it)
            var (cachedSize, nodeNum) = cache.getIfPresent(key) ?: Pair(0L, 0L)
            if (deleted) {
                cachedSize -= size
                nodeNum -= 1
            } else {
                cachedSize += size
                nodeNum += 1
            }
            cache.put(key, Pair(cachedSize, nodeNum))
        }
    }

    private fun findAllNodesUnderFolder(
        projectId: String,
        repoName: String,
        fullPath: String,
        deleted: String? = null
    ): List<TNode> {
        val srcRootNodePath = PathUtils.toPath(fullPath)
        val query = if (!deleted.isNullOrEmpty()) {
            val criteria = where(TNode::projectId).isEqualTo(projectId)
                .and(TNode::repoName).isEqualTo(repoName)
                .and(TNode::deleted).isEqualTo(deleted)
                .and(TNode::folder).isEqualTo(false)
                .and(TNode::fullPath).regex("^${PathUtils.escapeRegex(srcRootNodePath)}")
            Query(criteria)
        } else {
            val listOption = NodeListOption(includeFolder = false, deep = true)
            NodeQueryHelper.nodeListQuery(projectId, repoName, srcRootNodePath, listOption)
        }
        return nodeDao.find(query)
    }

    private fun findAndCacheSubFolders(
        artifactInfo: ArtifactInfo,
        deleted: String? = null,
        deletedFlag: Boolean = false
    ) {
        findAllNodesUnderFolder(
            artifactInfo.projectId,
            artifactInfo.repoName,
            artifactInfo.getArtifactFullPath(),
            deleted = deleted
        ).forEach {
            updateCache(
                projectId = artifactInfo.projectId,
                repoName = artifactInfo.repoName,
                fullPath = it.fullPath.getFolderPath(),
                size = it.size,
                deleted = deletedFlag
            )
        }
    }

    private fun String.getFolderPath(): String {
        val path = PathUtils.resolveParent(this)
        return PathUtils.normalizeFullPath(path)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeModifyEventListener::class.java)
        private const val FIXED_DELAY = 30000L
    }
}