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

package com.tencent.bkrepo.repository.service.node.impl

import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.service.cluster.DefaultCondition
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.repository.config.RepositoryProperties
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.dao.RepositoryDao
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteResult
import com.tencent.bkrepo.repository.pojo.node.NodeDeletedPoint
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeRestoreOption
import com.tencent.bkrepo.repository.pojo.node.NodeRestoreResult
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeArchiveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCompressedRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUnCompressedRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodesDeleteRequest
import com.tencent.bkrepo.repository.service.file.FileReferenceService
import com.tencent.bkrepo.repository.service.repo.QuotaService
import com.tencent.bkrepo.repository.service.repo.StorageCredentialService
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Conditional(DefaultCondition::class)
class NodeServiceImpl(
    override val nodeDao: NodeDao,
    override val repositoryDao: RepositoryDao,
    override val fileReferenceService: FileReferenceService,
    override val storageCredentialService: StorageCredentialService,
    override val storageService: StorageService,
    override val quotaService: QuotaService,
    override val repositoryProperties: RepositoryProperties,
    override val messageSupplier: MessageSupplier,
    override val servicePermissionClient: ServicePermissionClient,
) : NodeBaseService(
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

    override fun computeSize(artifact: ArtifactInfo, estimated: Boolean): NodeSizeInfo {
        return NodeStatsSupport(this).computeSize(artifact, estimated)
    }

    override fun aggregateComputeSize(criteria: Criteria): Long {
        return NodeStatsSupport(this).aggregateComputeSize(criteria)
    }

    override fun countFileNode(artifact: ArtifactInfo): Long {
        return NodeStatsSupport(this).countFileNode(artifact)
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun deleteNode(deleteRequest: NodeDeleteRequest): NodeDeleteResult {
        return NodeDeleteSupport(this).deleteNode(deleteRequest)
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun deleteNodes(nodesDeleteRequest: NodesDeleteRequest): NodeDeleteResult {
        return NodeDeleteSupport(this).deleteNodes(nodesDeleteRequest)
    }

    override fun countDeleteNodes(nodesDeleteRequest: NodesDeleteRequest): Long {
        return NodeDeleteSupport(this).countDeleteNodes(nodesDeleteRequest)
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun deleteByPath(
        projectId: String,
        repoName: String,
        fullPath: String,
        operator: String,
    ): NodeDeleteResult {
        return NodeDeleteSupport(this).deleteByPath(projectId, repoName, fullPath, operator)
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun deleteByPaths(
        projectId: String,
        repoName: String,
        fullPaths: List<String>,
        operator: String,
    ): NodeDeleteResult {
        return NodeDeleteSupport(this).deleteByPaths(projectId, repoName, fullPaths, operator)
    }

    override fun deleteBeforeDate(
        projectId: String,
        repoName: String,
        date: LocalDateTime,
        operator: String,
        path: String,
    ): NodeDeleteResult {
        return NodeDeleteSupport(this).deleteBeforeDate(projectId, repoName, date, operator, path)
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun moveNode(moveRequest: NodeMoveCopyRequest): NodeDetail {
        return NodeMoveCopySupport(this).moveNode(moveRequest)
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun copyNode(copyRequest: NodeMoveCopyRequest): NodeDetail {
        return NodeMoveCopySupport(this).copyNode(copyRequest)
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun renameNode(renameRequest: NodeRenameRequest) {
        NodeRenameSupport(this).renameNode(renameRequest)
    }

    override fun getDeletedNodeDetail(artifact: ArtifactInfo): List<NodeDetail> {
        return NodeRestoreSupport(this).getDeletedNodeDetail(artifact)
    }

    override fun getDeletedNodeDetailBySha256(projectId: String, repoName: String, sha256: String): NodeDetail? {
        return NodeRestoreSupport(this).getDeletedNodeDetailBySha256(
            projectId,
            repoName,
            sha256,
        )
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun restoreNode(artifact: ArtifactInfo, nodeRestoreOption: NodeRestoreOption): NodeRestoreResult {
        return NodeRestoreSupport(this).restoreNode(artifact, nodeRestoreOption)
    }

    override fun listDeletedPoint(artifact: ArtifactInfo): List<NodeDeletedPoint> {
        return NodeRestoreSupport(this).listDeletedPoint(artifact)
    }

    override fun restoreNode(restoreContext: NodeRestoreSupport.RestoreContext): NodeRestoreResult {
        return NodeRestoreSupport(this).restoreNode(restoreContext)
    }

    override fun archiveNode(nodeArchiveRequest: NodeArchiveRequest) {
        return NodeArchiveSupport(this).archiveNode(nodeArchiveRequest)
    }

    override fun restoreNode(nodeArchiveRequest: NodeArchiveRequest) {
        return NodeArchiveSupport(this).restoreNode(nodeArchiveRequest)
    }

    override fun compressedNode(nodeCompressedRequest: NodeCompressedRequest) {
        return NodeCompressSupport(this).compressedNode(nodeCompressedRequest)
    }

    override fun uncompressedNode(nodeUnCompressedRequest: NodeUnCompressedRequest) {
        return NodeCompressSupport(this).uncompressedNode(nodeUnCompressedRequest)
    }
}
