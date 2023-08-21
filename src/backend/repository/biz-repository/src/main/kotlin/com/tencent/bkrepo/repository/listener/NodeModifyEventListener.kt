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
import com.tencent.bkrepo.common.artifact.event.node.NodeCreatedEvent
import com.tencent.bkrepo.common.artifact.event.node.NodeDeletedEvent
import com.tencent.bkrepo.common.artifact.event.node.NodeMovedEvent
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.service.node.NodeService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit


/**
 * 节点事件监听，用户统计目录size以及目录下文件个数
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
            nodeDao.incSizeAndNodeNumOfFolder(
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


    @EventListener(ArtifactEvent::class)
    fun handle(event: ArtifactEvent) {
        if (!acceptTypes.contains(event.type)) {
            return
        }
        if (ignoreProjectOrRepoCheck(event.projectId, event.repoName)) return
        try {
            updateCacheOfModifiedFolder(event)
        } catch (ignore: Exception) {
            logger.warn("update folder cache error: ${ignore.message}")
        }
    }


    /**
     * 定时将缓存中的数据更新到db中
     */
    @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = FIXED_DELAY)
    fun storeFolderData() {
        cache.invalidateAll()
    }

    /**
     * 判断项目或者仓库是否不需要进行目录统计
     */
    private fun ignoreProjectOrRepoCheck(projectId: String, repoName: String): Boolean {
        IGNORE_PROJECT_PREFIX_LIST.forEach {
            if (projectId.startsWith(it)){
                return true
            }
        }
        return IGNORE_REPO_LIST.contains(repoName)
    }



    /**
     * 将变更的目录节点数据存放在缓存中
     */
    private fun updateCacheOfModifiedFolder(event: ArtifactEvent) {
        logger.info("event type ${event.type}")
        val modifiedNodeList = mutableListOf<ModifiedNodeInfo>()
        when (event.type) {
            EventType.NODE_MOVED -> {
                require(event is NodeMovedEvent)
                val dstFullPath = buildDstFullPath(event.dstFullPath, event.resourceKey)
                val createdNode = ModifiedNodeInfo(
                    projectId = event.dstProjectId,
                    repoName = event.dstRepoName,
                    fullPath = dstFullPath
                )
                val deletedNode = ModifiedNodeInfo(
                    projectId = event.projectId,
                    repoName = event.repoName,
                    fullPath = event.resourceKey,
                    deleted = true
                )
                modifiedNodeList.add(createdNode)
                modifiedNodeList.add(deletedNode)
            }
            EventType.NODE_DELETED -> {
                require(event is NodeDeletedEvent)
                val deletedNode = ModifiedNodeInfo(
                    projectId = event.projectId,
                    repoName = event.repoName,
                    fullPath = event.resourceKey,
                    deleted = true
                )
                modifiedNodeList.add(deletedNode)
            }
            EventType.NODE_COPIED -> {
                require(event is NodeCopiedEvent)
                val dstFullPath = buildDstFullPath(event.dstFullPath, event.resourceKey)
                val createdNode = ModifiedNodeInfo(
                    projectId = event.dstProjectId,
                    repoName = event.dstRepoName,
                    fullPath = dstFullPath
                )
                modifiedNodeList.add(createdNode)
            }
            EventType.NODE_CREATED -> {
                require(event is NodeCreatedEvent)
                val createdNode = ModifiedNodeInfo(
                    projectId = event.projectId,
                    repoName = event.repoName,
                    fullPath = event.resourceKey
                )
                modifiedNodeList.add(createdNode)
            }
            else -> throw UnsupportedOperationException()
        }
        modifiedNodeList.forEach {
            findFoldersAndUpdateCache(it)
        }
    }

    private fun findFoldersAndUpdateCache(modifiedNode: ModifiedNodeInfo) {
        val artifactInfo = ArtifactInfo(
            projectId = modifiedNode.projectId,
            repoName = modifiedNode.repoName,
            artifactUri = modifiedNode.fullPath
        )
        val node = if (modifiedNode.deleted) {
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
                deletedFlag = modifiedNode.deleted
            )
        } else {
            updateCache(
                projectId = artifactInfo.projectId,
                repoName = artifactInfo.repoName,
                fullPath = artifactInfo.getArtifactFullPath(),
                size = node.size,
                deleted = modifiedNode.deleted
            )
        }
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
            if (it != PathUtils.ROOT) {
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


    private fun findAllNodesUnderFolder(
        projectId: String,
        repoName: String,
        fullPath: String,
        deleted: String? = null
    ): List<TNode> {
        val srcRootNodePath = PathUtils.toPath(fullPath)
        val query = buildNodeQuery(projectId, repoName, srcRootNodePath, deleted)
        return nodeDao.find(query)
    }


    /**
     * 查询目录下的节点，排除path为"/"的节点
     */
    private fun buildNodeQuery(
        projectId: String,
        repoName: String,
        srcRootNodePath: String,
        deleted: String? = null
    ): Query {
        val criteria = where(TNode::projectId).isEqualTo(projectId)
        .and(TNode::repoName).isEqualTo(repoName)
            .apply {
                if (deleted.isNullOrEmpty()) {
                    this.and(TNode::deleted).isEqualTo(null)
                } else {
                    // 节点删除时其下所有节点的deleted值是一致的，但是节点move时其下所有节点的deleted是不一致的
                    this.and(TNode::deleted).gte(LocalDateTime.parse(deleted))
                }
            }
        .and(TNode::fullPath).regex("^${PathUtils.escapeRegex(srcRootNodePath)}")
        .and(TNode::folder).isEqualTo(false)
        .and(TNode::path).ne(PathUtils.ROOT)
        return Query(criteria).withHint(TNode.FULL_PATH_IDX)
    }


    private fun buildDstFullPath(dstFullPath: String, srcFullPath: String): String {
        val path = PathUtils.toPath(dstFullPath)
        val name = PathUtils.resolveName(srcFullPath)
        return PathUtils.combineFullPath(path, name)
    }

    private fun String.getFolderPath(): String {
        val path = PathUtils.resolveParent(this)
        return PathUtils.normalizeFullPath(path)
    }

    private data class ModifiedNodeInfo(
        var projectId: String,
        var repoName: String,
        var fullPath: String,
        var deleted: Boolean = false
    )

    companion object {
        private val logger = LoggerFactory.getLogger(NodeModifyEventListener::class.java)
        private const val FIXED_DELAY = 30000L
        private val IGNORE_PROJECT_PREFIX_LIST = listOf("CODE_", "CLOSED_SOURCE_", "git_")
        private val IGNORE_REPO_LIST = listOf(REPORT, LOG)
    }
}