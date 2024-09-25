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

package com.tencent.bkrepo.repository.service.node.impl.center

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.util.ClusterUtils
import com.tencent.bkrepo.common.metadata.util.ClusterUtils.isEdgeRequest
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.service.node.impl.NodeBaseService
import com.tencent.bkrepo.repository.service.node.impl.NodeRenameSupport
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where

class CommitEdgeCenterNodeRenameSupport(
    nodeBaseService: NodeBaseService,
    private val clusterProperties: ClusterProperties
) : NodeRenameSupport(
    nodeBaseService
) {

    /**
     * 重命名目录检查子节点是否包含edge节点
     * 重命名文件检查节点是否为edge节点
     */
    override fun checkNodeCluster(node: TNode) {
        if (node.folder) {
            val query = Query(
                where(TNode::projectId).isEqualTo(node.projectId)
                    .and(TNode::repoName).isEqualTo(node.repoName)
                    .and(TNode::fullPath).regex("^${PathUtils.escapeRegex(node.fullPath)}")
                    .and(TNode::folder).isEqualTo(false)
                    .and(TNode::deleted).isEqualTo(null)
                    .andOperator(buildClusterCriteria())
            )
            nodeDao.findOne(query) ?: return
            throw ErrorCodeException(CommonMessageCode.OPERATION_CROSS_CLUSTER_NOT_ALLOWED)
        } else {
            ClusterUtils.checkIsSrcCluster(node.clusterNames)
        }
    }

    private fun buildClusterCriteria(): Criteria {
        return if (isEdgeRequest()) {
            where(TNode::clusterNames).ne(SecurityUtils.getClusterName())
        } else {
            Criteria().andOperator(
                where(TNode::clusterNames).ne(listOf(clusterProperties.self.name)),
                where(TNode::clusterNames).ne(null)
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CommitEdgeCenterNodeRenameSupport::class.java)
    }
}
