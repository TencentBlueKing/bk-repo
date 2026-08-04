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

import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.opdata.cluster.topology.dao.ClusterNodeExtensionDao
import com.tencent.bkrepo.opdata.cluster.dao.ReplClusterDao
import com.tencent.bkrepo.opdata.cluster.dao.ReplTaskDao
import com.tencent.bkrepo.opdata.cluster.topology.model.TClusterNodeExtension
import com.tencent.bkrepo.opdata.cluster.topology.pojo.RemoteAggregationVO
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeRecord
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskRecord
import com.tencent.bkrepo.opdata.cluster.topology.pojo.TopologyChannelVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.TopologyNodeVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.TopologyVO
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * 拓扑数据构建服务。
 *
 * 基于 replication 模块的 cluster_node 与 replica_task 集合动态推导拓扑：
 * - 节点 = type ∈ {CENTER, EDGE, STANDALONE} 的 cluster_node 记录
 * - 通道 = 按 (源集群, remoteClusters[*].name) 聚合得到的边
 * - REMOTE 节点不出现在拓扑图中，单独以聚合气泡数据返回
 *
 * 拓扑骨架（不含流量）使用 30 秒本地缓存以承接 P95 ≤ 500ms 的接口要求。
 * 流量数据由 [TrafficStatsService] 单独负责，控制器层组合渲染。
 */
@Service
class TopologyService(
    private val replClusterDao: ReplClusterDao,
    private val replTaskDao: ReplTaskDao,
    private val extensionDao: ClusterNodeExtensionDao
) {

    private val skeletonCache = CacheBuilder.newBuilder()
        .expireAfterWrite(SKELETON_CACHE_TTL_SECONDS, TimeUnit.SECONDS)
        .maximumSize(8)
        .build<CacheKey, TopologyVO>()

    /**
     * 构建拓扑骨架数据。
     *
     * @param onlyEnabled 为 true 时仅纳入启用任务统计通道
     */
    fun buildTopology(onlyEnabled: Boolean = false): TopologyVO {
        val key = CacheKey(onlyEnabled)
        return skeletonCache.get(key) { doBuild(onlyEnabled) }
    }

    /**
     * 失效拓扑骨架缓存，元数据更新后调用。
     */
    fun invalidateSkeletonCache() {
        skeletonCache.invalidateAll()
    }

    private fun doBuild(onlyEnabled: Boolean): TopologyVO {
        val allNodes = replClusterDao.listAll()
        val nodesByName = allNodes.associateBy { it.name }
        val longTermNodes = allNodes.filter { it.type != ClusterNodeType.REMOTE }
        val remoteNodeNames = allNodes.filter { it.type == ClusterNodeType.REMOTE }
            .mapTo(mutableSetOf()) { it.name }

        // 加载元数据并合并到节点 VO
        val extensions = extensionDao.findByClusterNames(longTermNodes.map { it.name })
            .associateBy { it.clusterName }
        val nodeVOs = longTermNodes.map { node -> toNodeVO(node, extensions[node.name]) }

        // 加载任务并按是否关联 REMOTE 拆分
        val allTasks = replTaskDao.listAll()
        val candidateTasks = if (onlyEnabled) allTasks.filter { it.enabled } else allTasks
        val (remoteTasks, longTermTasks) = candidateTasks.partition { task ->
            task.remoteClusters.any { ref -> ref.name != null && ref.name in remoteNodeNames }
        }

        // 通道聚合（仅长期组网节点之间）
        val channels = aggregateChannels(longTermTasks, nodesByName, remoteNodeNames)

        // REMOTE 聚合
        val remoteSummary = aggregateRemote(remoteTasks, remoteNodeNames.size.toLong())

        return TopologyVO(nodes = nodeVOs, channels = channels, remoteSummary = remoteSummary)
    }

    private fun toNodeVO(node: ClusterNodeRecord, ext: TClusterNodeExtension?): TopologyNodeVO {
        return TopologyNodeVO(
            id = node.name,
            name = node.name,
            url = node.url,
            type = node.type,
            status = node.status,
            errorReason = node.errorReason,
            lastReportTime = node.lastReportTime,
            region = ext?.region,
            networkZone = ext?.networkZone,
            displayName = ext?.displayName ?: node.name,
            description = ext?.description
        )
    }

    private fun aggregateChannels(
        tasks: List<ReplicaTaskRecord>,
        nodesByName: Map<String, ClusterNodeRecord>,
        remoteNodeNames: Set<String>
    ): List<TopologyChannelVO> {
        // key = sourceCluster -> targetCluster
        val grouped = mutableMapOf<Pair<String, String>, MutableList<ReplicaTaskRecord>>()

        // 注：源集群在数据库中没有显式字段，按当前 replication 设计：
        // 任务由"本集群"创建并指向 remoteClusters。这里我们将"源集群"标记为
        // 集合中存在的 CENTER 节点（兜底为第一个长期组网节点）来近似展示。
        // 真实部署中可结合配置或扩展字段进一步细化。
        val defaultSource = nodesByName.values
            .firstOrNull { it.type == ClusterNodeType.CENTER }
            ?.name
            ?: nodesByName.values.firstOrNull { it.type != ClusterNodeType.REMOTE }?.name

        tasks.forEach { task ->
            val source = defaultSource ?: return@forEach
            task.remoteClusters.forEach { ref ->
                val targetName = ref.name ?: return@forEach
                if (targetName in remoteNodeNames) return@forEach
                if (targetName !in nodesByName) return@forEach
                if (targetName == source) return@forEach
                grouped.getOrPut(source to targetName) { mutableListOf() }.add(task)
            }
        }

        return grouped.map { (pair, tasksOnChannel) ->
            val (source, target) = pair
            val replicaTypes = tasksOnChannel.mapNotNull { it.replicaType }.toSet()
            val active = tasksOnChannel.count { it.enabled }
            val total = tasksOnChannel.size
            TopologyChannelVO(
                id = "$source->$target",
                sourceCluster = source,
                targetCluster = target,
                replicaTypes = replicaTypes,
                totalTaskCount = total,
                activeTaskCount = active,
                allDisabled = active == 0 && total > 0,
                recentTrafficBytes = null
            )
        }
    }

    private fun aggregateRemote(
        remoteTasks: List<ReplicaTaskRecord>,
        remoteNodeCount: Long
    ): RemoteAggregationVO {
        val active = remoteTasks.count { it.enabled }.toLong()
        val completed = (remoteTasks.size - active).coerceAtLeast(0).toLong()
        return RemoteAggregationVO(
            remoteNodeCount = remoteNodeCount,
            activeRemoteTaskCount = active,
            completedRemoteTaskCount = completed
        )
    }

    private data class CacheKey(val onlyEnabled: Boolean)

    companion object {
        private val logger = LoggerFactory.getLogger(TopologyService::class.java)
        private const val SKELETON_CACHE_TTL_SECONDS: Long = 30
    }
}
