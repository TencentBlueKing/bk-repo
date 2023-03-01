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

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.StartCenterCondition
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.repository.config.RepositoryProperties
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.dao.RepositoryDao
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteResult
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodesDeleteRequest
import com.tencent.bkrepo.repository.service.file.FileReferenceService
import com.tencent.bkrepo.repository.service.repo.QuotaService
import com.tencent.bkrepo.repository.service.repo.StorageCredentialService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@Conditional(StartCenterCondition::class)
class StartCenterNodeServiceImpl(
    override val nodeDao: NodeDao,
    override val repositoryDao: RepositoryDao,
    override val fileReferenceService: FileReferenceService,
    override val storageCredentialService: StorageCredentialService,
    override val storageService: StorageService,
    override val quotaService: QuotaService,
    override val repositoryProperties: RepositoryProperties,
    override val messageSupplier: MessageSupplier,
    val clusterProperties: ClusterProperties
) : CenterNodeServiceImpl(
    nodeDao,
    repositoryDao,
    fileReferenceService,
    storageCredentialService,
    storageService,
    quotaService,
    repositoryProperties,
    messageSupplier
) {

    override fun checkRepo(projectId: String, repoName: String): TRepository {
        val repo = repositoryDao.findByNameAndType(projectId, repoName)
            ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
        val region = SecurityUtils.getRegion()
        if (!region.isNullOrBlank() && !repo.regions.orEmpty().contains(region)) {
            throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
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
                    existNode.checkLocalRegion()
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
        node.regions = setOf(SecurityUtils.getRegion() ?: clusterProperties.region!!)
        return node
    }

    override fun doCreate(node: TNode, repository: TRepository?): TNode {
        if (SecurityUtils.getRegion().isNullOrBlank()) {
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
        val region = SecurityUtils.getRegion()
        if (region.isNullOrBlank()) {
            return super.createNode(createRequest)
        }
        with(createRequest) {
            val existNode = nodeDao.findNode(projectId, repoName, PathUtils.normalizeFullPath(fullPath))
                ?: return super.createNode(createRequest)
            if (sha256 == existNode.sha256) {
                val regions = existNode.regions.orEmpty().toMutableSet()
                regions.add(region)
                existNode.regions = regions
                nodeDao.save(existNode)
                logger.info("Create edge node[/$projectId/$repoName$fullPath], sha256[$sha256], region[$region] success.")
                return convertToDetail(existNode)!!
            } else {
                return super.createNode(createRequest)
            }

        }
    }

    override fun deleteNode(deleteRequest: NodeDeleteRequest): NodeDeleteResult {
        return StarCenterNodeDeleteSupport(this, clusterProperties).deleteNode(deleteRequest)
    }

    override fun deleteNodes(nodesDeleteRequest: NodesDeleteRequest): NodeDeleteResult {
        return StarCenterNodeDeleteSupport(this, clusterProperties).deleteNodes(nodesDeleteRequest)
    }

    override fun deleteByPath(
        projectId: String,
        repoName: String,
        fullPath: String,
        operator: String
    ): NodeDeleteResult {
        return StarCenterNodeDeleteSupport(this, clusterProperties).deleteByPath(
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
        return StarCenterNodeDeleteSupport(this, clusterProperties).deleteByPaths(
            projectId,
            repoName,
            fullPaths,
            operator
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StartCenterNodeServiceImpl::class.java)
    }
}