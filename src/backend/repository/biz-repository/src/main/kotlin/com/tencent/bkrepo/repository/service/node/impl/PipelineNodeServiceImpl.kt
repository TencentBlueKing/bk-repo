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

package com.tencent.bkrepo.repository.service.node.impl

import com.tencent.bkrepo.auth.api.ServicePipelineClient
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.service.node.NodeService
import com.tencent.bkrepo.repository.service.node.PipelineNodeService
import org.springframework.stereotype.Service

@Service
class PipelineNodeServiceImpl(
    private val nodeService: NodeService,
    private val servicePipelineClient: ServicePipelineClient
): PipelineNodeService {
    override fun listPipeline(userId: String, projectId: String, repoName: String): List<NodeInfo> {
        // 1. auth查询有权限的pipeline
        val pipelines = servicePipelineClient.listPermissionedPipelines(userId, projectId).data.orEmpty()
        // 2. 查询根节点下的目录
        val artifactInfo = DefaultArtifactInfo(projectId, repoName, PathUtils.ROOT)
        val option = NodeListOption(includeMetadata = true, sort = true)
        val nodeMap = nodeService.listNode(artifactInfo, option).associateBy { it.name }
        // 3. 内存过滤
        val pipelineNodeList = mutableListOf<NodeInfo>()
        if (pipelines.firstOrNull().equals("*")) {
            pipelineNodeList.addAll(nodeMap.values)
        } else {
            pipelines.forEach {
                val node = nodeMap[it]
                if (node != null) {
                    pipelineNodeList.add(node)
                }
            }
        }
        // 4. 返回结果
        return pipelineNodeList
    }
}
