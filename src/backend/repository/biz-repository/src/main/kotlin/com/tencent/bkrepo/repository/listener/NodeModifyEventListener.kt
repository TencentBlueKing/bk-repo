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
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.cache.RemovalCause
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.artifact.event.node.NodeCleanEvent
import com.tencent.bkrepo.common.artifact.event.node.NodeCopiedEvent
import com.tencent.bkrepo.common.artifact.event.node.NodeCreatedEvent
import com.tencent.bkrepo.common.artifact.event.node.NodeDeletedEvent
import com.tencent.bkrepo.common.artifact.event.node.NodeMovedEvent
import com.tencent.bkrepo.common.artifact.event.node.NodeRenamedEvent
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.path.PathUtils.combineFullPath
import com.tencent.bkrepo.common.mongo.dao.AbstractMongoDao
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.service.node.NodeService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder


/**
 * 节点事件监听，用户统计目录size以及目录下文件个数
 */
@Component
class NodeModifyEventListener(
    private val nodeService: NodeService,
    private val nodeDao: NodeDao,
    )  {

    private val cache: LoadingCache<Triple<String, String, String>, Pair<LongAdder, LongAdder>>
    = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .removalListener<Triple<String, String, String>, Pair<LongAdder, LongAdder>> {
            if (it.cause == RemovalCause.REPLACED) return@removalListener
            logger.info("remove ${it.key}, ${it.value}, cause ${it.cause}, Thread ${Thread.currentThread().name}")
            nodeDao.incSizeAndNodeNumOfFolder(
                projectId = it.key!!.first,
                repoName = it.key!!.second,
                fullPath = it.key!!.third,
                size = it.value.first.sumThenReset(),
                nodeNum = it.value.second.sumThenReset()
            )
        }
        .build(CacheLoader.from { _ -> Pair(LongAdder(), LongAdder()) })

    /**
     * 允许接收的事件类型
     */
    private val acceptTypes = setOf(
        EventType.NODE_COPIED,
        EventType.NODE_CREATED,
        EventType.NODE_DELETED,
        EventType.NODE_MOVED,
        EventType.NODE_RENAMED,
        EventType.NODE_CLEAN,
    )


    @Async
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
        return false
    }



    /**
     * 将变更的目录节点数据存放在缓存中
     */
    private fun updateCacheOfModifiedFolder(event: ArtifactEvent) {
        logger.info("event $event")
        val modifiedNodeList = mutableListOf<ModifiedNodeInfo>()
        when (event.type) {
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
            EventType.NODE_CREATED -> {
                require(event is NodeCreatedEvent)
                val createdNode = ModifiedNodeInfo(
                    projectId = event.projectId,
                    repoName = event.repoName,
                    fullPath = event.resourceKey
                )
                modifiedNodeList.add(createdNode)
            }
            EventType.NODE_RENAMED -> {
                require(event is NodeRenamedEvent)
                // 节点重命名逻辑和其他操作不同，它会对旧节点下的目录删除，然后新建，但是对于非目录节点是进行更新动作，而不是删除再新建
                // 节点重命名操作只需要更新该节点下的子目录的统计信息，不需要更新其上层目录统计信息
                val renamedNode = ModifiedNodeInfo(
                    projectId = event.projectId,
                    repoName = event.repoName,
                    fullPath = event.newFullPath,
                    includePrefix = event.newFullPath
                )
                modifiedNodeList.add(renamedNode)
            }
            EventType.NODE_MOVED -> {
                require(event is NodeMovedEvent)
                // 1 move空目录，2 move到已存在目录, 3 move到新目录 4 同路径，跳过  5 src为dst目录下的子节点，跳过
                // 针对1 2 两种情况，需要判断原目录中的节点，然后再进行目标目录的统计信息更新
                val createdNode = ModifiedNodeInfo(
                    projectId = event.dstProjectId,
                    repoName = event.dstRepoName,
                    fullPath = event.dstFullPath,
                    srcProjectId = event.projectId,
                    srcRepoName = event.repoName,
                    srcFullPath = event.resourceKey,
                    srcDeleted = true
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
            EventType.NODE_COPIED -> {
                require(event is NodeCopiedEvent)
                // 1 copy空目录， 2 copy到已存在目录，3 copy到新目录  4 同路径，跳过  5 src为dst目录下的子节点，跳过
                // 针对1 2 两种情况，需要判断原目录中的节点，然后再进行目标目录的统计信息更新
                val createdNode = ModifiedNodeInfo(
                    projectId = event.dstProjectId,
                    repoName = event.dstRepoName,
                    fullPath = event.dstFullPath,
                    srcProjectId = event.projectId,
                    srcRepoName = event.repoName,
                    srcFullPath = event.resourceKey,
                )
                modifiedNodeList.add(createdNode)
            }
            EventType.NODE_CLEAN -> {
                require(event is NodeCleanEvent)
                val deletedNode = ModifiedNodeInfo(
                    projectId = event.projectId,
                    repoName = event.repoName,
                    fullPath = event.resourceKey,
                    deleted = true,
                    deletedDateTime = event.deletedDate
                )
                modifiedNodeList.add(deletedNode)
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
            var temp = nodeService.getDeletedNodeDetail(artifactInfo).firstOrNull()
            // 节点清理只会清理目录下指定时间之前的节点，目录不会被清理
            if (temp == null && !modifiedNode.deletedDateTime.isNullOrEmpty()) {
                temp = nodeService.getNodeDetail(artifactInfo)
            }
            temp ?: return
        } else {
            // 查询节点信息，当节点新增，然后删除后可能会找不到节点
            nodeService.getNodeDetail(artifactInfo)
                ?: nodeService.getDeletedNodeDetail(artifactInfo).firstOrNull() ?: return
        }

        logger.info("start to stat modified node size with fullPath ${node.fullPath}" +
                        " in repo ${node.projectId}|${node.repoName}")
        if (node.folder) {
            val sourceNodes = filterSourceNodesFromMoveOrCopy(modifiedNode)
            logger.info("the size of node ${modifiedNode.srcFullPath} is ${sourceNodes?.size}" +
                            " in repo ${modifiedNode.srcProjectId}|${modifiedNode.srcRepoName}")
            if (sourceNodes != null && sourceNodes.isEmpty()) return
            findAndCacheSubFolders(
                artifactInfo = artifactInfo,
                deleted = modifiedNode.deletedDateTime ?: node.nodeInfo.deleted,
                deletedFlag = modifiedNode.deleted,
                includePrefix = modifiedNode.includePrefix,
                sourceNodes = sourceNodes
            )
        } else {
            updateCache(
                projectId = artifactInfo.projectId,
                repoName = artifactInfo.repoName,
                fullPath = artifactInfo.getArtifactFullPath(),
                size = node.size,
                deleted = modifiedNode.deleted,
                includePrefix = modifiedNode.includePrefix
            )
        }
    }

    /**
     * 更新缓存
     * 当要更新包含该文件所有的目录的缓存记录时includePrefix为空
     * 当只需要更新特定目录前缀目录的缓存记录时设置includePrefix
     */
    private fun updateCache(
        projectId: String,
        repoName: String,
        fullPath: String,
        size: Long,
        deleted: Boolean = false,
        includePrefix: String? = null
    ) {

        // 更新当前节点所有上级目录统计信息
        val folderPaths = PathUtils.resolveAncestorFolder(fullPath)
        folderPaths.forEach { it ->
            if (it == PathUtils.ROOT) return@forEach
            // 当只需要更新特定目录前缀目录的缓存记录时设置folderPrefix
            if (!includePrefix.isNullOrEmpty() && !it.startsWith(includePrefix)) return@forEach
            val key = Triple(projectId, repoName, it)
            val (cachedSize, nodeNum) = cache.get(key)
            if (deleted) {
                cachedSize.add(-1 * size)
                nodeNum.decrement()
            } else {
                cachedSize.add(size)
                nodeNum.increment()
            }
        }
    }

    private fun findAndCacheSubFolders(
        artifactInfo: ArtifactInfo,
        deleted: String? = null,
        deletedFlag: Boolean = false,
        includePrefix: String? = null,
        sourceNodes: List<String>? = null
    ) {
        val action: ((List<TNode>) -> Unit) = {  nodeList ->
            nodeList.forEach {
                if (!sourceNodes.isNullOrEmpty() && !sourceNodes.contains(it.fullPath)) return@forEach
                updateCache(
                    projectId = artifactInfo.projectId,
                    repoName = artifactInfo.repoName,
                    fullPath = it.fullPath,
                    size = it.size,
                    deleted = deletedFlag,
                    includePrefix = includePrefix
                )
            }
        }
        findAllNodesUnderFolder(
            artifactInfo.projectId,
            artifactInfo.repoName,
            artifactInfo.getArtifactFullPath(),
            deleted = deleted,
            action = action
        )
    }


    private fun findAllNodesUnderFolder(
        projectId: String,
        repoName: String,
        fullPath: String,
        deleted: String? = null,
        action: (List<TNode>) -> Unit
    ) {
        val srcRootNodePath = PathUtils.toPath(fullPath)
        val query = buildNodeQuery(projectId, repoName, srcRootNodePath, deleted)
        var nodes: List<TNode>
        var pageNumber = DEFAULT_PAGE_NUMBER
        do {
            val pageRequest = Pages.ofRequest(pageNumber, 1000)
            query.with(pageRequest).with(Sort.by(AbstractMongoDao.ID).ascending())
            nodes = nodeDao.find(query)
            action(nodes)
            pageNumber++
        } while (nodes.isNotEmpty())
    }


    /**
     * 针对move/copy情况下目标节点是目录的情况下，过滤出变更的节点信息
     * 可能情况：1 源节点为空目录 2 目标节点为已存在的目录，其下可能已经包含文件
     */
    private fun filterSourceNodesFromMoveOrCopy(modifiedNode: ModifiedNodeInfo): List<String>? {
        if (modifiedNode.srcFullPath.isNullOrEmpty()) return null
        val artifactInfo = ArtifactInfo(
            projectId = modifiedNode.srcProjectId!!,
            repoName = modifiedNode.srcRepoName!!,
            artifactUri = modifiedNode.srcFullPath!!
        )
        val sourceNodes = mutableListOf<String>()
        val node = if (modifiedNode.srcDeleted) {
            nodeService.getDeletedNodeDetail(artifactInfo).firstOrNull() ?: return emptyList()
        } else {
            // 查询节点信息，当节点新增，然后删除后可能会找不到节点
            nodeService.getNodeDetail(artifactInfo)
                ?: nodeService.getDeletedNodeDetail(artifactInfo).firstOrNull() ?: return emptyList()
        }
        val path = PathUtils.resolveParent(modifiedNode.srcFullPath!!)
        if (node.folder) {
            val action: ((List<TNode>) -> Unit) = {  nodeList ->
                nodeList.forEach {
                    sourceNodes.add(combineFullPath(modifiedNode.fullPath, it.fullPath.removePrefix(path)))
                }
            }
            findAllNodesUnderFolder(
                artifactInfo.projectId,
                artifactInfo.repoName,
                artifactInfo.getArtifactFullPath(),
                node.nodeInfo.deleted,
                action = action
            )
        } else {
            sourceNodes.add(combineFullPath(modifiedNode.fullPath, node.fullPath.removePrefix(path)))
        }
        return sourceNodes
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

    private data class ModifiedNodeInfo(
        var projectId: String,
        var repoName: String,
        var fullPath: String,
        var deleted: Boolean = false,
        // 针对重命名去过滤上层目录
        var includePrefix: String? = null,
        // 针对move/copy 目标节点是目录的情况下去判断来源节点信息
        var srcProjectId: String? = null,
        var srcRepoName: String? = null,
        var srcFullPath: String? = null,
        var srcDeleted: Boolean = false,
        var deletedDateTime: String? = null
    )

    companion object {
        private val logger = LoggerFactory.getLogger(NodeModifyEventListener::class.java)
        private const val FIXED_DELAY = 10000L
        private val IGNORE_PROJECT_PREFIX_LIST = listOf("CODE_", "CLOSED_SOURCE_")
    }
}