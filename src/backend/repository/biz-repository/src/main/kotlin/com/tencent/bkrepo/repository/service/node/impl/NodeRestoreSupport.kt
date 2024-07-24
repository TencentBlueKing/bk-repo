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
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.repository.service.node.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils.isRoot
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.fs.server.constant.FAKE_SHA256
import com.tencent.bkrepo.fs.server.constant.FS_ATTR_KEY
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.node.ConflictStrategy
import com.tencent.bkrepo.repository.pojo.node.NodeDeletedPoint
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.NodeRestoreOption
import com.tencent.bkrepo.repository.pojo.node.NodeRestoreResult
import com.tencent.bkrepo.repository.service.node.NodeRestoreOperation
import com.tencent.bkrepo.repository.service.node.impl.NodeBaseService.Companion.convertToDetail
import com.tencent.bkrepo.repository.util.MetadataUtils
import com.tencent.bkrepo.repository.util.NodeQueryHelper
import com.tencent.bkrepo.repository.util.NodeQueryHelper.nodeDeletedFolderQuery
import com.tencent.bkrepo.repository.util.NodeQueryHelper.nodeDeletedPointListQuery
import com.tencent.bkrepo.repository.util.NodeQueryHelper.nodeDeletedPointListQueryBySha256
import com.tencent.bkrepo.repository.util.NodeQueryHelper.nodeDeletedPointQuery
import com.tencent.bkrepo.repository.util.NodeQueryHelper.nodeListQuery
import com.tencent.bkrepo.repository.util.NodeQueryHelper.nodeQuery
import com.tencent.bkrepo.repository.util.NodeQueryHelper.nodeRestoreUpdate
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.FindAndModifyOptions
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 节点统计接口实现
 */
open class NodeRestoreSupport(
    nodeBaseService: NodeBaseService
) : NodeRestoreOperation {

    val nodeDao: NodeDao = nodeBaseService.nodeDao
    val blockNodeService: BlockNodeService = nodeBaseService.blockNodeService

    override fun getDeletedNodeDetail(artifact: ArtifactInfo): List<NodeDetail> {
        with(artifact) {
            val query = nodeDeletedPointListQuery(projectId, repoName, getArtifactFullPath())
            val deletedNode = nodeDao.findOne(query)
            return if (deletedNode == null) {
                emptyList()
            } else {
                listOf(convertToDetail(deletedNode)!!)
            }
        }
    }

    override fun getDeletedNodeDetailBySha256(projectId: String, repoName: String, sha256: String): NodeDetail? {
        val query = nodeDeletedPointListQueryBySha256(projectId, repoName, sha256)
        val node = nodeDao.findOne(query)
        return convertToDetail(node)
    }

    override fun restoreNode(artifact: ArtifactInfo, nodeRestoreOption: NodeRestoreOption): NodeRestoreResult {
        with(resolveContext(artifact, nodeRestoreOption)) {
            return restoreNode(this)
        }
    }

    /**
     * 查询节点是否存在，需要考虑根节点
     */
    private fun findDeletedNode(context: RestoreContext): TNode? {
        with(context) {
            if (isRoot(rootFullPath)) {
                return NodeDao.buildRootNode(projectId, repoName).apply {
                    deleted = deletedTime
                }
            }
            val query = nodeDeletedPointQuery(projectId, repoName, rootFullPath, deletedTime)
            return nodeDao.findOne(query)
        }
    }

    override fun listDeletedPoint(artifact: ArtifactInfo): List<NodeDeletedPoint> {
        with(artifact) {
            val query = nodeDeletedPointListQuery(projectId, repoName, getArtifactFullPath())
            return nodeDao.find(query).map { convert(it) }
        }
    }

    override fun restoreNode(restoreContext: RestoreContext): NodeRestoreResult {
        with(restoreContext) {
            findDeletedNode(this)?.let {
                restore(this, it)
            }

            val result = NodeRestoreResult(
                fullPath = rootFullPath,
                restoreCount = restoreCount,
                skipCount = skipCount,
                conflictCount = conflictCount
            )
            logger.info("Success to restore $rootFullPath: $result.")
            return result
        }
    }

    /**
     * 恢复节点[node]
     */
    private fun restore(context: RestoreContext, node: TNode) {
        with(context) {
            if (!isRoot(node.fullPath)) {
                resolveConflict(this, node)
            }
            // 如果是文件夹，继续恢复子节点
            if (node.folder) {
                restoreFolder(this, node)
            }
        }
    }

    /**
     * 处理冲突并恢复节点，根据不同的冲突策略采取不同的方式
     */
    open fun resolveConflict(context: RestoreContext, node: TNode) {
        with(context) {
            val fullPath = node.fullPath
            if (node.deleted == null || nodeDao.exists(projectId, repoName, fullPath)) {
                when (conflictStrategy) {
                    ConflictStrategy.SKIP -> {
                        skipCount += 1
                        return
                    }
                    ConflictStrategy.OVERWRITE -> {
                        deleteExistedNode(node, operator)
                        conflictCount += 1
                    }
                    ConflictStrategy.FAILED -> throw ErrorCodeException(ArtifactMessageCode.NODE_CONFLICT, fullPath)
                }
            }
            val query = nodeDeletedPointQuery(projectId, repoName, fullPath, deletedTime)
            val option = FindAndModifyOptions()
            option.returnNew(false)
            val deletedNode = nodeDao.findAndModify(query, nodeRestoreUpdate(), option, TNode::class.java)
            if (deletedNode?.sha256 == FAKE_SHA256 || deletedNode?.metadata?.find { it.key == FS_ATTR_KEY } != null) {
                try {
                    runBlocking {
                        blockNodeService.restoreBlocks(
                            projectId = projectId,
                            repoName = repoName,
                            fullPath = fullPath,
                            nodeCreateDate = deletedNode.createdDate,
                            nodeDeleteDate = deletedNode.deleted!!
                        )
                    }
                } catch (e: Exception) {
                    logger.error("restore block resources failed: $projectId/$repoName/$fullPath", e)
                    nodeDao.save(deletedNode)
                    return
                }
            }
            restoreCount += if (deletedNode != null) 1 else 0
        }
    }

    private fun deleteExistedNode(
        node: TNode,
        userId: String
    ) {
        with(node) {
            val query = nodeQuery(projectId, repoName, fullPath)
            if (node.sha256 == FAKE_SHA256 || node.metadata?.find { it.key == FS_ATTR_KEY } != null) {
                runBlocking { blockNodeService.deleteBlocks(projectId, repoName, fullPath) }
            }
            nodeDao.updateFirst(query, NodeQueryHelper.nodeDeleteUpdate(userId))
        }
    }

    /**
     * 恢复目录下的子节点，调用时目录本身已经恢复
     */
    private fun restoreFolder(context: RestoreContext, node: TNode) {
        // 先查询是否存在子节点
        val option = NodeListOption(
            includeFolder = true,
            includeMetadata = false,
            deep = false,
            sort = false
        )
        val query = nodeListQuery(context.projectId, context.repoName, node.fullPath, option)
        try {
            if (nodeDao.count(query) == 0L) {
                // 不存在子节点则使用快速方式恢复
                fastRestoreFolder(context, node)
            } else {
                // 否则使用遍历方式恢复
                loopRestoreFolder(context, node)
            }
        } catch (exception: DuplicateKeyException) {
            // 有新节点冲突，使用遍历方式
            logger.warn("Try to fast restore failed, use loop restore.", exception)
            loopRestoreFolder(context, node)
        }
    }

    /**
     * 快速恢复目录
     */
    private fun fastRestoreFolder(context: RestoreContext, node: TNode) {
        with(context) {
            val query = nodeDeletedFolderQuery(projectId, repoName, node.fullPath, deletedTime, true)
            val updateResult = nodeDao.updateMulti(query, nodeRestoreUpdate())
            restoreCount += updateResult.modifiedCount
        }
    }

    /**
     * 遍历节点方式恢复目录
     */
    private fun loopRestoreFolder(context: RestoreContext, node: TNode) {
        with(context) {
            val query = nodeDeletedFolderQuery(projectId, repoName, node.fullPath, deletedTime, false)
            nodeDao.find(query).forEach {
                restore(context, it)
            }
        }
    }

    private fun resolveContext(artifact: ArtifactInfo, option: NodeRestoreOption): RestoreContext {
        return RestoreContext(
            projectId = artifact.projectId,
            repoName = artifact.repoName,
            rootFullPath = artifact.getArtifactFullPath(),
            deletedTime = Instant.ofEpochMilli(option.deletedId).atZone(ZoneId.systemDefault()).toLocalDateTime(),
            conflictStrategy = option.conflictStrategy,
            operator = SecurityUtils.getUserId()
        )
    }

    private fun convert(node: TNode): NodeDeletedPoint {
        return node.let {
            NodeDeletedPoint(
                id = it.deleted!!.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                fullPath = it.fullPath,
                size = it.size,
                sha256 = it.sha256,
                metadata = MetadataUtils.toMap(it.metadata),
                deletedBy = it.lastModifiedBy,
                deletedTime = it.deleted!!
            )
        }
    }

    data class RestoreContext(
        val projectId: String,
        val repoName: String,
        val rootFullPath: String,
        val deletedTime: LocalDateTime,
        val conflictStrategy: ConflictStrategy,
        val operator: String,
        var restoreCount: Long = 0L,
        var conflictCount: Long = 0L,
        var skipCount: Long = 0L
    )

    companion object {
        private val logger = LoggerFactory.getLogger(NodeRestoreSupport::class.java)
    }
}
