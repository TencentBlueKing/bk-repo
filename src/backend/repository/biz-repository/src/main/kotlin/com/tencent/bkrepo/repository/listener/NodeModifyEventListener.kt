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

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.REPORT
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.artifact.event.node.NodeCopiedEvent
import com.tencent.bkrepo.common.artifact.event.node.NodeMovedEvent
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.service.otel.util.AsyncUtils.trace
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.dao.repository.FolderStatRepository
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.service.node.NodeService
import com.tencent.bkrepo.repository.util.NodeQueryHelper
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit


/**
 * 节点事件监听，用于folder size统计
 */
@Component
class NodeModifyEventListener(
    private val mongoTemplate: MongoTemplate,
    private val nodeService: NodeService,
    private val nodeDao: NodeDao,
    private val folderStatRepository: FolderStatRepository
    )  {

    private val executors = run {
        val namedThreadFactory = ThreadFactoryBuilder().setNameFormat("nodeModify-worker-%d").build()
        ThreadPoolExecutor(
            1, 5, 30, TimeUnit.SECONDS,
            LinkedBlockingQueue(8192), namedThreadFactory, ThreadPoolExecutor.CallerRunsPolicy()
        )
    }


    // 节点下载后将节点信息放入缓存，当缓存失效时更新accessDate
    private val cache: Cache<Triple<String, String, String>, LocalDateTime> = CacheBuilder.newBuilder()
        .maximumSize(10000)
        .expireAfterAccess(30, TimeUnit.SECONDS)
        .removalListener<Triple<String, String, String>, LocalDateTime> {
            executors.execute(
                Runnable {
                    updateFolderSize(
                        projectId = it.key!!.first,
                        repoName = it.key!!.second,
                        folderPath = it.key!!.third,
                    )
                }.trace()
            )
        }
        .build()

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
        if (event.repoName == REPORT || event.repoName == LOG_REPO) return
        updateModifiedFolderCache(event)
    }


    /**
     * 将有变更的目录节点存放在缓存中
     */
    private fun updateModifiedFolderCache(event: ArtifactEvent) {
        val (artifactInfo, originalArtifactInfo, deleted) = when (event.type) {
            EventType.NODE_MOVED -> {
                val movedEvent = event as NodeMovedEvent
                val artifactInfo = ArtifactInfo(
                    projectId = movedEvent.dstProjectId,
                    repoName = movedEvent.dstRepoName,
                    artifactUri = movedEvent.dstFullPath
                )
                val originalArtifactInfo = ArtifactInfo(
                    projectId = movedEvent.projectId,
                    repoName = movedEvent.repoName,
                    artifactUri = movedEvent.resourceKey
                )
                Triple(artifactInfo, originalArtifactInfo, false)
            }
            EventType.NODE_DELETED -> {
                val artifactInfo = ArtifactInfo(
                    projectId = event.projectId,
                    repoName = event.repoName,
                    artifactUri = event.resourceKey
                )
                findModifiedFolder(
                    artifactInfo = artifactInfo,
                    deleted = true
                )
                Triple(artifactInfo, null, true)
            }
            EventType.NODE_COPIED -> {
                val copyEvent = event as NodeCopiedEvent
                val artifactInfo = ArtifactInfo(
                    projectId = copyEvent.dstProjectId,
                    repoName = copyEvent.dstRepoName,
                    artifactUri = copyEvent.dstFullPath
                )
                Triple(artifactInfo, null, false)
            }
            EventType.NODE_CREATED -> {
                val artifactInfo = ArtifactInfo(
                    projectId = event.projectId,
                    repoName = event.repoName,
                    artifactUri = event.resourceKey
                )
                Triple(artifactInfo, null, false)
            }
            else -> throw UnsupportedOperationException()
        }
        findModifiedFolder(
            artifactInfo = artifactInfo,
            deleted = deleted,
            originalArtifactInfo = originalArtifactInfo
        )
    }

    private fun findModifiedFolder(
        artifactInfo: ArtifactInfo,
        originalArtifactInfo: ArtifactInfo? = null,
        deleted: Boolean = false
    ) {
        val node = if (deleted) {
            nodeService.getDeletedNodeDetail(artifactInfo).firstOrNull()
        } else {
            nodeService.getNodeDetail(artifactInfo)
        } ?: return


        if (node.folder) {
            findAndCacheSubFolders(
                artifactInfo = artifactInfo,
                originalArtifactInfo = originalArtifactInfo,
                deleted = node.nodeInfo.deleted
            )
        } else {
            updateCache(
                projectId = artifactInfo.projectId,
                repoName = artifactInfo.repoName,
                fullPath = artifactInfo.getArtifactFullPath()
            )
            if (originalArtifactInfo != null) {
                updateCache(
                    projectId = originalArtifactInfo.projectId,
                    repoName = originalArtifactInfo.repoName,
                    fullPath = originalArtifactInfo.getArtifactFullPath()
                )
            }
        }
    }




    private fun updateFolderSize(
        projectId: String,
        repoName: String,
        folderPath: String
    ) {
        val folderRealTimeSize = computeFolderSize(projectId, repoName, folderPath)
        if (folderRealTimeSize == null) {
            folderStatRepository.removeFolderStat(projectId, repoName, folderPath)
        } else {
            folderStatRepository.updateFolderSize(projectId, repoName, folderPath, folderRealTimeSize)
        }
    }


    private fun buildNodeQuery(projectId: String, repoName: String, fullPath: String): Criteria {
        return where(TNode::projectId).isEqualTo(projectId)
            .and(TNode::repoName).isEqualTo(repoName)
            .and(TNode::path).isEqualTo(fullPath+StringPool.SLASH)
            .and(TNode::deleted).isEqualTo(null)
    }

    private fun aggregateComputeSize(criteria: Criteria, collectionName: String): Long? {
        val aggregation = Aggregation.newAggregation(
            Aggregation.match(criteria),
            Aggregation.group().sum(TNode::size.name).`as`(TNode::size.name)
        )
        val aggregateResult = mongoTemplate.aggregate(aggregation, collectionName, HashMap::class.java)
        return aggregateResult.mappedResults.firstOrNull()?.get(TNode::size.name) as? Long
    }

    private fun computeFolderSize(
        projectId: String,
        repoName: String,
        fullPath: String
    ): Long? {
        val folderSizeQuery = buildNodeQuery(projectId, repoName, fullPath)
        val nodeCollectionName = COLLECTION_NODE_PREFIX + shardingSequence(projectId, SHARDING_COUNT)
        return aggregateComputeSize(folderSizeQuery, nodeCollectionName)
    }

    /**
     * 计算所在分表序号
     */
    private fun shardingSequence(value: Any, shardingCount: Int): Int {
        val hashCode = value.hashCode()
        return hashCode and shardingCount - 1
    }

    private fun updateCache(
        projectId: String,
        repoName: String,
        fullPath: String,
        folder: Boolean = false
    ) {
        val folderPath = if (folder) {
            fullPath
        } else {
            var parentPath = PathUtils.resolveParent(fullPath)
            if (parentPath != StringPool.SLASH) {
                parentPath = parentPath.removeSuffix(StringPool.SLASH)
            }
            parentPath
        }
        cache.put(Triple(projectId, repoName, folderPath), LocalDateTime.now())
    }

    private fun findSubFolders(
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
                .and(TNode::folder).isEqualTo(true)
                .and(TNode::fullPath).regex("^${PathUtils.escapeRegex(srcRootNodePath)}")
            Query(criteria)
        } else {
            val listOption = NodeListOption(includeFolder = true, deep = true, sort = true)
            NodeQueryHelper.nodeListQuery(projectId, repoName, srcRootNodePath, listOption)
        }
        return nodeDao.find(query)
    }

    private fun findAndCacheSubFolders(
        artifactInfo: ArtifactInfo,
        originalArtifactInfo: ArtifactInfo? = null,
        deleted: String? = null
    ) {
        findSubFolders(
            artifactInfo.projectId,
            artifactInfo.repoName,
            artifactInfo.getArtifactFullPath(),
            deleted
        ).forEach {
            if (it.folder) {
                updateCache(
                    projectId = artifactInfo.projectId,
                    repoName = artifactInfo.repoName,
                    fullPath = it.fullPath,
                    folder = true
                )
                if (originalArtifactInfo != null) {
                    updateCache(
                        projectId = originalArtifactInfo.projectId,
                        repoName = originalArtifactInfo.repoName,
                        fullPath = it.fullPath,
                        folder = true
                    )
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeModifyEventListener::class.java)
        private const val SHARDING_COUNT = 256
        private const val COLLECTION_NODE_PREFIX = "node_"
        private const val LOG_REPO = "log"
    }
}