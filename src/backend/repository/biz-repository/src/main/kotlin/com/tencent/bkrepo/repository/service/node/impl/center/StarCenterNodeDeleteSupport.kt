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

import com.tencent.bkrepo.common.api.constant.ensureSuffix
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteResult
import com.tencent.bkrepo.repository.service.node.impl.NodeDeleteSupport
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import java.time.LocalDateTime

class StarCenterNodeDeleteSupport(
    private val centerNodeBaseService: CenterNodeBaseService,
    private val clusterProperties: ClusterProperties
): NodeDeleteSupport(
    centerNodeBaseService
) {

    override fun deleteByPath(
        projectId: String,
        repoName: String,
        fullPath: String,
        operator: String
    ): NodeDeleteResult {
        val normalizedFullPath = PathUtils.normalizeFullPath(fullPath)
        val node = nodeDao.findNode(projectId, repoName, normalizedFullPath)
            ?: return NodeDeleteResult(0,0, LocalDateTime.now())
        if (node.folder) {
            return delete(node, operator)
        }

        return if (!node.isLocalRegion()) {
            NodeDeleteResult(0,0, LocalDateTime.now())
        } else {
            val srcRegion = SecurityUtils.getRegion() ?: clusterProperties.region.toString()
            deleteFileNode(node, srcRegion, operator)
            NodeDeleteResult(1, node.size, LocalDateTime.now())
        }
    }

    override fun deleteByPaths(
        projectId: String,
        repoName: String,
        fullPaths: List<String>,
        operator: String
    ): NodeDeleteResult {
        var deletedNumber = 0L
        var deletedSize = 0L
        fullPaths.forEach {
            val result = deleteByPath(projectId, repoName, it, operator)
            deletedNumber += result.deletedNumber
            deletedSize += result.deletedSize
        }
        return NodeDeleteResult(deletedNumber, deletedSize, LocalDateTime.now())
    }

    fun delete(folder: TNode, operator: String): NodeDeleteResult {
        var deletedNumber = 0L
        var deletedSize = 0L
        val srcRegion = SecurityUtils.getRegion() ?: clusterProperties.region.toString()
        val criteria = where(TNode::projectId).isEqualTo(folder.projectId)
            .and(TNode::repoName).isEqualTo(folder.repoName)
            .and(TNode::deleted).isEqualTo(null)
            .and(TNode::folder).isEqualTo(false)
            .and(TNode::path).isEqualTo(folder.fullPath.ensureSuffix("/"))
        val query = Query(criteria)
        val subNodes = nodeDao.find(query)
        subNodes.forEach {
            if (it.folder) {
                val result = delete(it, operator)
                deletedNumber += result.deletedNumber
                deletedSize += result.deletedSize
            } else {
                if (!it.isLocalRegion()) {
                    return@forEach
                }
                deleteFileNode(it, srcRegion, operator)
                deletedNumber ++
                deletedSize += it.size
            }
        }
        if (subNodes.size.toLong() == deletedNumber) {
            super.deleteByPath(folder.projectId, folder.repoName, folder.fullPath, operator)
        }
        return NodeDeleteResult(deletedNumber, deletedSize, LocalDateTime.now())
    }

    private fun deleteFileNode(
        node: TNode,
        srcRegion: String,
        operator: String
    ) {
        node.regions = node.regions.orEmpty().minus(srcRegion)
        if (node.regions.orEmpty().isEmpty()) {
            super.deleteByPath(node.projectId, node.repoName, node.fullPath, operator)
        } else {
            nodeDao.save(node)
        }
        if (srcRegion == clusterProperties.region.toString() && node.regions.orEmpty().isNotEmpty()) {
            node.id = null
            node.deleted = LocalDateTime.now()
            nodeDao.insert(node)
        }
    }
}