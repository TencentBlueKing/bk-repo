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
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.dao.ClusterNodeDao
import com.tencent.bkrepo.replication.dao.FederatedRepositoryDao
import com.tencent.bkrepo.replication.dao.ReplicaTaskDao
import com.tencent.bkrepo.replication.metrics.TopologyMetrics
import com.tencent.bkrepo.replication.model.TFederatedRepository
import com.tencent.bkrepo.replication.model.TReplicaTask
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeName
import com.tencent.bkrepo.replication.pojo.federation.FederatedCluster
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.ReplicaStatus
import com.tencent.bkrepo.replication.pojo.task.setting.ReplicaSetting
import com.tencent.bkrepo.replication.service.topology.UpstreamEdgeService
import com.tencent.bkrepo.replication.service.topology.UpstreamEdgeService.LocalUpstreamEdge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

/**
 * UpstreamEdgeSyncJob 关键分组逻辑单元测试。
 *
 * 由于 Job 内含 Feign Client 跨集群推送，本测试只关注本地直写路径（applyLocal*）的行为正确性；
 * 跨集群推送侧通过未注册任何 ClusterNode（findByName 返回 null）让推送进入 catch-and-skip 路径，
 * 同时验证：
 *  - EDGE_PULL 任务被汇总成 LocalUpstreamEdge 调用 applyLocalEdgePullSnapshot
 *  - 推送类 ReplicaTask 不进入 EDGE_PULL 路径
 *  - 联邦镜像本地写覆盖了正确的成员集
 *  - 本地全无内容时 service 仍被调用且 summary 反馈 0
 */
@DisplayName("UpstreamEdgeSyncJob 单元测试")
class UpstreamEdgeSyncJobTest {

    private val replicaTaskDao: ReplicaTaskDao = mock()
    private val federatedRepositoryDao: FederatedRepositoryDao = mock()
    private val clusterNodeDao: ClusterNodeDao = mock()
    private val upstreamEdgeService: UpstreamEdgeService = mock()
    private val topologyMetrics: TopologyMetrics = mock()
    private val replicationProperties = ReplicationProperties()
    private val clusterProperties = ClusterProperties(
        role = ClusterNodeType.STANDALONE,
        self = ClusterInfo(name = "selfCluster", url = "http://self")
    )

    private lateinit var job: UpstreamEdgeSyncJob

    @BeforeEach
    fun setUp() {
        job = UpstreamEdgeSyncJob(
            replicaTaskDao = replicaTaskDao,
            federatedRepositoryDao = federatedRepositoryDao,
            clusterNodeDao = clusterNodeDao,
            upstreamEdgeService = upstreamEdgeService,
            clusterProperties = clusterProperties,
            replicationProperties = replicationProperties,
            topologyMetrics = topologyMetrics
        )
    }

    @Test
    fun `EDGE_PULL tasks should be applied as local EDGE_PULL snapshot only`() {
        whenever(replicaTaskDao.listByReplicaTypes(eq(setOf(ReplicaType.EDGE_PULL)), any())).doReturn(
            listOf(buildTask("k1", "n1", ReplicaType.EDGE_PULL, listOf("upstreamA")))
        )
        whenever(replicaTaskDao.listByReplicaTypes(
            eq(setOf(ReplicaType.SCHEDULED, ReplicaType.REAL_TIME, ReplicaType.RUN_ONCE)),
            any()
        )).doReturn(emptyList())
        whenever(federatedRepositoryDao.listAll()).doReturn(emptyList())

        job.runOnce()

        val captor = argumentCaptor<List<LocalUpstreamEdge>>()
        verify(upstreamEdgeService).applyLocalEdgePullSnapshot(captor.capture())
        val edges = captor.firstValue
        assertEquals(1, edges.size)
        assertEquals("upstreamA", edges[0].upstreamClusterName)
        assertEquals("k1", edges[0].replicaTaskKey)

        // 联邦镜像也会被调用（即使为空），保证范围式删除得以执行
        verify(upstreamEdgeService).applyLocalFederationMirror(eq(emptySet()), eq(emptyList()))
    }

    @Test
    fun `push tasks should not enter EDGE_PULL path`() {
        whenever(replicaTaskDao.listByReplicaTypes(eq(setOf(ReplicaType.EDGE_PULL)), any())).doReturn(emptyList())
        whenever(replicaTaskDao.listByReplicaTypes(
            eq(setOf(ReplicaType.SCHEDULED, ReplicaType.REAL_TIME, ReplicaType.RUN_ONCE)),
            any()
        )).doReturn(
            listOf(buildTask("k2", "n2", ReplicaType.SCHEDULED, listOf("targetX")))
        )
        whenever(federatedRepositoryDao.listAll()).doReturn(emptyList())
        // findByName(targetX) 返回 null 让 pushSnapshot 抛 IllegalStateException 并被 Job 捕获
        whenever(clusterNodeDao.findByName(any())).doReturn(null)

        val summary = job.runOnce()

        // EDGE_PULL 路径调用了一次（空列表）
        verify(upstreamEdgeService).applyLocalEdgePullSnapshot(eq(emptyList()))
        // 推送进入 catch 分支：pushFailed=1
        assertEquals(1, summary.pushFailed)
        assertEquals(0, summary.pushSucceeded)
    }

    @Test
    fun `federation mirror locally applied with member set scope`() {
        val targetCluster = stubCluster("peerY", "id-peer-y")
        whenever(replicaTaskDao.listByReplicaTypes(any(), any())).doReturn(emptyList())
        whenever(federatedRepositoryDao.listAll()).doReturn(
            listOf(
                buildFederation(
                    federationId = "fed-1",
                    projectId = "p1",
                    repoName = "r1",
                    selfClusterId = "id-self",
                    members = listOf(
                        FederatedCluster(
                            projectId = "p1",
                            repoName = "r1",
                            clusterId = "id-peer-y",
                            enabled = true
                        )
                    )
                )
            )
        )
        whenever(clusterNodeDao.findById(eq("id-peer-y"))).doReturn(targetCluster)
        whenever(clusterNodeDao.findByName(eq("peerY"))).doReturn(targetCluster)

        job.runOnce()

        val membersCaptor = argumentCaptor<Set<String>>()
        val edgesCaptor = argumentCaptor<List<LocalUpstreamEdge>>()
        verify(upstreamEdgeService).applyLocalFederationMirror(membersCaptor.capture(), edgesCaptor.capture())
        assertTrue(membersCaptor.firstValue.contains("peerY"))
        assertEquals(1, edgesCaptor.firstValue.size)
        assertEquals("peerY", edgesCaptor.firstValue[0].upstreamClusterName)
    }

    @Test
    fun `nothing to sync should still call services with empty inputs`() {
        whenever(replicaTaskDao.listByReplicaTypes(any(), any())).doReturn(emptyList())
        whenever(federatedRepositoryDao.listAll()).doReturn(emptyList())

        val summary = job.runOnce()

        verify(upstreamEdgeService).applyLocalEdgePullSnapshot(eq(emptyList()))
        verify(upstreamEdgeService).applyLocalFederationMirror(eq(emptySet()), eq(emptyList()))
        // 没有推送目标
        verify(upstreamEdgeService, never()).applyRemoteSnapshot(any(), any())
        assertEquals(0, summary.pushedEntries)
        assertEquals(0, summary.pushSucceeded)
        assertEquals(0, summary.pushFailed)
    }

    private fun buildTask(
        key: String,
        name: String,
        type: ReplicaType,
        targets: List<String>
    ): TReplicaTask {
        return TReplicaTask(
            key = key,
            name = name,
            projectId = "p",
            replicaObjectType = ReplicaObjectType.REPOSITORY,
            replicaType = type,
            setting = ReplicaSetting(),
            remoteClusters = targets.mapIndexed { i, n -> ClusterNodeName(id = "id-$i", name = n) }.toSet(),
            status = ReplicaStatus.WAITING,
            description = null,
            lastExecutionStatus = null,
            lastExecutionTime = null,
            nextExecutionTime = null,
            executionTimes = 0,
            enabled = true,
            record = false,
            recordReserveDays = 30,
            createdBy = "ut",
            createdDate = LocalDateTime.now(),
            lastModifiedBy = "ut",
            lastModifiedDate = LocalDateTime.now()
        )
    }

    private fun buildFederation(
        federationId: String,
        projectId: String,
        repoName: String,
        selfClusterId: String,
        members: List<FederatedCluster>
    ): TFederatedRepository {
        val now = LocalDateTime.now()
        return TFederatedRepository(
            createdBy = "ut",
            createdDate = now,
            lastModifiedBy = "ut",
            lastModifiedDate = now,
            projectId = projectId,
            repoName = repoName,
            clusterId = selfClusterId,
            federationId = federationId,
            name = "fed-$federationId",
            federatedClusters = members
        )
    }

    private fun stubCluster(name: String, id: String) = com.tencent.bkrepo.replication.model.TClusterNode(
        id = id,
        name = name,
        status = com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeStatus.HEALTHY,
        type = ClusterNodeType.STANDALONE,
        url = "http://$name",
        username = null,
        password = null,
        certificate = null,
        createdBy = "ut",
        createdDate = LocalDateTime.now(),
        lastModifiedBy = "ut",
        lastModifiedDate = LocalDateTime.now()
    )
}
