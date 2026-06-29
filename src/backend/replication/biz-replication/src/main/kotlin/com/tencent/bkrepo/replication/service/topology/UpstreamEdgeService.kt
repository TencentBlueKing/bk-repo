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

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.replication.dao.ClusterNodeDao
import com.tencent.bkrepo.replication.dao.UpstreamEdgeDao
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TUpstreamEdge
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeEntry
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeInfo
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeSourceType
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeStatus
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 上游边核心服务。
 *
 * 与上层职责：
 * - 远程快照写入：[applyRemoteSnapshot]，用于接收"推送方→本节点"的 REPLICA_PUSH 快照覆盖
 *   （sourceType=REPLICA_PUSH），严格不触碰本节点的 EDGE_PULL / FEDERATION 记录。
 *   FEDERATION 边由各成员节点本地直写维护（[applyLocalFederationMirror]），不再走远程推送。
 * - 本地直写：
 *   - [applyLocalEdgePullSnapshot]：覆盖 sourceType=EDGE_PULL 的全部记录。
 *   - [applyLocalFederationMirror]：覆盖本节点联邦成员集范围内的 sourceType=FEDERATION 记录。
 * - 查询：[listLocalEdges] 供链式追溯查询接口与运维接口共用。
 */
@Service
class UpstreamEdgeService(
    private val upstreamEdgeDao: UpstreamEdgeDao,
    private val clusterNodeDao: ClusterNodeDao
) {

    /**
     * 应用一份来自 [upstreamClusterName] 的远程 REPLICA_PUSH 快照。
     *
     * 步骤（顺序执行，保证最终一致）：
     * 1. 校验 [upstreamClusterName] 必须在本地 ClusterNode 已注册，未注册则拒绝并写审计日志；
     * 2. 拒绝快照中混入非 REPLICA_PUSH 的条目（EDGE_PULL / FEDERATION 都由本地直写维护）；
     * 3. 删除 (upstreamClusterName=upstreamClusterName, sourceType=REPLICA_PUSH) 的旧记录；
     * 4. 批量 upsert [entries]（保留本地直写的 EDGE_PULL / FEDERATION 记录不动）；
     * 5. 空快照同样会触发删除（确保删除场景最终一致）。
     *
     * @return 写入的条数（含被覆盖的）
     */
    fun applyRemoteSnapshot(upstreamClusterName: String, entries: List<UpstreamEdgeEntry>): Int {
        if (clusterNodeDao.findByName(upstreamClusterName) == null) {
            logger.warn(
                "[Audit] reject upstream-edge snapshot from unregistered cluster: $upstreamClusterName, " +
                    "entries=${entries.size}"
            )
            throw ErrorCodeException(
                ReplicationMessageCode.UPSTREAM_CLUSTER_NOT_REGISTERED,
                upstreamClusterName
            )
        }
        // 拒绝快照中混入非 REPLICA_PUSH 类型：EDGE_PULL 与 FEDERATION 都是本地直写范畴，
        // 不允许从远端覆盖，否则会与本节点的 applyLocalEdgePullSnapshot / applyLocalFederationMirror 互相覆盖。
        entries.firstOrNull { it.sourceType != UpstreamEdgeSourceType.REPLICA_PUSH }?.let {
            logger.warn(
                "[Audit] reject snapshot from $upstreamClusterName: contains non REPLICA_PUSH entry " +
                    "sourceType=${it.sourceType} replicaTaskKey=${it.replicaTaskKey} which must be local-only"
            )
            throw IllegalArgumentException("snapshot must only contain REPLICA_PUSH entries")
        }

        val deleted = upstreamEdgeDao.deleteByUpstreamAndSourceTypes(
            upstreamClusterName,
            REMOTE_SNAPSHOT_SOURCE_TYPES
        )
        val now = LocalDateTime.now()
        val records = entries.map { it.toRecord(upstreamClusterName, now) }
        if (records.isNotEmpty()) {
            upstreamEdgeDao.batchUpsert(records)
        }
        logger.info(
            "Applied remote snapshot from [$upstreamClusterName]: deleted=$deleted, upserted=${records.size}"
        )
        return records.size
    }

    /**
     * 覆盖本地全部 sourceType=EDGE_PULL 的边。
     *
     * 调用前提：[edges] 是本节点扫描本地 EDGE_PULL ReplicaTask 后组装的全量条目，
     * upstreamClusterName 取自 ReplicaTask.remoteClusters 中的拉取源。
     */
    fun applyLocalEdgePullSnapshot(edges: List<LocalUpstreamEdge>): Int {
        val deleted = upstreamEdgeDao.deleteAllByEdgePull()
        val now = LocalDateTime.now()
        val records = edges.map { it.toRecord(UpstreamEdgeSourceType.EDGE_PULL, now) }
        if (records.isNotEmpty()) {
            upstreamEdgeDao.batchUpsert(records)
        }
        logger.info("Applied local EDGE_PULL snapshot: deleted=$deleted, upserted=${records.size}")
        return records.size
    }

    /**
     * 覆盖本地 sourceType=FEDERATION 镜像边。
     *
     * 范围严格限定为 [federationMembers]（本节点所属联邦成员集），
     * 避免误删远端推送方写入的 FEDERATION 记录。
     */
    fun applyLocalFederationMirror(federationMembers: Set<String>, edges: List<LocalUpstreamEdge>): Int {
        if (federationMembers.isEmpty() && edges.isEmpty()) {
            return 0
        }
        val scope = federationMembers + edges.map { it.upstreamClusterName }
        val deleted = upstreamEdgeDao.deleteFederationByUpstreams(scope)
        val now = LocalDateTime.now()
        val records = edges.map { it.toRecord(UpstreamEdgeSourceType.FEDERATION, now) }
        if (records.isNotEmpty()) {
            upstreamEdgeDao.batchUpsert(records)
        }
        logger.info("Applied local FEDERATION mirror: deleted=$deleted, upserted=${records.size}")
        return records.size
    }

    /**
     * 查询本地全部边，供链式追溯第一跳读取。
     */
    fun listLocalEdges(includeDisabled: Boolean = false): List<TUpstreamEdge> {
        return if (includeDisabled) {
            upstreamEdgeDao.listByStatus(null)
        } else {
            upstreamEdgeDao.listByStatus(UpstreamEdgeStatus.ENABLED)
        }
    }

    /**
     * 分页查询本地边（运维接口）。
     */
    fun pageLocalEdges(includeDisabled: Boolean, pageNumber: Int, pageSize: Int): Page<UpstreamEdgeInfo> {
        val pageable = PageRequest.of(
            (pageNumber - 1).coerceAtLeast(0),
            pageSize,
            Sort.by(Sort.Direction.DESC, TUpstreamEdge::lastModifiedDate.name)
        )
        val total = upstreamEdgeDao.count(includeDisabled)
        val records = upstreamEdgeDao.page(includeDisabled, pageable).map { it.toInfo() }
        return Page(pageNumber, pageSize, total, records)
    }

    /**
     * 本地条目载体（在 Job 中组装后传入 service）。
     */
    data class LocalUpstreamEdge(
        val upstreamClusterName: String,
        val replicaTaskKey: String,
        val replicaTaskName: String? = null,
        val status: UpstreamEdgeStatus = UpstreamEdgeStatus.ENABLED
    ) {
        fun toRecord(sourceType: UpstreamEdgeSourceType, now: LocalDateTime): TUpstreamEdge {
            return TUpstreamEdge(
                upstreamClusterName = upstreamClusterName,
                replicaTaskKey = replicaTaskKey,
                replicaTaskName = replicaTaskName,
                sourceType = sourceType,
                status = status,
                lastSyncTime = now,
                createdDate = now,
                lastModifiedDate = now
            )
        }
    }

    private fun UpstreamEdgeEntry.toRecord(upstreamClusterName: String, now: LocalDateTime): TUpstreamEdge {
        return TUpstreamEdge(
            upstreamClusterName = upstreamClusterName,
            replicaTaskKey = replicaTaskKey,
            replicaTaskName = replicaTaskName,
            sourceType = sourceType,
            status = status,
            lastSyncTime = now,
            createdDate = now,
            lastModifiedDate = now
        )
    }

    private fun TUpstreamEdge.toInfo(): UpstreamEdgeInfo {
        return UpstreamEdgeInfo(
            id = id,
            upstreamClusterName = upstreamClusterName,
            replicaTaskKey = replicaTaskKey,
            replicaTaskName = replicaTaskName,
            sourceType = sourceType,
            status = status,
            lastSyncTime = lastSyncTime,
            createdDate = createdDate,
            lastModifiedDate = lastModifiedDate
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UpstreamEdgeService::class.java)
        private val REMOTE_SNAPSHOT_SOURCE_TYPES = setOf(
            UpstreamEdgeSourceType.REPLICA_PUSH
        )
    }
}
