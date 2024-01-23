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

package com.tencent.bkrepo.repository.service.node.impl.center

import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.util.ClusterUtils
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.CommitEdgeCenterCondition
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.repository.config.RepositoryProperties
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.dao.RepositoryDao
import com.tencent.bkrepo.repository.model.TMetadata
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteResult
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeRestoreResult
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodesDeleteRequest
import com.tencent.bkrepo.repository.service.file.FileReferenceService
import com.tencent.bkrepo.repository.service.node.impl.NodeRestoreSupport
import com.tencent.bkrepo.repository.service.node.impl.NodeServiceImpl
import com.tencent.bkrepo.repository.service.repo.QuotaService
import com.tencent.bkrepo.repository.service.repo.StorageCredentialService
import com.tencent.bkrepo.repository.util.NodeQueryHelper
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@Conditional(CommitEdgeCenterCondition::class)
class CommitEdgeCenterNodeServiceImpl(
    override val nodeDao: NodeDao,
    override val repositoryDao: RepositoryDao,
    override val fileReferenceService: FileReferenceService,
    override val storageCredentialService: StorageCredentialService,
    override val storageService: StorageService,
    override val quotaService: QuotaService,
    override val repositoryProperties: RepositoryProperties,
    override val messageSupplier: MessageSupplier,
    override val servicePermissionClient: ServicePermissionClient,
    val clusterProperties: ClusterProperties
) : NodeServiceImpl(
    nodeDao,
    repositoryDao,
    fileReferenceService,
    storageCredentialService,
    storageService,
    quotaService,
    repositoryProperties,
    messageSupplier,
    servicePermissionClient,
) {

    override fun checkRepo(projectId: String, repoName: String): TRepository {
        val repo = repositoryDao.findByNameAndType(projectId, repoName)
            ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)

        if (!ClusterUtils.containsSrcCluster(repo.clusterNames)) {
            throw ErrorCodeException(CommonMessageCode.OPERATION_CROSS_CLUSTER_NOT_ALLOWED)
        }
        return repo
    }

    override fun checkConflictAndQuota(createRequest: NodeCreateRequest, fullPath: String): LocalDateTime? {
        with(createRequest) {
            val existNode = nodeDao.findNode(projectId, repoName, fullPath)
            if (existNode != null) {
                if (!overwrite) {
                    throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, fullPath)
                } else if (existNode.folder || this.folder) {
                    throw ErrorCodeException(ArtifactMessageCode.NODE_CONFLICT, fullPath)
                } else {
                    ClusterUtils.checkIsSrcCluster(existNode.clusterNames)
                    val changeSize = this.size?.minus(existNode.size) ?: -existNode.size
                    quotaService.checkRepoQuota(projectId, repoName, changeSize)
                    return deleteByPath(projectId, repoName, fullPath, operator).deletedTime
                }
            } else {
                quotaService.checkRepoQuota(projectId, repoName, this.size ?: 0)
            }
            return null
        }
    }

    override fun buildTNode(request: NodeCreateRequest): TNode {
        val node = super.buildTNode(request)
        node.clusterNames = setOf(SecurityUtils.getClusterName() ?: clusterProperties.self.name!!)
        return node
    }

    override fun doCreate(node: TNode, repository: TRepository?): TNode {
        if (SecurityUtils.getClusterName().isNullOrBlank()) {
            return super.doCreate(node, repository)
        }
        try {
            nodeDao.insert(node)
            if (!node.folder) {
                quotaService.increaseUsedVolume(node.projectId, node.repoName, node.size)
            }
        } catch (exception: DuplicateKeyException) {
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
            if (sha256 == existNode.sha256) {
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
        return CommitEdgeCenterNodeDeleteSupport(this, clusterProperties).deleteNode(deleteRequest)
    }

    override fun deleteNodes(nodesDeleteRequest: NodesDeleteRequest): NodeDeleteResult {
        return CommitEdgeCenterNodeDeleteSupport(this, clusterProperties).deleteNodes(nodesDeleteRequest)
    }

    override fun deleteByPath(
        projectId: String,
        repoName: String,
        fullPath: String,
        operator: String
    ): NodeDeleteResult {
        return CommitEdgeCenterNodeDeleteSupport(this, clusterProperties).deleteByPath(
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
        return CommitEdgeCenterNodeDeleteSupport(this, clusterProperties).deleteByPaths(
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
        path: String
    ): NodeDeleteResult {
        return CommitEdgeCenterNodeDeleteSupport(this, clusterProperties).deleteBeforeDate(
            projectId,
            repoName,
            date,
            operator,
            path
        )
    }

    override fun restoreNode(restoreContext: NodeRestoreSupport.RestoreContext): NodeRestoreResult {
        return CommitEdgeCenterNodeRestoreSupport(this).restoreNode(restoreContext)
    }

    override fun copyNode(copyRequest: NodeMoveCopyRequest): NodeDetail {
        return CommitEdgeCenterNodeMoveCopySupport(this, clusterProperties).copyNode(copyRequest)
    }

    override fun moveNode(moveRequest: NodeMoveCopyRequest): NodeDetail {
        return CommitEdgeCenterNodeMoveCopySupport(this, clusterProperties).moveNode(moveRequest)
    }

    override fun renameNode(renameRequest: NodeRenameRequest) {
        return CommitEdgeCenterNodeRenameSupport(this).renameNode(renameRequest)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CommitEdgeCenterNodeServiceImpl::class.java)

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
