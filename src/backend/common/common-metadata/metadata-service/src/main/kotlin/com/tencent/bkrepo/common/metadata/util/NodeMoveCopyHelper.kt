package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils.toPath
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.model.TRepository
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import org.springframework.data.mongodb.core.query.Query
import java.time.LocalDateTime

object NodeMoveCopyHelper {

    /**
     * 预检查
     */
    fun preCheck(context: MoveCopyContext) {
        // 只允许local或者composite类型仓库操作
        val canSrcRepoMove = context.srcRepo.category.let {
            it == RepositoryCategory.LOCAL || it == RepositoryCategory.COMPOSITE
        }
        val canDstRepoMove = context.dstRepo.category.let {
            it == RepositoryCategory.LOCAL || it == RepositoryCategory.COMPOSITE
        }
        if (!canSrcRepoMove || !canDstRepoMove) {
            throw ErrorCodeException(CommonMessageCode.METHOD_NOT_ALLOWED, "Only local repository is supported")
        }
    }

    /**
     * 判断能否忽略执行
     */
    fun canIgnore(context: MoveCopyContext): Boolean {
        with(context) {
            var canIgnore = false
            if (isSameRepo()) {
                if (srcNode.fullPath == dstNode?.fullPath) {
                    // 同路径，跳过
                    canIgnore = true
                } else if (dstNode?.folder == true && srcNode.path == toPath(dstNode.fullPath)) {
                    // src为dst目录下的子节点，跳过
                    canIgnore = true
                }
            }
            return canIgnore
        }
    }

    fun buildDstNode(
        context: MoveCopyContext,
        node: TNode,
        dstPath: String,
        dstName: String,
        dstFullPath: String
    ): TNode {
        with(context) {
            val dstNode = node.copy(
                id = null,
                projectId = dstProjectId,
                repoName = dstRepoName,
                path = dstPath,
                name = dstName,
                fullPath = dstFullPath,
                size = if (node.folder) 0 else node.size,
                nodeNum = if (node.folder) null else node.nodeNum,
                lastModifiedBy = operator,
                lastModifiedDate = LocalDateTime.now()
            )
            // move操作，create信息保留
            if (move) {
                dstNode.createdBy = operator
                dstNode.createdDate = LocalDateTime.now()
            }

            return dstNode
        }
    }

    fun buildDstBlockNode(
        context: MoveCopyContext,
        blockNode: TBlockNode,
        dstFullPath: String
    ): TBlockNode {
        with(context) {
            val dstBlockNode = blockNode.copy(
                id = null,
                projectId = dstProjectId,
                repoName = dstRepoName,
                nodeFullPath = dstFullPath,
            )
            // move操作，create信息保留
            if (move) {
                dstBlockNode.createdBy = operator
                dstBlockNode.createdDate = LocalDateTime.now()
            }

            return dstBlockNode
        }
    }

    fun checkConflict(context: MoveCopyContext, node: TNode, existNode: TNode?) {
        // 目录 -> 文件: 出错
        if (node.folder && existNode?.folder == false) {
            throw ErrorCodeException(ArtifactMessageCode.NODE_CONFLICT, existNode.fullPath)
        }
        // 文件 -> 文件 & 不允许覆盖: 出错
        if (!node.folder && existNode?.folder == false && !context.overwrite) {
            throw ErrorCodeException(ArtifactMessageCode.NODE_CONFLICT, existNode.fullPath)
        }
    }

    fun buildSubNodesQuery(
        context: MoveCopyContext,
        srcRootNodePath: String,
        listOption: NodeListOption
    ): Query {
        with(context) {
            val query = NodeQueryHelper.nodeListQuery(srcNode.projectId, srcNode.repoName, srcRootNodePath, listOption)
            return query
        }
    }

    data class MoveCopyContext(
        val srcRepo: TRepository,
        val srcCredentials: StorageCredentials?,
        val srcNode: TNode,
        val dstProjectId: String,
        val dstRepoName: String,
        val dstFullPath: String,
        val dstRepo: TRepository,
        val dstCredentials: StorageCredentials?,
        val dstNode: TNode?,
        val dstNodeFolder: Boolean?,
        val overwrite: Boolean,
        val operator: String,
        val move: Boolean
    ) {
        fun isSameRepo() = srcNode.projectId == dstProjectId && srcNode.repoName == dstRepoName
    }
}
