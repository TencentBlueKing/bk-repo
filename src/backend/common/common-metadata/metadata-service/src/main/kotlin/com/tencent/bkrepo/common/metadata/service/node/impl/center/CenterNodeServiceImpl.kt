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

package com.tencent.bkrepo.common.metadata.service.node.impl.center

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.properties.RouterControllerProperties
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.config.RepositoryProperties
import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.dao.repo.RepositoryDao
import com.tencent.bkrepo.common.metadata.model.TMetadata
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.model.TRepository
import com.tencent.bkrepo.common.metadata.pojo.node.RestoreContext
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import com.tencent.bkrepo.common.metadata.service.node.impl.NodeBaseService
import com.tencent.bkrepo.common.metadata.service.node.impl.NodeBaseService.Companion
import com.tencent.bkrepo.common.metadata.service.node.impl.NodeServiceImpl
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.QuotaService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.metadata.util.ClusterUtils
import com.tencent.bkrepo.common.metadata.util.NodeBaseServiceHelper.convertToDetail
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.condition.CommitEdgeCenterCondition
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteResult
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeRestoreResult
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodesDeleteRequest
import com.tencent.bkrepo.router.api.RouterControllerClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@Conditional(SyncCondition::class, CommitEdgeCenterCondition::class)
class CenterNodeServiceImpl(
    override val nodeDao: NodeDao,
    override val repositoryDao: RepositoryDao,
    override val fileReferenceService: FileReferenceService,
    override val storageCredentialService: StorageCredentialService,
    override val quotaService: QuotaService,
    override val repositoryProperties: RepositoryProperties,
    override val messageSupplier: MessageSupplier,
    override val servicePermissionClient: ServicePermissionClient,
    override val routerControllerClient: RouterControllerClient,
    override val routerControllerProperties: RouterControllerProperties,
    override val blockNodeService: BlockNodeService,
    override val projectService: ProjectService,
    val clusterProperties: ClusterProperties,
    val archiveClient: ArchiveClient,
) : NodeServiceImpl(
    nodeDao,
    repositoryDao,
    fileReferenceService,
    storageCredentialService,
    quotaService,
    repositoryProperties,
    messageSupplier,
    servicePermissionClient,
    routerControllerClient,
    routerControllerProperties,
    blockNodeService,
    projectService,
    archiveClient,
) {

    override fun checkRepo(projectId: String, repoName: String): TRepository {
        val repo = repositoryDao.findByNameAndType(projectId, repoName)
            ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)

        val enabledCheck = clusterProperties.commitEdge.repo.check
        if (enabledCheck && !ClusterUtils.containsSrcCluster(repo.clusterNames)) {
            throw ErrorCodeException(CommonMessageCode.OPERATION_CROSS_CLUSTER_NOT_ALLOWED)
        }
        return repo
    }

    override fun deleteByFullPathWithoutDecreaseVolume(
        projectId: String, repoName: String, fullPath: String, operator: String
    ) {
        return CenterNodeDeleteSupport(this, clusterProperties).deleteByFullPathWithoutDecreaseVolume(
            projectId,
            repoName,
            fullPath,
            operator
        )
    }

    override fun buildTNode(request: NodeCreateRequest): TNode {
        val node = super.buildTNode(request)
        SecurityUtils.getClusterName()?.let {
            node.clusterNames = setOf(it)
        }
        return node
    }

    override fun doCreate(node: TNode, repository: TRepository?, separate: Boolean): TNode {
        if (SecurityUtils.getClusterName().isNullOrBlank()) {
            return super.doCreate(node, repository, separate)
        }
        try {
            nodeDao.insert(node)
            if (!node.folder) {
                quotaService.increaseUsedVolume(node.projectId, node.repoName, node.size)
            }
        } catch (exception: DuplicateKeyException) {
            if (separate){
                logger.warn("Insert block base node[$node] error: [${exception.message}]")
                throw ErrorCodeException(ArtifactMessageCode.NODE_CONFLICT, node.fullPath)
            }
            logger.warn("Insert node[$node] error: [${exception.message}]")
        }

        return node
    }

    override fun createNode(createRequest: NodeCreateRequest): NodeDetail {
        with(createRequest) {
            val srcCluster = SecurityUtils.getClusterName() ?: clusterProperties.self.name.toString()
            val normalizeFullPath = PathUtils.normalizeFullPath(fullPath)
            val existNode = nodeDao.findNode(projectId, repoName, normalizeFullPath)
                ?: return super.createNode(createRequest)
            if (sha256 == existNode.sha256 && sha256 != FAKE_SHA256) {
                val clusterNames = existNode.clusterNames.orEmpty().toMutableSet()
                clusterNames.add(srcCluster)
                val query = NodeQueryHelper.nodeQuery(projectId, repoName, normalizeFullPath)
                if (existNode.clusterNames.orEmpty().isEmpty()) {
                    clusterNames.add(clusterProperties.self.name.toString())
                }
                val update = Update().addToSet(TNode::clusterNames.name).each(clusterNames)
                nodeMetadata?.let { update.set(TNode::metadata.name, it.map { convert(it) }) }
                update.set(TNode::lastModifiedBy.name, operator)
                update.set(TNode::lastModifiedDate.name, LocalDateTime.now())
                nodeDao.updateFirst(query, update)
                existNode.clusterNames = clusterNames
                logger.info("Create node[/$projectId/$repoName$fullPath],sha256[$sha256],region[$srcCluster] success.")
                return convertToDetail(existNode)!!
            } else {
                return super.createNode(createRequest)
            }
        }
    }

    override fun deleteNode(deleteRequest: NodeDeleteRequest): NodeDeleteResult {
        return CenterNodeDeleteSupport(this, clusterProperties).deleteNode(deleteRequest)
    }

    override fun deleteNodes(nodesDeleteRequest: NodesDeleteRequest): NodeDeleteResult {
        return CenterNodeDeleteSupport(this, clusterProperties).deleteNodes(nodesDeleteRequest)
    }

    override fun deleteByPath(
        projectId: String,
        repoName: String,
        fullPath: String,
        operator: String
    ): NodeDeleteResult {
        return CenterNodeDeleteSupport(this, clusterProperties).deleteByPath(
            projectId,
            repoName,
            fullPath,
            operator
        )
    }

    override fun deleteByPaths(
        projectId: String,
        repoName: String,
        fullPaths: List<String>,
        operator: String
    ): NodeDeleteResult {
        return CenterNodeDeleteSupport(this, clusterProperties).deleteByPaths(
            projectId,
            repoName,
            fullPaths,
            operator
        )
    }

    override fun deleteBeforeDate(
        projectId: String,
        repoName: String,
        date: LocalDateTime,
        operator: String,
        path: String,
        decreaseVolume: Boolean
    ): NodeDeleteResult {
        return CenterNodeDeleteSupport(this, clusterProperties).deleteBeforeDate(
            projectId,
            repoName,
            date,
            operator,
            path,
            decreaseVolume
        )
    }

    override fun restoreNode(restoreContext: RestoreContext): NodeRestoreResult {
        return CenterNodeRestoreSupport(this).restoreNode(restoreContext)
    }

    override fun copyNode(copyRequest: NodeMoveCopyRequest): NodeDetail {
        return CenterNodeMoveCopySupport(this).copyNode(copyRequest)
    }

    override fun moveNode(moveRequest: NodeMoveCopyRequest): NodeDetail {
        return CenterNodeMoveCopySupport(this).moveNode(moveRequest)
    }

    override fun renameNode(renameRequest: NodeRenameRequest) {
        return CenterNodeRenameSupport(this, clusterProperties).renameNode(renameRequest)
    }

    override fun additionalCheck(existNode: TNode) {
        // center 检查请求来源cluster是否是资源的唯一拥有者
        ClusterUtils.checkIsSrcCluster(existNode.clusterNames)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CenterNodeServiceImpl::class.java)

        private fun convert(metadataModel: MetadataModel): TMetadata {
            with(metadataModel) {
                return TMetadata(
                    key = key,
                    value = value,
                    system = system,
                    description = description,
                    link = link
                )
            }
        }
    }
}
