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

package com.tencent.bkrepo.common.metadata.service.metadata.impl

import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.config.RepositoryProperties
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.util.ClusterUtils.ignoreException
import com.tencent.bkrepo.common.metadata.util.ClusterUtils.nodeLevelNotFoundError
import com.tencent.bkrepo.common.security.manager.ci.CIPermissionManager
import com.tencent.bkrepo.common.service.cluster.condition.CommitEdgeEdgeCondition
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.repository.api.cluster.ClusterMetadataClient
import com.tencent.bkrepo.repository.pojo.metadata.DeletedNodeMetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import io.micrometer.observation.ObservationRegistry
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service

@Service
@Conditional(SyncCondition::class, CommitEdgeEdgeCondition::class)
class EdgeMetadataServiceImpl(
    nodeDao: NodeDao,
    repositoryProperties: RepositoryProperties,
    clusterProperties: ClusterProperties,
    ciPermissionManager: CIPermissionManager,
    metadataLabelCacheService: MetadataLabelCacheService,
    registry: ObservationRegistry
) : MetadataServiceImpl(
    nodeDao,
    repositoryProperties,
    ciPermissionManager,
    metadataLabelCacheService,
    registry
) {

    private val centerMetadataClient: ClusterMetadataClient by lazy {
        FeignClientFactory.create(
            clusterProperties.center,
            "repository",
            clusterProperties.self.name
        )
    }

    override fun saveMetadata(request: MetadataSaveRequest) {
        ignoreException(
            projectId = request.projectId,
            repoName = request.repoName,
            messageCodes = nodeLevelNotFoundError
        ) {
            centerMetadataClient.saveMetadata(request)
        }
        super.saveMetadata(request)
    }

    override fun deleteMetadata(request: MetadataDeleteRequest, allowDeleteSystemMetadata: Boolean) {
        ignoreException(
            projectId = request.projectId,
            repoName = request.repoName,
            messageCodes = nodeLevelNotFoundError
        ) {
            centerMetadataClient.deleteMetadata(request)
        }
        super.deleteMetadata(request, allowDeleteSystemMetadata)
    }

    override fun addForbidMetadata(request: MetadataSaveRequest) {
        ignoreException(
            projectId = request.projectId,
            repoName = request.repoName,
            messageCodes = nodeLevelNotFoundError
        ) {
            centerMetadataClient.addForbidMetadata(request)
        }
        super.addForbidMetadata(request)
    }

    override fun saveMetadataForDeletedNode(request: DeletedNodeMetadataSaveRequest) {
        ignoreException(
            projectId = request.metadataSaveRequest.projectId,
            repoName = request.metadataSaveRequest.repoName,
            messageCodes = nodeLevelNotFoundError
        ) {
            centerMetadataClient.saveMetadataForDeletedNode(request)
        }
        super.saveMetadataForDeletedNode(request)
    }
}
