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

package com.tencent.bkrepo.common.metadata.service.node.impl.center

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.metadata.pojo.node.ConflictStrategy
import com.tencent.bkrepo.common.metadata.pojo.node.RestoreContext
import com.tencent.bkrepo.common.metadata.util.ClusterUtils
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.service.node.impl.NodeBaseService
import com.tencent.bkrepo.common.metadata.service.node.impl.NodeRestoreSupport
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper

class CenterNodeRestoreSupport(
    nodeBaseService: NodeBaseService
) : NodeRestoreSupport(
    nodeBaseService
) {

    override fun resolveConflict(context: RestoreContext, node: TNode) {
        val fullPath = node.fullPath
        val existNode = nodeDao.findNode(context.projectId, context.repoName, fullPath)
        if (node.deleted == null || existNode != null) {
            when (context.conflictStrategy) {
                ConflictStrategy.SKIP -> {
                    context.skipCount += 1
                    return
                }
                ConflictStrategy.OVERWRITE -> {
                    existNode?.let { ClusterUtils.checkIsSrcCluster(it.clusterNames) }
                    val query = NodeQueryHelper.nodeQuery(context.projectId, context.repoName, fullPath)
                    nodeDao.updateFirst(query, NodeQueryHelper.nodeDeleteUpdate(context.operator))
                    context.conflictCount += 1
                }
                ConflictStrategy.FAILED -> throw ErrorCodeException(ArtifactMessageCode.NODE_CONFLICT, fullPath)
            }
        }
        val query = NodeQueryHelper.nodeDeletedPointQuery(
            projectId = context.projectId,
            repoName = context.repoName,
            fullPath = fullPath,
            deleted = context.deletedTime
        )
        context.restoreCount += nodeDao.updateFirst(query, NodeQueryHelper.nodeRestoreUpdate()).modifiedCount
    }
}
