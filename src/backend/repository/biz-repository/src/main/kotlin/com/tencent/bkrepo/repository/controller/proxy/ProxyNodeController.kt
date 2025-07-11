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

package com.tencent.bkrepo.repository.controller.proxy

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.proxy.ProxyNodeClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateAccessDateRequest
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import org.springframework.web.bind.annotation.RestController

@RestController
class ProxyNodeController(
    private val nodeService: NodeService
) : ProxyNodeClient {
    override fun getNodeDetail(projectId: String, repoName: String, fullPath: String): Response<NodeDetail?> {
        val artifactInfo = ArtifactInfo(projectId, repoName, fullPath)
        return ResponseBuilder.success(nodeService.getNodeDetail(artifactInfo))
    }

    override fun createNode(nodeCreateRequest: NodeCreateRequest): Response<NodeDetail> {
        return ResponseBuilder.success(nodeService.createNode(nodeCreateRequest))
    }

    override fun listNode(
        projectId: String,
        repoName: String,
        path: String,
        includeFolder: Boolean,
        deep: Boolean,
        includeMetadata: Boolean
    ): Response<List<NodeInfo>> {
        val artifactInfo = DefaultArtifactInfo(projectId, repoName, path)
        val nodeListOption = NodeListOption(
            includeFolder = includeFolder,
            includeMetadata = includeMetadata,
            deep = deep
        )
        return ResponseBuilder.success(nodeService.listNode(artifactInfo, nodeListOption))
    }

    override fun updateNodeAccessDate(nodeUpdateAccessDateRequest: NodeUpdateAccessDateRequest): Response<Void> {
        nodeService.updateNodeAccessDate(nodeUpdateAccessDateRequest)
        return ResponseBuilder.success()
    }
}
