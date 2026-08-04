/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.opdata.cluster.topology.service

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.opdata.cluster.topology.dao.ClusterNodeExtensionDao
import com.tencent.bkrepo.opdata.cluster.dao.ReplClusterDao
import com.tencent.bkrepo.opdata.cluster.topology.model.TClusterNodeExtension
import com.tencent.bkrepo.opdata.cluster.topology.pojo.ClusterNodeMetadataUpdateRequest
import com.tencent.bkrepo.opdata.cluster.topology.pojo.ClusterNodeMetadataVO
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 节点元数据管理服务。
 *
 * 仅作用于长期组网节点（CENTER / EDGE / STANDALONE），写入到 cluster_node_extension 集合，
 * 不修改 replication 模块的 cluster_node 核心字段。
 *
 * 写操作完成后通过 [TopologyService.invalidateSkeletonCache] 立即失效拓扑骨架缓存，
 * 保证下一次拓扑查询能反映最新元数据。
 */
@Service
class ClusterNodeMetadataService(
    private val replClusterDao: ReplClusterDao,
    private val extensionDao: ClusterNodeExtensionDao,
    private val topologyService: TopologyService
) {

    /**
     * 列出所有长期组网节点 + 已补充的元数据。
     *
     * 未配置元数据的节点也会出现在返回列表中，对应字段为 null。
     */
    fun listAll(): List<ClusterNodeMetadataVO> {
        val longTermNodes = replClusterDao.listByTypes(LONG_TERM_TYPES)
        val extensions = extensionDao.findByClusterNames(longTermNodes.map { it.name })
            .associateBy { it.clusterName }
        return longTermNodes.map { node -> toVO(node, extensions[node.name]) }
    }

    /**
     * 更新指定节点的元数据。
     *
     * 当目标节点不存在或非长期组网节点时抛出 [IllegalArgumentException]。
     */
    fun update(clusterName: String, request: ClusterNodeMetadataUpdateRequest): ClusterNodeMetadataVO {
        val node = replClusterDao.findByName(clusterName)
            ?: throw IllegalArgumentException("cluster node not found: $clusterName")
        require(node.type != ClusterNodeType.REMOTE) {
            "cannot maintain metadata for REMOTE node: $clusterName"
        }
        val operator = SecurityUtils.getUserId()
        extensionDao.upsertByClusterName(
            clusterName = clusterName,
            region = request.region,
            networkZone = request.networkZone,
            displayName = request.displayName,
            description = request.description,
            operator = operator
        )
        // 立即失效拓扑骨架缓存
        topologyService.invalidateSkeletonCache()
        logger.info("[topology] cluster_node_extension upserted: clusterName={}, by={}", clusterName, operator)

        val ext = extensionDao.findByClusterName(clusterName)
        return toVO(node, ext)
    }

    private fun toVO(node: ClusterNodeRecord, ext: TClusterNodeExtension?): ClusterNodeMetadataVO {
        return ClusterNodeMetadataVO(
            clusterName = node.name,
            url = node.url,
            type = node.type,
            region = ext?.region,
            networkZone = ext?.networkZone,
            displayName = ext?.displayName,
            description = ext?.description,
            lastModifiedBy = ext?.lastModifiedBy,
            lastModifiedDate = ext?.lastModifiedDate
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClusterNodeMetadataService::class.java)
        private val LONG_TERM_TYPES = listOf(
            ClusterNodeType.CENTER,
            ClusterNodeType.EDGE,
            ClusterNodeType.STANDALONE
        )
    }
}
