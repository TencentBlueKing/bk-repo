/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.fs.server.service.node

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.metadata.client.RAuthClient
import com.tencent.bkrepo.common.metadata.config.RepositoryProperties
import com.tencent.bkrepo.common.metadata.dao.node.RNodeDao
import com.tencent.bkrepo.common.metadata.dao.repo.RRepositoryDao
import com.tencent.bkrepo.common.metadata.service.blocknode.RBlockNodeService
import com.tencent.bkrepo.common.metadata.service.file.RFileReferenceService
import com.tencent.bkrepo.common.metadata.service.project.RProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RQuotaService
import com.tencent.bkrepo.common.metadata.service.repo.RStorageCredentialService
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteResult
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RNodeServiceImpl(
    override val nodeDao: RNodeDao,
    override val repositoryDao: RRepositoryDao,
    override val fileReferenceService: RFileReferenceService,
    override val storageCredentialService: RStorageCredentialService,
    override val quotaService: RQuotaService,
    override val repositoryProperties: RepositoryProperties,
    override val messageSupplier: MessageSupplier,
    override val authClient: RAuthClient,
    override val blockNodeService: RBlockNodeService,
    override val projectService: RProjectService,
) : RNodeBaseService(
    nodeDao,
    repositoryDao,
    fileReferenceService,
    storageCredentialService,
    quotaService,
    repositoryProperties,
    messageSupplier,
    authClient,
    blockNodeService,
    projectService
) {

    override suspend fun computeSize(
        artifact: ArtifactInfo, estimated: Boolean
    ): NodeSizeInfo {
        return RNodeStatsSupport(this).computeSize(artifact, estimated)
    }

    override suspend fun aggregateComputeSize(criteria: Criteria): Long {
        return RNodeStatsSupport(this).aggregateComputeSize(criteria)
    }

    @Transactional(rollbackFor = [Throwable::class])
    override suspend fun deleteNode(deleteRequest: NodeDeleteRequest): NodeDeleteResult {
        return RNodeDeleteSupport(this).deleteNode(deleteRequest)
    }

    override suspend fun deleteByFullPathWithoutDecreaseVolume(
        projectId: String, repoName: String, fullPath: String, operator: String
    ) {
        return RNodeDeleteSupport(this).deleteByFullPathWithoutDecreaseVolume(
            projectId, repoName, fullPath, operator
        )
    }

    @Transactional(rollbackFor = [Throwable::class])
    override suspend fun deleteByPath(
        projectId: String,
        repoName: String,
        fullPath: String,
        operator: String,
    ): NodeDeleteResult {
        return RNodeDeleteSupport(this).deleteByPath(projectId, repoName, fullPath, operator)
    }


    @Transactional(rollbackFor = [Throwable::class])
    override suspend fun moveNode(moveRequest: NodeMoveCopyRequest): NodeDetail {
        return RNodeMoveCopySupport(this).moveNode(moveRequest)
    }

    @Transactional(rollbackFor = [Throwable::class])
    override suspend fun copyNode(copyRequest: NodeMoveCopyRequest): NodeDetail {
        return RNodeMoveCopySupport(this).copyNode(copyRequest)
    }

    @Transactional(rollbackFor = [Throwable::class])
    override suspend fun renameNode(renameRequest: NodeRenameRequest) {
        RNodeRenameSupport(this).renameNode(renameRequest)
    }
}
