/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.conan.service.impl.edge

import com.tencent.bkrepo.common.service.cluster.condition.CommitEdgeEdgeCondition
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.conan.api.ConanMetadataClient
import com.tencent.bkrepo.conan.dao.ConanMetadataDao
import com.tencent.bkrepo.conan.pojo.metadata.ConanMetadataRequest
import com.tencent.bkrepo.conan.service.ConanMetadataService
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service

@Service
@Conditional(CommitEdgeEdgeCondition::class)
class EdgeConanMetadataService(
    clusterProperties: ClusterProperties,
    private val conanMetadataDao: ConanMetadataDao
) : ConanMetadataService(conanMetadataDao) {
    private val centerConanMetadataClient: ConanMetadataClient by lazy {
        FeignClientFactory.create(clusterProperties.center, "conan", clusterProperties.self.name)
    }

    override fun storeMetadata(request: ConanMetadataRequest) {
        with(request) {
            // 更新center节点metadata信息
            val centerRequest = ConanMetadataRequest(
                projectId = projectId,
                repoName = repoName,
                name = name,
                user = user,
                channel = channel,
                version = version,
                recipe = recipe,
            )
            centerConanMetadataClient.storeMetadata(centerRequest)
            super.storeMetadata(request)
        }
    }

    override fun delete(projectId: String, repoName: String, recipe: String) {
        centerConanMetadataClient.delete(projectId, repoName, recipe)
        super.delete(projectId, repoName, recipe)
    }
}
