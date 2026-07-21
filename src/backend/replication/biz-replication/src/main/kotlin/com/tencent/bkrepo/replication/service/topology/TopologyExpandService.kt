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

package com.tencent.bkrepo.replication.service.topology

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.replication.api.cluster.ClusterTopologyClient
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.dao.ClusterNodeDao
import com.tencent.bkrepo.replication.model.TClusterNode
import com.tencent.bkrepo.replication.model.TUpstreamEdge
import com.tencent.bkrepo.replication.pojo.topology.TopologyNode
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeStatus
import feign.FeignException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 跨集群递归展开服务。
 *
 * 设计要点：
 *  - 同 traceId 内对同一 clusterName 的展开结果做请求级缓存（菱形拓扑去重，避免重复 RPC）；
 *  - 同层多个上游并行展开（最大并发数受 [ReplicationProperties.topology.expandConcurrency] 控制）；
 *  - 单跳 RPC 超时由 [ReplicationProperties.topology.rpcTimeout] 控制（默认 3s）；
 *  - RPC 失败 → unreachable=true；老版本节点（404/405）→ legacy=true；深度耗尽 → truncated=true；
 *    任一原因都不会中断同层其他分支。
 */
@Service
class TopologyExpandService(
    private val clusterNodeDao: ClusterNodeDao,
    private val clusterProperties: ClusterProperties,
    private val replicationProperties: ReplicationProperties
) {

    /**
     * 顶层入口：从根节点开始递归展开。
     */
    fun expandFromRoot(
        rootClusterName: String,
        rootUrl: String?,
        rootType: ClusterNodeType?,
        localEdges: List<TUpstreamEdge>,
        maxDepth: Int,
        includeDisabled: Boolean,
        traceId: String
    ): TopologyNode {
        val ctx = ExpandContext(
            traceId = traceId,
            maxDepth = maxDepth,
            includeDisabled = includeDisabled,
            cache = ConcurrentHashMap()
        )
        val children = expandChildren(
            ctx = ctx,
            edges = localEdges,
            parentDepth = 0
        )
        return TopologyNode(
            clusterName = rootClusterName,
            url = rootUrl,
            type = rootType,
            depth = 0,
            children = children
        )
    }

    private fun expandChildren(
        ctx: ExpandContext,
        edges: List<TUpstreamEdge>,
        parentDepth: Int
    ): List<TopologyNode> {
        if (edges.isEmpty()) return emptyList()
        val childDepth = parentDepth + 1
        // 同层并行展开
        val limiter = Semaphore(replicationProperties.topology.expandConcurrency.coerceAtLeast(1))
        val futures = edges.map { edge ->
            CompletableFuture.supplyAsync({
                limiter.acquire()
                try {
                    expandSingle(ctx, edge, childDepth)
                } finally {
                    limiter.release()
                }
            }, EXECUTOR)
        }
        return futures.map { f ->
            try {
                f.get()
            } catch (e: ExecutionException) {
                logger.warn("topology expand task failed: ${e.cause?.message}", e.cause)
                TopologyNode(
                    clusterName = "<unknown>",
                    depth = childDepth,
                    unreachable = true,
                    unreachableReason = e.cause?.javaClass?.simpleName + ": " + e.cause?.message
                )
            }
        }
    }

    /**
     * 展开一条边到具体的子节点。
     */
    private fun expandSingle(ctx: ExpandContext, edge: TUpstreamEdge, depth: Int): TopologyNode {
        val baseNode = TopologyNode(
            clusterName = edge.upstreamClusterName,
            replicaTaskKey = edge.replicaTaskKey,
            sourceType = edge.sourceType,
            status = edge.status,
            depth = depth,
            disabled = ctx.includeDisabled && edge.status == UpstreamEdgeStatus.DISABLED,
            lastSyncTime = edge.lastSyncTime
        )

        // 深度耗尽：到达 maxDepth 这一层即截断
        if (depth >= ctx.maxDepth) {
            return baseNode.copy(truncated = true)
        }

        // 菱形拓扑请求级缓存：同 traceId 内同一 clusterName 命中即复用
        val cached = ctx.cache[edge.upstreamClusterName]
        if (cached != null) {
            return baseNode.copy(
                url = cached.url,
                type = cached.type,
                children = cached.children,
                truncated = cached.truncated,
                unreachable = cached.unreachable,
                unreachableReason = cached.unreachableReason,
                legacy = cached.legacy
            )
        }

        val clusterNode = clusterNodeDao.findByName(edge.upstreamClusterName)
        if (clusterNode == null) {
            val node = baseNode.copy(
                unreachable = true,
                unreachableReason = "cluster_node not registered locally"
            )
            ctx.cache[edge.upstreamClusterName] = node
            return node
        }

        val resolved = try {
            invokeUpstream(clusterNode, ctx, depth)
        } catch (e: FeignException.NotFound) {
            baseNode.copy(
                url = clusterNode.url,
                type = clusterNode.type,
                legacy = true,
                unreachableReason = "404 NotFound (legacy)"
            )
        } catch (e: FeignException.MethodNotAllowed) {
            baseNode.copy(
                url = clusterNode.url,
                type = clusterNode.type,
                legacy = true,
                unreachableReason = "405 MethodNotAllowed (legacy)"
            )
        } catch (e: TimeoutException) {
            baseNode.copy(
                url = clusterNode.url,
                type = clusterNode.type,
                unreachable = true,
                unreachableReason = "rpc timeout: ${e.message}"
            )
        } catch (e: Exception) {
            logger.warn(
                "topology expand to [${edge.upstreamClusterName}] failed: ${e.javaClass.simpleName} ${e.message}"
            )
            baseNode.copy(
                url = clusterNode.url,
                type = clusterNode.type,
                unreachable = true,
                unreachableReason = "${e.javaClass.simpleName}: ${e.message}"
            )
        }
        ctx.cache[edge.upstreamClusterName] = resolved
        return resolved
    }

    /**
     * 跨集群一次 RPC：调用上游 `_internal=true` 模式取其本地一跳，再就地继续递归。
     */
    private fun invokeUpstream(
        clusterNode: TClusterNode,
        ctx: ExpandContext,
        currentDepth: Int
    ): TopologyNode {
        val client = FeignClientFactory.create<ClusterTopologyClient>(
            clusterNode.toClusterInfo(),
            "replication",
            clusterProperties.self.name
        )
        val rpcTimeoutMillis = replicationProperties.topology.rpcTimeout.toMillis()

        val future = CompletableFuture.supplyAsync(
            {
                client.expandUpstream(
                    maxDepth = (ctx.maxDepth - currentDepth).coerceAtLeast(1),
                    includeDisabled = ctx.includeDisabled,
                    internalCall = true,
                    traceId = ctx.traceId,
                    depth = currentDepth
                )
            },
            EXECUTOR
        )
        val response = try {
            future.get(rpcTimeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw e
        } catch (e: ExecutionException) {
            // 解包真实异常以便上层精确识别 FeignException 子类型
            throw e.cause ?: e
        }

        val remoteRoot = response.data ?: return TopologyNode(
            clusterName = clusterNode.name,
            url = clusterNode.url,
            type = clusterNode.type,
            depth = currentDepth,
            unreachable = true,
            unreachableReason = "remote response data is null"
        )

        // 远端返回的 children 是它的本地一跳（未递归）；继续在本地驱动下一层递归
        val grandChildren = remoteRoot.children.map { childLeaf ->
            val edge = TUpstreamEdge(
                upstreamClusterName = childLeaf.clusterName,
                replicaTaskKey = childLeaf.replicaTaskKey ?: "",
                replicaTaskName = null,
                sourceType = childLeaf.sourceType ?: edgeFallbackSourceType(),
                status = childLeaf.status ?: UpstreamEdgeStatus.ENABLED,
                lastSyncTime = childLeaf.lastSyncTime ?: java.time.LocalDateTime.now(),
                createdDate = java.time.LocalDateTime.now(),
                lastModifiedDate = java.time.LocalDateTime.now()
            )
            expandSingle(ctx, edge, currentDepth + 1)
        }

        return TopologyNode(
            clusterName = clusterNode.name,
            url = clusterNode.url,
            type = clusterNode.type,
            depth = currentDepth,
            children = grandChildren
        )
    }

    private fun edgeFallbackSourceType() =
        com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeSourceType.REPLICA_PUSH

    private fun TClusterNode.toClusterInfo(): ClusterInfo {
        return ClusterInfo(
            name = name,
            url = url,
            username = username,
            password = password,
            certificate = certificate,
            appId = appId,
            accessKey = accessKey,
            secretKey = secretKey,
            udpPort = udpPort
        )
    }

    /**
     * 单次顶层查询级别的递归展开上下文。
     */
    private data class ExpandContext(
        val traceId: String,
        val maxDepth: Int,
        val includeDisabled: Boolean,
        val cache: ConcurrentHashMap<String, TopologyNode>
    )

    companion object {
        private val logger = LoggerFactory.getLogger(TopologyExpandService::class.java)

        /**
         * 展开任务专用线程池；同顶层查询级 traceId 内会有大量并行 RPC，使用 cached 线程池避免阻塞 IO 拖累主流程。
         */
        private val EXECUTOR = Executors.newCachedThreadPool { r ->
            Thread(r, "topology-expand-" + UUID.randomUUID().toString().take(8)).apply { isDaemon = true }
        }
    }
}
