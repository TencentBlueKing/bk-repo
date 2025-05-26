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

package com.tencent.bkrepo.common.metadata.service.node.impl.center

import com.tencent.bkrepo.common.artifact.path.PathUtils.resolveName
import com.tencent.bkrepo.common.artifact.path.PathUtils.resolveParent
import com.tencent.bkrepo.common.artifact.path.PathUtils.toPath
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.service.node.impl.NodeBaseService
import com.tencent.bkrepo.common.metadata.service.node.impl.NodeMoveCopySupport
import com.tencent.bkrepo.common.metadata.util.ClusterUtils
import com.tencent.bkrepo.common.metadata.util.ClusterUtils.isEdgeRequest
import com.tencent.bkrepo.common.metadata.util.NodeMoveCopyHelper.MoveCopyContext
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.metadata.pojo.node.NodeListOption
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where

class CenterNodeMoveCopySupport(
    private val nodeBaseService: NodeBaseService
) : NodeMoveCopySupport(
    nodeBaseService
) {

    override fun checkConflict(context: MoveCopyContext, node: TNode, existNode: TNode?) {
        super.checkConflict(context, node, existNode)
        logger.debug("check node[${node.projectId}/${node.repoName}/${node.fullPath}] cluster")
        ClusterUtils.checkIsSrcCluster(node.clusterNames)
        existNode?.let { ClusterUtils.checkIsSrcCluster(it.clusterNames) }
    }

    override fun moveCopyFile(context: MoveCopyContext) {
        with(context) {
            val dstPath = if (dstNode?.folder == true || dstNodeFolder == true) {
                toPath(dstFullPath)
            } else {
                resolveParent(dstFullPath)
            }
            val dstName = if (dstNode?.folder == true || dstNodeFolder == true) {
                srcNode.name
            } else {
                resolveName(dstFullPath)
            }
            // 创建dst父目录
            nodeBaseService.mkdirs(dstProjectId, dstRepoName, dstPath, operator)
            doMoveCopy(context, srcNode, dstPath, dstName)
        }
    }

    override fun buildSubNodesQuery(
        context: MoveCopyContext,
        srcRootNodePath: String,
        listOption: NodeListOption
    ): Query {
        val query = super.buildSubNodesQuery(context, srcRootNodePath, listOption)
        if (isEdgeRequest()) {
            query.addCriteria(where(TNode::clusterNames).isEqualTo(SecurityUtils.getClusterName()))
        }
        return query
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CenterNodeMoveCopySupport::class.java)
    }
}
