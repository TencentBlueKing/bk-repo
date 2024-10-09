/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.service.project.impl

import com.tencent.bkrepo.auth.api.ServiceBkiamV3ResourceClient
import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.util.ClusterUtils.reportMetadataToCenter
import com.tencent.bkrepo.common.service.cluster.condition.CommitEdgeEdgeCondition
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.exception.RemoteErrorCodeException
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.repository.api.cluster.ClusterProjectClient
import com.tencent.bkrepo.common.metadata.dao.project.ProjectDao
import com.tencent.bkrepo.common.metadata.dao.project.ProjectMetricsDao
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service

@Service
@Conditional(SyncCondition::class, CommitEdgeEdgeCondition::class)
class EdgeProjectServiceImpl(
    projectDao: ProjectDao,
    servicePermissionClient: ServicePermissionClient,
    serviceBkiamV3ResourceClient: ServiceBkiamV3ResourceClient,
    clusterProperties: ClusterProperties,
    projectMetricsDao: ProjectMetricsDao,
    storageCredentialService: StorageCredentialService,
) : ProjectServiceImpl(
    projectDao,
    servicePermissionClient,
    projectMetricsDao,
    serviceBkiamV3ResourceClient,
    storageCredentialService
) {

    private val centerProjectClient: ClusterProjectClient by lazy {
        FeignClientFactory.create(
            clusterProperties.center,
            "repository",
            clusterProperties.self.name
        )
    }

    override fun createProject(request: ProjectCreateRequest): ProjectInfo {
        if (!reportMetadataToCenter(request.name)) {
            return super.createProject(request)
        }
        try {
            centerProjectClient.createProject(request)
        } catch (e: RemoteErrorCodeException) {
            if (e.errorCode != ArtifactMessageCode.PROJECT_EXISTED.getCode()) {
                throw e
            }
        }
        return super.createProject(request)
    }
}
