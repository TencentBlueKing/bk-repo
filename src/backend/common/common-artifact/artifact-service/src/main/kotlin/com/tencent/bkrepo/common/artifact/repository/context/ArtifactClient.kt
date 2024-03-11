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

package com.tencent.bkrepo.common.artifact.repository.context

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.metadata.pojo.node.NodeDetail
import com.tencent.bkrepo.common.metadata.pojo.node.NodeInfo
import com.tencent.bkrepo.common.metadata.pojo.node.NodeListOption
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeUpdateAccessDateRequest
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.springframework.beans.factory.annotation.Autowired

@Suppress("SpringJavaInjectionPointsAutowiringInspection")
open class ArtifactClient {

    @Autowired
    private lateinit var repositoryClient: RepositoryClient

    @Autowired
    private lateinit var nodeService: NodeService

    open fun getRepositoryDetailOrNull(
        projectId: String,
        repoName: String,
        repoType: String
    ): RepositoryDetail? {
        return repositoryClient.getRepoDetail(projectId, repoName, repoType).data
    }

    open fun getNodeDetailOrNull(projectId: String, repoName: String, fullPath: String): NodeDetail? {
        return nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath))
    }

    open fun listNode(
        projectId: String,
        repoName: String,
        fullPath: String,
        includeFolder: Boolean,
        deep: Boolean
    ): List<NodeInfo>? {
        return nodeService.listNode(
            artifact = ArtifactInfo(projectId, repoName, fullPath),
            option = NodeListOption(includeFolder = includeFolder, deep =  deep)
        )
    }

    open fun updateAccessDate(request: NodeUpdateAccessDateRequest) {
        nodeService.updateNodeAccessDate(request)
    }
}
