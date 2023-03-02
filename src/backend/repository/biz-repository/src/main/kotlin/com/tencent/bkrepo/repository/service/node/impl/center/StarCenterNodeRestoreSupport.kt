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
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.node.ConflictStrategy
import com.tencent.bkrepo.repository.service.node.impl.NodeRestoreSupport
import com.tencent.bkrepo.repository.util.NodeQueryHelper

class StarCenterNodeRestoreSupport(
    centerNodeBaseService: CenterNodeBaseService
) : NodeRestoreSupport(
    centerNodeBaseService
) {

    override fun resolveConflict(context: RestoreContext, node: TNode) {
        with(context) {
            val fullPath = node.fullPath
            val existNode = nodeDao.findNode(projectId, repoName, fullPath)
            if (node.deleted == null || existNode != null) {
                when (conflictStrategy) {
                    ConflictStrategy.SKIP -> {
                        skipCount += 1
                        return
                    }
                    ConflictStrategy.OVERWRITE -> {
                        existNode!!.checkIsSrcRegion()
                        val query = NodeQueryHelper.nodeQuery(projectId, repoName, fullPath)
                        nodeDao.updateFirst(query, NodeQueryHelper.nodeDeleteUpdate(operator))
                        conflictCount += 1
                    }
                    ConflictStrategy.FAILED -> throw ErrorCodeException(ArtifactMessageCode.NODE_CONFLICT, fullPath)
                }
            }
            val query = NodeQueryHelper.nodeDeletedPointQuery(projectId, repoName, fullPath, deletedTime)
            restoreCount += nodeDao.updateFirst(query, NodeQueryHelper.nodeRestoreUpdate()).modifiedCount
        }
    }
}
