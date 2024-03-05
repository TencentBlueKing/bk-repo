/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 *  A copy of the MIT License is included in this file.
 *
 *
 *  Terms of the MIT License:
 *  ---------------------------------------------------
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 *  documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 *  the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 *  LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 *  NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 *  SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.metadata.service.node.impl.center

import com.tencent.bkrepo.common.metadata.util.ClusterUtils
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.common.metadata.service.node.impl.NodeBaseService
import com.tencent.bkrepo.common.metadata.service.node.impl.NodeMoveCopySupport
import java.time.LocalDateTime

class CommitEdgeCenterNodeMoveCopySupport(
    nodeBaseService: NodeBaseService,
    private val clusterProperties: ClusterProperties
) : NodeMoveCopySupport(
    nodeBaseService
) {

    override fun checkConflict(context: MoveCopyContext, node: TNode, existNode: TNode?) {
        super.checkConflict(context, node, existNode)
        if (context.move) {
            ClusterUtils.checkIsSrcCluster(node.clusterNames)
            existNode?.let { ClusterUtils.checkIsSrcCluster(it.clusterNames) }
        } else {
            ClusterUtils.checkContainsSrcCluster(node.clusterNames)
            existNode?.let { ClusterUtils.checkContainsSrcCluster(it.clusterNames) }
        }
    }

    override fun buildDstNode(
        context: MoveCopyContext,
        node: TNode,
        dstPath: String,
        dstName: String,
        dstFullPath: String
    ): TNode {
        with(context) {
            val srcCluster = SecurityUtils.getClusterName() ?: clusterProperties.self.name.toString()
            val dstNode = node.copy(
                id = null,
                projectId = dstProjectId,
                repoName = dstRepoName,
                path = dstPath,
                name = dstName,
                fullPath = dstFullPath,
                lastModifiedBy = operator,
                lastModifiedDate = LocalDateTime.now(),
                clusterNames = setOf(srcCluster)
            )
            // move操作，create信息保留
            if (move) {
                dstNode.createdBy = operator
                dstNode.createdDate = LocalDateTime.now()
            }

            return dstNode
        }
    }
}
