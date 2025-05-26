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

package com.tencent.bkrepo.common.metadata.service.node.impl.edge

import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.common.artifact.properties.RouterControllerProperties
import com.tencent.bkrepo.common.metadata.dao.repo.RepositoryDao
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.metadata.util.ClusterUtils.reportMetadataToCenter
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.repository.api.cluster.ClusterNodeClient
import com.tencent.bkrepo.common.metadata.config.RepositoryProperties
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.pojo.node.NodeDetail
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeUpdateAccessDateRequest
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeUpdateRequest
import com.tencent.bkrepo.common.metadata.service.node.impl.NodeBaseService
import com.tencent.bkrepo.common.metadata.service.repo.QuotaService
import com.tencent.bkrepo.router.api.RouterControllerClient

abstract class EdgeNodeBaseService(
    override val nodeDao: NodeDao,
    override val repositoryDao: RepositoryDao,
    override val fileReferenceService: FileReferenceService,
    override val storageCredentialService: StorageCredentialService,
    override val quotaService: QuotaService,
    override val repositoryProperties: RepositoryProperties,
    override val messageSupplier: MessageSupplier,
    override val routerControllerClient: RouterControllerClient,
    override val servicePermissionClient: ServicePermissionClient,
    override val routerControllerProperties: RouterControllerProperties,
    override val blockNodeService: BlockNodeService,
    override val projectService: ProjectService,
    open val clusterProperties: ClusterProperties,
) : NodeBaseService(
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
    projectService
) {

    val centerNodeClient: ClusterNodeClient by lazy {
        FeignClientFactory.create(
            clusterProperties.center,
            "repository",
            clusterProperties.self.name
        )
    }

    override fun buildTNode(request: NodeCreateRequest): TNode {
        val node = super.buildTNode(request)
        if (reportMetadataToCenter(request.projectId, request.repoName)) {
            node.clusterNames = setOf(clusterProperties.self.name!!)
        }
        return node
    }

    override fun createNode(createRequest: NodeCreateRequest): NodeDetail {
        if (reportMetadataToCenter(createRequest.projectId, createRequest.repoName)) {
            centerNodeClient.createNode(createRequest)
        }
        return super.createNode(createRequest)
    }

    override fun updateNode(updateRequest: NodeUpdateRequest) {
        val node = nodeDao.findNode(updateRequest.projectId, updateRequest.repoName, updateRequest.fullPath)
        if (node?.clusterNames?.isEmpty() == false) {
            centerNodeClient.updateNode(updateRequest)
        }
        super.updateNode(updateRequest)
    }

    override fun updateNodeAccessDate(updateAccessDateRequest: NodeUpdateAccessDateRequest) {
        val node = nodeDao.findNode(
            updateAccessDateRequest.projectId,
            updateAccessDateRequest.repoName,
            updateAccessDateRequest.fullPath
        )
        if (node?.clusterNames?.isEmpty() == false) {
            centerNodeClient.updateNodeAccessDate(updateAccessDateRequest)
        }
        super.updateNodeAccessDate(updateAccessDateRequest)
    }
}
