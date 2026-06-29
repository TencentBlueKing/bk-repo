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

package com.tencent.bkrepo.replication.job.topology

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.exception.RemoteErrorCodeException
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.replication.api.cluster.ClusterTopologyClient
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.dao.ClusterNodeDao
import com.tencent.bkrepo.replication.dao.FederatedRepositoryDao
import com.tencent.bkrepo.replication.dao.ReplicaTaskDao
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.metrics.TopologyMetrics
import com.tencent.bkrepo.replication.model.TClusterNode
import com.tencent.bkrepo.replication.model.TFederatedRepository
import com.tencent.bkrepo.replication.model.TReplicaTask
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeEntry
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeSourceType
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeStatus
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeSyncRequest
import com.tencent.bkrepo.replication.service.topology.UpstreamEdgeService
import com.tencent.bkrepo.replication.service.topology.UpstreamEdgeService.LocalUpstreamEdge
import feign.FeignException
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 链式拓扑追溯 - 上游边周期同步任务。
 *
 * 每个周期内顺序执行两条路径：
 *  - 本地直写（先执行）：扫描本地 EDGE_PULL ReplicaTask + 本地 FederatedRepository 镜像，
 *    直接覆盖本地 t_upstream_edge 中相应的 EDGE_PULL / FEDERATION 镜像范围。
 *  - 跨集群推送（后执行）：扫描本地推送类 ReplicaTask + FederatedRepository 中除自己外的成员，
 *    按目标集群分组组装 [UpstreamEdgeSyncRequest] 推送给对方。即使本周期对某目标已无任何条目，
 *    也会推送空快照，以确保删除场景最终一致。
 *
 * 不依赖也不区分本节点或对端节点的 ClusterNodeType，所有部署形态统一走本任务。
 */
@Component
class UpstreamEdgeSyncJob(
    private val replicaTaskDao: ReplicaTaskDao,
    private val federatedRepositoryDao: FederatedRepositoryDao,
    private val clusterNodeDao: ClusterNodeDao,
    private val upstreamEdgeService: UpstreamEdgeService,
    private val clusterProperties: ClusterProperties,
    @Suppress("unused") private val replicationProperties: ReplicationProperties,
    private val topologyMetrics: TopologyMetrics
) {

    /**
     * 是否已经对未升级的某目标打过 INFO 日志（避免每周期刷屏）。
     */
    private val legacyLoggedTargets = ConcurrentHashMap.newKeySet<String>()

    /**
     * 是否已经对"对端未在其本地 cluster_node 注册本节点"的某目标打过 INFO 日志（避免每周期刷屏）。
     */
    private val unregisteredLoggedTargets = ConcurrentHashMap.newKeySet<String>()

    /**
     * 默认 5 分钟一次；可通过配置 `replication.topology.sync-interval` 用 ISO-8601 Duration 覆盖
     * （如 PT30M / PT2H）。这里用配置占位符而不是 SpEL bean 引用，避免依赖
     * @ConfigurationProperties 注册的 bean 名（其默认 bean 名是类全限定名，不是 `replicationProperties`）。
     */
    @Scheduled(fixedDelayString = "\${replication.topology.sync-interval:PT5M}")
    @SchedulerLock(name = "UpstreamEdgeSyncJob", lockAtMostFor = "PT2H")
    fun scheduledRun() {
        logger.info("[UpstreamEdgeSyncJob] scheduled trigger fired")
        runOnce()
    }

    /**
     * 主入口；运维接口可以直接调用以提前触发本周期。
     *
     * 每次执行会生成一个 8 位短 cycleId，并以 `[UpstreamEdgeSyncJob][cycleId]` 作为日志前缀，
     * 多周期日志交错时通过 `grep '[cycleId]'` 即可拉出完整链路。
     */
    fun runOnce(): SyncSummary {
        val started = System.currentTimeMillis()
        val cycleId = UUID.randomUUID().toString().take(8)
        val selfName = clusterProperties.self.name
        if (selfName.isNullOrBlank()) {
            logger.warn("[UpstreamEdgeSyncJob][$cycleId] skipped: cluster.self.name is not configured")
            return SyncSummary()
        }
        logger.info("[UpstreamEdgeSyncJob][$cycleId] cycle start, self=$selfName")
        val summary = SyncSummary()
        try {
            applyLocalPaths(selfName, summary, cycleId)
        } catch (e: Exception) {
            logger.warn(
                "[UpstreamEdgeSyncJob][$cycleId] local-write path failed, will continue with remote push: " +
                    "exception=${e.javaClass.simpleName} message=${e.message}",
                e
            )
            summary.localFailed = true
        }
        try {
            applyRemotePaths(selfName, summary, cycleId)
        } catch (e: Exception) {
            logger.warn(
                "[UpstreamEdgeSyncJob][$cycleId] remote-push path failed: " +
                    "exception=${e.javaClass.simpleName} message=${e.message}",
                e
            )
            summary.remoteFailed = true
        }
        val cost = System.currentTimeMillis() - started
        if (summary.totalSourceItems() == 0) {
            logger.info("[UpstreamEdgeSyncJob][$cycleId] nothing to sync this cycle (cost=${cost}ms)")
        } else {
            logger.info("[UpstreamEdgeSyncJob][$cycleId] done: $summary, cost=${cost}ms")
        }
        topologyMetrics.recordSyncDuration(cost * 1_000_000L)
        topologyMetrics.incSyncResult(!summary.localFailed && !summary.remoteFailed)
        return summary
    }

    /**
     * 本地直写路径（EDGE_PULL + FEDERATION 镜像）。
     */
    private fun applyLocalPaths(selfName: String, summary: SyncSummary, cycleId: String) {
        val localStart = System.currentTimeMillis()
        // 1. EDGE_PULL：本节点向远端拉取，A→B 关系数据本来就住在本节点
        val edgePullTasks = replicaTaskDao.listByReplicaTypes(setOf(ReplicaType.EDGE_PULL))
        val edgePullEdges = edgePullTasks.flatMap { task -> task.toEdgePullLocalEdges() }
        logger.info(
            "[UpstreamEdgeSyncJob][$cycleId] local-write start: edgePullTasks=${edgePullTasks.size} " +
                "edgePullEdges=${edgePullEdges.size}"
        )
        upstreamEdgeService.applyLocalEdgePullSnapshot(edgePullEdges)
        summary.edgePullCount = edgePullEdges.size

        // 2. FEDERATION 镜像：本地写入"本节点联邦成员集"内的 FEDERATION 边
        val federations = federatedRepositoryDao.listAll()
        val federationLocalEdges = federations.flatMap { fr -> fr.toFederationMirrorEdges(selfName) }
        val federationMemberNames = federationLocalEdges.map { it.upstreamClusterName }.toSet()
        upstreamEdgeService.applyLocalFederationMirror(federationMemberNames, federationLocalEdges)
        summary.federationLocalCount = federationLocalEdges.size

        // 详细打印：每个联邦配置的对端列表（含 enabled 状态），便于排查"为什么某个集群没有出现在 memberNames"
        val federationDetails = federations.map { fr ->
            val peers = fr.federatedClusters.joinToString(",") { fc ->
                val peerName = clusterNodeDao.findById(fc.clusterId)?.name ?: "id=${fc.clusterId}"
                "$peerName(${if (fc.enabled) "enabled" else "disabled"})"
            }
            "${fr.projectId}/${fr.repoName}#${fr.federationId}@${fr.clusterId}->[$peers]"
        }
        // 详细打印：本地写入的每条 FEDERATION 边（按 upstream 分组），可定位"哪条 repo 把哪个集群带成上游"
        val edgesByUpstream = federationLocalEdges.groupBy { it.upstreamClusterName }
            .mapValues { (_, edges) -> edges.map { "${it.replicaTaskKey}(${it.status})" } }
        logger.info(
            "[UpstreamEdgeSyncJob][$cycleId] local-write done: federations=${federations.size} " +
                "federationLocalEdges=${federationLocalEdges.size} memberNames=${federationMemberNames.size} " +
                "cost=${System.currentTimeMillis() - localStart}ms\n" +
                "  federations.detail=${truncate(federationDetails)}\n" +
                "  memberNames.detail=$federationMemberNames\n" +
                "  federationLocalEdges.detail=${truncate(edgesByUpstream.entries.map { (k, v) -> "$k->${truncate(v)}" })}"
        )
    }

    /**
     * 日志截断工具：列表过长时只打印前 [max] 条 + 省略提示，避免成百上千联邦配置刷爆日志。
     */
    private fun <T> truncate(list: List<T>, max: Int = 50): String {
        if (list.size <= max) return list.toString()
        return list.take(max).toString().dropLast(1) + ", ...(+${list.size - max} more)]"
    }

    /**
     * 跨集群推送路径：扫描本地"指向其它集群"的全部 REPLICA_PUSH 边，
     * 按目标集群分组后向其推送快照。即便某目标本周期无任何条目，也会推送空快照。
     *
     * 注意：FEDERATION 边不在此路径推送。原因是每个联邦成员节点本地都持有完整的
     * [TFederatedRepository] 副本（创建/更新联邦时双向同步），各节点都能从本地数据推导出
     * "对端是我的 FEDERATION 上游"这一事实，因此 FEDERATION 边的写入完全交由
     * [applyLocalPaths] 里的 `applyLocalFederationMirror` 负责，避免双源写入与互相覆盖。
     */
    private fun applyRemotePaths(selfName: String, summary: SyncSummary, cycleId: String) {
        val remoteStart = System.currentTimeMillis()
        val pushTypes = setOf(ReplicaType.SCHEDULED, ReplicaType.REAL_TIME, ReplicaType.RUN_ONCE)
        val pushTasks = replicaTaskDao.listByReplicaTypes(pushTypes)

        val grouped = mutableMapOf<String, MutableList<UpstreamEdgeEntry>>()

        // 推送类 ReplicaTask：每条任务的每个 remoteCluster 都是一个目标。
        // REMOTE 类型节点是外部仓库（Docker/Helm/Maven/Hub/JFrog…），没有 bk-repo 私有的
        // /api/replication/topology/upstream-edges/sync 接口，强行推送只会触发
        // Connect timed out / 404 / 405 等无效流量并污染统计，因此直接跳过。
        pushTasks.forEach { task ->
            task.remoteClusters.forEach { rc ->
                val targetName = rc.name.takeIf { it.isNotBlank() } ?: return@forEach
                if (targetName == selfName) return@forEach
                val targetNode = clusterNodeDao.findByName(targetName) ?: return@forEach
                if (targetNode.type == ClusterNodeType.REMOTE) {
                    summary.pushSkippedRemote++
                    return@forEach
                }
                grouped.getOrPut(targetName) { mutableListOf() }.add(
                    UpstreamEdgeEntry(
                        replicaTaskKey = task.key,
                        replicaTaskName = task.name,
                        sourceType = UpstreamEdgeSourceType.REPLICA_PUSH,
                        status = if (task.enabled) UpstreamEdgeStatus.ENABLED else UpstreamEdgeStatus.DISABLED
                    )
                )
            }
        }

        if (grouped.isEmpty()) {
            logger.info(
                "[UpstreamEdgeSyncJob][$cycleId] remote-push skipped: no targets, " +
                    "pushTasks=${pushTasks.size} skippedRemote=${summary.pushSkippedRemote}"
            )
            return
        }

        logger.info(
            "[UpstreamEdgeSyncJob][$cycleId] remote-push start: targets=${grouped.size} " +
                "totalEntries=${grouped.values.sumOf { it.size }} " +
                "pushTasks=${pushTasks.size} skippedRemote=${summary.pushSkippedRemote}"
        )

        val now = LocalDateTime.now()
        grouped.forEach { (targetName, entries) ->
            val targetNode = clusterNodeDao.findByName(targetName)
            val targetType = targetNode?.type
            val targetUrl = targetNode?.url
            val targetStart = System.currentTimeMillis()
            logger.debug(
                "[UpstreamEdgeSyncJob][$cycleId] -> push start target=$targetName type=$targetType " +
                    "url=$targetUrl entries=${entries.size}"
            )
            try {
                pushSnapshot(targetName, UpstreamEdgeSyncRequest(selfName, entries, now))
                summary.pushSucceeded++
                summary.pushedEntries += entries.size
                logger.info(
                    "[UpstreamEdgeSyncJob][$cycleId] -> push ok    target=$targetName type=$targetType " +
                        "entries=${entries.size} cost=${System.currentTimeMillis() - targetStart}ms"
                )
            } catch (e: FeignException.NotFound) {
                onLegacyTarget(targetName, e)
                summary.pushSkippedLegacy++
            } catch (e: FeignException.MethodNotAllowed) {
                onLegacyTarget(targetName, e)
                summary.pushSkippedLegacy++
            } catch (e: RemoteErrorCodeException) {
                if (e.errorCode == ReplicationMessageCode.UPSTREAM_CLUSTER_NOT_REGISTERED.getCode()) {
                    onUnregisteredTarget(
                        cycleId = cycleId,
                        selfName = selfName,
                        targetName = targetName,
                        targetType = targetType?.name,
                        targetUrl = targetUrl,
                        entries = entries,
                        cost = System.currentTimeMillis() - targetStart,
                        e = e
                    )
                    summary.pushSkippedUnregistered++
                } else {
                    logger.warn(
                        "[UpstreamEdgeSyncJob][$cycleId] -> push FAIL  target=$targetName type=$targetType " +
                            "url=$targetUrl entries=${entries.size} " +
                            "cost=${System.currentTimeMillis() - targetStart}ms " +
                            "exception=RemoteErrorCodeException errorCode=${e.errorCode} message=${e.message}"
                    )
                    summary.pushFailed++
                }
            } catch (e: Exception) {
                val httpStatus = (e as? FeignException)?.status()
                logger.warn(
                    "[UpstreamEdgeSyncJob][$cycleId] -> push FAIL  target=$targetName type=$targetType " +
                        "url=$targetUrl entries=${entries.size} " +
                        "cost=${System.currentTimeMillis() - targetStart}ms " +
                        "exception=${e.javaClass.simpleName} status=$httpStatus message=${e.message}"
                )
                summary.pushFailed++
            }
        }
        logger.info(
            "[UpstreamEdgeSyncJob][$cycleId] remote-push done: targets=${grouped.size} " +
                "succ=${summary.pushSucceeded} fail=${summary.pushFailed} legacy=${summary.pushSkippedLegacy} " +
                "unregistered=${summary.pushSkippedUnregistered} " +
                "cost=${System.currentTimeMillis() - remoteStart}ms"
        )
    }

    private fun onLegacyTarget(targetName: String, e: Exception) {
        if (legacyLoggedTargets.add(targetName)) {
            logger.info(
                "Target cluster [$targetName] does not support upstream-edge sync (legacy version): ${e.message}; " +
                    "skip silently for subsequent cycles."
            )
        }
    }

    /**
     * 对端拒绝原因：本节点未在对端的 cluster_node 中注册。
     *
     * 这是"双向链路漏配"，本节点已把对端登记，但对端没把本节点登记。这类错误每个周期都会复现，
     * 直到对端运维显式注册。
     *
     * 日志策略：
     *  - 首次命中某目标：以 INFO 打印完整的排查信息（cycleId / self / target 元信息 / 本周期 entries 摘要 /
     *    errorCode / 原始 message / 处置说明），便于一眼看出是哪个集群、配了哪些上游、对端拒绝了多少条；
     *  - 后续周期同一目标：降级为 DEBUG，避免日志刷屏；如需重新出 INFO，可在运维侧重启服务或清空
     *    [unregisteredLoggedTargets]。
     */
    private fun onUnregisteredTarget(
        cycleId: String,
        selfName: String,
        targetName: String,
        targetType: String?,
        targetUrl: String?,
        entries: List<UpstreamEdgeEntry>,
        cost: Long,
        e: RemoteErrorCodeException
    ) {
        // entries 摘要：按 sourceType 计数，并打印每条的 key + status，便于定位"哪几条边被对端拒绝"
        val bySourceType = entries.groupingBy { it.sourceType }.eachCount()
        val entryDetail = entries.joinToString(",") {
            "${it.sourceType}:${it.replicaTaskKey}(${it.status})"
        }
        val firstSeen = unregisteredLoggedTargets.add(targetName)
        val baseMsg = "[UpstreamEdgeSyncJob][$cycleId] -> push SKIP(unregistered) " +
            "target=$targetName type=$targetType url=$targetUrl " +
            "self=$selfName entries=${entries.size} bySourceType=$bySourceType " +
            "cost=${cost}ms errorCode=${e.errorCode} message=${e.message} " +
            "hint=peer cluster_node has no entry for [$selfName], ask peer ops to register us; " +
            "entries.detail=[$entryDetail]"
        if (firstSeen) {
            logger.info("$baseMsg (logged once per target; subsequent cycles will be DEBUG)")
        } else {
            logger.debug(baseMsg)
        }
    }

    private fun pushSnapshot(targetName: String, request: UpstreamEdgeSyncRequest) {
        val target = clusterNodeDao.findByName(targetName)
            ?: throw IllegalStateException("target cluster [$targetName] not found in local cluster_node")
        val client = FeignClientFactory.create<ClusterTopologyClient>(
            target.toClusterInfo(),
            "replication",
            clusterProperties.self.name
        )
        client.syncUpstreamEdges(request)
    }

    private fun TReplicaTask.toEdgePullLocalEdges(): List<LocalUpstreamEdge> {
        return remoteClusters.mapNotNull { rc ->
            val upstreamName = rc.name.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            LocalUpstreamEdge(
                upstreamClusterName = upstreamName,
                replicaTaskKey = key,
                replicaTaskName = name,
                status = if (enabled) UpstreamEdgeStatus.ENABLED else UpstreamEdgeStatus.DISABLED
            )
        }
    }

    private fun TFederatedRepository.toFederationMirrorEdges(selfName: String): List<LocalUpstreamEdge> {
        return federatedClusters.mapNotNull { fc ->
            val target = clusterNodeDao.findById(fc.clusterId) ?: return@mapNotNull null
            if (target.name == selfName) return@mapNotNull null
            LocalUpstreamEdge(
                upstreamClusterName = target.name,
                replicaTaskKey = federationKey(this, fc.clusterId),
                replicaTaskName = this.name,
                status = if (fc.enabled) UpstreamEdgeStatus.ENABLED else UpstreamEdgeStatus.DISABLED
            )
        }
    }

    private fun federationKey(fr: TFederatedRepository, peerClusterId: String): String {
        return "fed:${fr.federationId}:${fr.projectId}/${fr.repoName}:${fr.clusterId}->$peerClusterId"
    }

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
     * 同步执行结果汇总。
     *
     * - pushSkippedLegacy：对端 bk-repo 但版本未升级（404/405），后续周期会静默跳过；
     * - pushSkippedRemote：目标 ClusterNode 类型为 REMOTE（外部仓库），主动跳过，永远不推送；
     * - pushSkippedUnregistered：对端业务拒绝（错误码 UPSTREAM_CLUSTER_NOT_REGISTERED），
     *   说明本节点未在对端 cluster_node 中注册，需要对端运维介入；本端按目标静默 INFO 一次。
     */
    data class SyncSummary(
        var edgePullCount: Int = 0,
        var federationLocalCount: Int = 0,
        var pushedEntries: Int = 0,
        var pushSucceeded: Int = 0,
        var pushFailed: Int = 0,
        var pushSkippedLegacy: Int = 0,
        var pushSkippedRemote: Int = 0,
        var pushSkippedUnregistered: Int = 0,
        var localFailed: Boolean = false,
        var remoteFailed: Boolean = false
    ) {
        fun totalSourceItems(): Int = edgePullCount + federationLocalCount + pushedEntries
        override fun toString(): String {
            return "edgePull=$edgePullCount federationLocal=$federationLocalCount " +
                "push.entries=$pushedEntries push.succ=$pushSucceeded " +
                "push.fail=$pushFailed push.legacy=$pushSkippedLegacy " +
                "push.skippedRemote=$pushSkippedRemote " +
                "push.skippedUnregistered=$pushSkippedUnregistered " +
                "localFailed=$localFailed remoteFailed=$remoteFailed"
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UpstreamEdgeSyncJob::class.java)
    }
}
