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

package com.tencent.bkrepo.common.metadata.service.node.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.path.PathUtils.combineFullPath
import com.tencent.bkrepo.common.artifact.path.PathUtils.resolveName
import com.tencent.bkrepo.common.artifact.path.PathUtils.resolveParent
import com.tencent.bkrepo.common.artifact.path.PathUtils.toPath
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.dao.repo.RepositoryDao
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.model.TRepository
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.metadata.service.node.NodeMoveCopyOperation
import com.tencent.bkrepo.common.metadata.service.repo.QuotaService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.metadata.util.NodeBaseServiceHelper.convertToDetail
import com.tencent.bkrepo.common.metadata.util.NodeBaseServiceHelper.fsNode
import com.tencent.bkrepo.common.metadata.util.NodeEventFactory
import com.tencent.bkrepo.common.metadata.util.NodeMoveCopyHelper
import com.tencent.bkrepo.common.metadata.util.NodeMoveCopyHelper.MoveCopyContext
import com.tencent.bkrepo.common.metadata.util.NodeMoveCopyHelper.buildDstBlockNode
import com.tencent.bkrepo.common.metadata.util.NodeMoveCopyHelper.buildDstNode
import com.tencent.bkrepo.common.metadata.util.NodeMoveCopyHelper.canIgnore
import com.tencent.bkrepo.common.metadata.util.NodeMoveCopyHelper.preCheck
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper
import com.tencent.bkrepo.common.service.util.SpringContextUtils.Companion.publishEvent
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.constant.DEFAULT_STORAGE_CREDENTIALS_KEY
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Query
import java.time.format.DateTimeFormatter

/**
 * 节点移动/拷贝接口实现
 */
open class NodeMoveCopySupport(
    private val nodeBaseService: NodeBaseService,
) : NodeMoveCopyOperation {

    private val nodeDao: NodeDao = nodeBaseService.nodeDao
    private val repositoryDao: RepositoryDao = nodeBaseService.repositoryDao
    private val storageCredentialService: StorageCredentialService = nodeBaseService.storageCredentialService
    private val quotaService: QuotaService = nodeBaseService.quotaService
    private val blockNodeService: BlockNodeService = nodeBaseService.blockNodeService

    override fun moveNode(moveRequest: NodeMoveCopyRequest): NodeDetail {
        val dstNode = moveCopy(moveRequest, true)
        logger.info("Move node success: [$moveRequest]")
        return dstNode
    }

    override fun copyNode(copyRequest: NodeMoveCopyRequest): NodeDetail {
        val dstNode = moveCopy(copyRequest, false)
        logger.info("Copy node success: [$copyRequest]")
        return dstNode
    }

    /**
     * 处理节点操作请求
     */
    private fun moveCopy(request: NodeMoveCopyRequest, move: Boolean): NodeDetail {
        with(resolveContext(request, move)) {
            preCheck(this)
            if (canIgnore(this)) {
                return convertToDetail(
                    nodeBaseService.nodeDao.findNode(
                        projectId = dstProjectId,
                        repoName = dstRepoName,
                        fullPath = dstFullPath
                    )
                )!!
            }
            if (srcNode.folder) {
                moveCopyFolder(this)
            } else {
                moveCopyFile(this)
            }
            if (move) {
                publishEvent(NodeEventFactory.buildMovedEvent(request))
            } else {
                publishEvent(NodeEventFactory.buildCopiedEvent(request))
            }
            return convertToDetail(
                nodeBaseService.nodeDao.findNode(
                    projectId = dstProjectId,
                    repoName = dstRepoName,
                    fullPath = dstFullPath
                )
            )!!
        }
    }

    /**
     * 移动/复制节点
     */
    protected fun doMoveCopy(
        context: MoveCopyContext,
        node: TNode,
        dstPath: String,
        dstName: String
    ) {
        with(context) {
            val dstFullPath = combineFullPath(dstPath, dstName)
            // 冲突检查
            val existNode = nodeDao.findNode(dstProjectId, dstRepoName, dstFullPath)
            // 目录 -> 目录: 跳过
            if (node.folder && existNode?.folder == true) return
            checkConflict(context, node, existNode)
            // copy目标节点
            val dstNode = buildDstNode(this, node, dstPath, dstName, dstFullPath)
            // 仓库配额检查
            checkQuota(context, node, existNode)

            // 文件 & 跨存储node
            if (!node.folder && srcCredentials != dstCredentials) {
                // 默认存储为null,所以需要使用一个默认key，以区分该节点是拷贝节点
                dstNode.copyFromCredentialsKey = srcNode.copyFromCredentialsKey
                    ?: srcCredentials?.key
                        ?: DEFAULT_STORAGE_CREDENTIALS_KEY
                dstNode.copyIntoCredentialsKey = dstCredentials?.key ?: DEFAULT_STORAGE_CREDENTIALS_KEY
            }
            // 创建dst block node，此处只需要复制，如果是move操作在src node被删除时对应的src block node也会一起删除
            copyBlockNode(context, node, dstFullPath)
            // 创建dst节点
            nodeBaseService.doCreate(dstNode, dstRepo)
            // move操作，创建dst节点后，还需要删除src节点
            // 因为分表所以不能直接更新src节点，必须创建新的并删除旧的
            if (move) {
                val query = NodeQueryHelper.nodeQuery(node.projectId, node.repoName, node.fullPath)
                val update = NodeQueryHelper.nodeDeleteUpdate(operator)
                if (!node.folder) {
                    quotaService.decreaseUsedVolume(node.projectId, node.repoName, node.size)
                }
                nodeDao.updateFirst(query, update)
            }
        }
    }

    private fun checkQuota(context: MoveCopyContext, node: TNode, existNode: TNode?) {
        // 目录不占仓库容量，不需要检查
        if (node.folder) return

        with(context) {
            // 文件 -> 文件，目标文件不存在
            if (existNode == null) {
                // 同仓库的移动操作不需要检查仓库已使用容量
                if (!(isSameRepo() && move)) {
                    quotaService.checkRepoQuota(dstProjectId, dstRepoName, node.size)
                }
            }

            // 文件 -> 文件 & 允许覆盖: 删除old
            if (existNode?.folder == false && overwrite) {
                quotaService.checkRepoQuota(existNode.projectId, existNode.repoName, node.size - existNode.size)
                nodeBaseService.deleteByFullPathWithoutDecreaseVolume(
                    existNode.projectId, existNode.repoName, existNode.fullPath, operator
                )
                quotaService.decreaseUsedVolume(existNode.projectId, existNode.repoName, existNode.size)
            }
        }
    }

    private fun resolveContext(request: NodeMoveCopyRequest, move: Boolean): MoveCopyContext {
        with(request) {
            val srcFullPath = PathUtils.normalizeFullPath(srcFullPath)
            val dstProjectId = request.destProjectId ?: srcProjectId
            val dstRepoName = request.destRepoName ?: srcRepoName
            val dstFullPath = PathUtils.normalizeFullPath(request.destFullPath)
            val isSameRepo = srcProjectId == destProjectId && srcRepoName == destRepoName
            // 查询repository
            val srcRepo = findRepository(srcProjectId, srcRepoName)
            val dstRepo = if (!isSameRepo) findRepository(dstProjectId, dstRepoName) else srcRepo
            // 查询storageCredentials
            val srcCredentials = findCredential(srcRepo.credentialsKey)
            val dstCredentials = if (!isSameRepo) findCredential(dstRepo.credentialsKey) else srcCredentials
            val srcNode = nodeDao.findNode(srcProjectId, srcRepoName, srcFullPath)
                ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, srcFullPath)
            val dstNode = nodeDao.findNode(dstProjectId, dstRepoName, dstFullPath)

            return MoveCopyContext(
                srcRepo = srcRepo,
                srcCredentials = srcCredentials,
                srcNode = srcNode,
                dstProjectId = dstProjectId,
                dstRepoName = dstRepoName,
                dstFullPath = dstFullPath,
                dstRepo = dstRepo,
                dstCredentials = dstCredentials,
                dstNode = dstNode,
                dstNodeFolder = destNodeFolder,
                overwrite = overwrite,
                operator = request.operator,
                move = move
            )
        }
    }

    open fun checkConflict(context: MoveCopyContext, node: TNode, existNode: TNode?) {
        NodeMoveCopyHelper.checkConflict(context, node, existNode)
    }

    /**
     * 移动/复制目录
     */
    private fun moveCopyFolder(context: MoveCopyContext) {
        with(context) {
            // 目录 -> 文件: error
            if (dstNode?.folder == false) {
                throw ErrorCodeException(ArtifactMessageCode.NODE_CONFLICT, dstFullPath)
            }
            val dstRootNodePath = if (dstNode == null) {
                // 目录 -> 不存在的目录
                val path = resolveParent(dstFullPath)
                val name = resolveName(dstFullPath)
                // 创建dst父目录
                nodeBaseService.mkdirs(dstProjectId, dstRepoName, path, operator)
                // 操作节点
                doMoveCopy(this, srcNode, path, name)
                PathUtils.combinePath(path, name)
            } else {
                // 目录 -> 存在的目录
                val path = toPath(dstNode.fullPath)
                val name = srcNode.name
                // 操作节点
                doMoveCopy(this, srcNode, path, name)
                PathUtils.combinePath(path, name)
            }
            val srcRootNodePath = toPath(srcNode.fullPath)
            val listOption = NodeListOption(includeFolder = true, includeMetadata = true, deep = true, sort = false)
            val query = buildSubNodesQuery(this, srcRootNodePath, listOption)
            // 目录下的节点 -> 创建好的目录
            nodeDao.stream(query).forEach {
                doMoveCopy(this, it, it.path.replaceFirst(srcRootNodePath, dstRootNodePath), it.name)
            }
        }
    }

    open fun buildSubNodesQuery(
        context: MoveCopyContext,
        srcRootNodePath: String,
        listOption: NodeListOption
    ): Query {
        return NodeMoveCopyHelper.buildSubNodesQuery(context, srcRootNodePath, listOption)
    }

    /**
     * 移动/复制文件
     */
    open fun moveCopyFile(context: MoveCopyContext) {
        with(context) {
            val dstPath = if (dstNode?.folder == true) toPath(dstNode.fullPath) else resolveParent(dstFullPath)
            val dstName = if (dstNode?.folder == true) srcNode.name else resolveName(dstFullPath)
            // 创建dst父目录
            nodeBaseService.mkdirs(dstProjectId, dstRepoName, dstPath, operator)
            doMoveCopy(context, srcNode, dstPath, dstName)
        }
    }

    private fun copyBlockNode(context: MoveCopyContext, srcNode: TNode, dstFullPath: String) {
        if (srcNode.folder || fsNode(srcNode)) {
            return
        }
        val srcBlocks = blockNodeService.listAllBlocks(
            srcNode.projectId,
            srcNode.repoName,
            srcNode.fullPath,
            srcNode.createdDate.format(DateTimeFormatter.ISO_DATE_TIME)
        )

        srcBlocks.forEach { block ->
            blockNodeService.createBlock(buildDstBlockNode(context, block, dstFullPath), context.dstCredentials)
        }
    }

    private fun findRepository(projectId: String, repoName: String): TRepository {
        return repositoryDao.findByNameAndType(projectId, repoName)
            ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
    }

    private fun findCredential(key: String?): StorageCredentials? {
        return key?.let { storageCredentialService.findByKey(it) }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeMoveCopySupport::class.java)
    }
}
