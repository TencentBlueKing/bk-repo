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
import com.tencent.bkrepo.opdata.cluster.topology.dao.ClusterNodeExtensionDao
import com.tencent.bkrepo.opdata.cluster.dao.ReplClusterDao
import com.tencent.bkrepo.opdata.cluster.dao.ReplTaskDao
import com.tencent.bkrepo.opdata.cluster.topology.model.TClusterNodeExtension
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeRecord
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

/**
 * TopologyService 单元测试。
 *
 * 覆盖：
 * - REMOTE 节点排除（不出现在拓扑节点列表中）
 * - 同一对集群多种 replicaType 任务聚合为一条通道
 * - 通道全部 disabled 时正确标记 allDisabled
 * - 元数据缺失时使用 name 作为 displayName 兜底
 * - REMOTE 任务被纳入聚合气泡而不是普通通道
 */
internal class TopologyServiceTest {

    private lateinit var replClusterDao: ReplClusterDao
    private lateinit var replTaskDao: ReplTaskDao
    private lateinit var extensionDao: ClusterNodeExtensionDao
    private lateinit var service: TopologyService

    @BeforeEach
    fun setUp() {
        replClusterDao = mock()
        replTaskDao = mock()
        extensionDao = mock()
        service = TopologyService(replClusterDao, replTaskDao, extensionDao)
    }

    @Test
    fun `buildTopology should exclude REMOTE nodes from rendered nodes`() {
        // given：1 个 CENTER + 1 个 EDGE + 1 个 REMOTE
        val nodes = listOf(
            buildCluster("center-sz", ClusterNodeType.CENTER),
            buildCluster("edge-hk", ClusterNodeType.EDGE),
            buildCluster("remote-001", ClusterNodeType.REMOTE)
        )
        whenever(replClusterDao.listAll()).thenReturn(nodes)
        whenever(extensionDao.findByClusterNames(listOf("center-sz", "edge-hk"))).thenReturn(emptyList())
        whenever(replTaskDao.listAll()).thenReturn(emptyList())

        // when
        val topology = service.buildTopology()

        // then：REMOTE 不出现，displayName 兜底为 name
        assertEquals(2, topology.nodes.size)
        assertTrue(topology.nodes.none { it.type == ClusterNodeType.REMOTE })
        val center = topology.nodes.first { it.name == "center-sz" }
        assertEquals("center-sz", center.displayName)
        assertNull(center.region)
    }

    @Test
    fun `buildTopology should aggregate multiple replica types into single channel`() {
        // given：center -> edge 两个不同 replicaType 的任务
        val nodes = listOf(
            buildCluster("center-sz", ClusterNodeType.CENTER),
            buildCluster("edge-hk", ClusterNodeType.EDGE)
        )
        val tasks = listOf(
            buildTask("k1", "REAL_TIME", listOf("edge-hk"), enabled = true),
            buildTask("k2", "SCHEDULED", listOf("edge-hk"), enabled = true)
        )
        whenever(replClusterDao.listAll()).thenReturn(nodes)
        whenever(extensionDao.findByClusterNames(listOf("center-sz", "edge-hk"))).thenReturn(emptyList())
        whenever(replTaskDao.listAll()).thenReturn(tasks)

        // when
        val topology = service.buildTopology()

        // then：聚合为 1 条通道，类型集合包含 REAL_TIME 与 SCHEDULED
        assertEquals(1, topology.channels.size)
        val channel = topology.channels.single()
        assertEquals("center-sz", channel.sourceCluster)
        assertEquals("edge-hk", channel.targetCluster)
        assertEquals(setOf("REAL_TIME", "SCHEDULED"), channel.replicaTypes)
        assertEquals(2, channel.totalTaskCount)
        assertEquals(2, channel.activeTaskCount)
        assertFalse(channel.allDisabled)
    }

    @Test
    fun `buildTopology should mark channel allDisabled when all tasks disabled`() {
        // given：唯一一条任务被禁用
        val nodes = listOf(
            buildCluster("center-sz", ClusterNodeType.CENTER),
            buildCluster("edge-hk", ClusterNodeType.EDGE)
        )
        val tasks = listOf(buildTask("k1", "REAL_TIME", listOf("edge-hk"), enabled = false))
        whenever(replClusterDao.listAll()).thenReturn(nodes)
        whenever(extensionDao.findByClusterNames(listOf("center-sz", "edge-hk"))).thenReturn(emptyList())
        whenever(replTaskDao.listAll()).thenReturn(tasks)

        // when
        val topology = service.buildTopology()

        // then
        val channel = topology.channels.single()
        assertEquals(0, channel.activeTaskCount)
        assertEquals(1, channel.totalTaskCount)
        assertTrue(channel.allDisabled)
    }

    @Test
    fun `buildTopology should merge extension metadata into nodes`() {
        // given：center 节点有元数据，edge 没有
        val nodes = listOf(
            buildCluster("center-sz", ClusterNodeType.CENTER),
            buildCluster("edge-hk", ClusterNodeType.EDGE)
        )
        val ext = TClusterNodeExtension(
            clusterName = "center-sz",
            region = "sz",
            networkZone = "IDC内网",
            displayName = "深圳中心",
            description = "主中心节点",
            lastModifiedBy = "admin",
            lastModifiedDate = LocalDateTime.now()
        )
        whenever(replClusterDao.listAll()).thenReturn(nodes)
        whenever(extensionDao.findByClusterNames(listOf("center-sz", "edge-hk"))).thenReturn(listOf(ext))
        whenever(replTaskDao.listAll()).thenReturn(emptyList())

        // when
        val topology = service.buildTopology()

        // then
        val center = topology.nodes.first { it.name == "center-sz" }
        val edge = topology.nodes.first { it.name == "edge-hk" }
        assertEquals("深圳中心", center.displayName)
        assertEquals("sz", center.region)
        assertEquals("IDC内网", center.networkZone)
        // edge 未配置元数据，displayName 应当兜底为 name
        assertEquals("edge-hk", edge.displayName)
        assertNull(edge.region)
    }

    @Test
    fun `buildTopology should put tasks targeting REMOTE into remoteSummary not channels`() {
        // given：1 个 center + 1 个 remote，任务指向 remote
        val nodes = listOf(
            buildCluster("center-sz", ClusterNodeType.CENTER),
            buildCluster("remote-001", ClusterNodeType.REMOTE),
            buildCluster("remote-002", ClusterNodeType.REMOTE)
        )
        val tasks = listOf(
            buildTask("k1", "SCHEDULED", listOf("remote-001"), enabled = true),
            buildTask("k2", "SCHEDULED", listOf("remote-002"), enabled = false)
        )
        whenever(replClusterDao.listAll()).thenReturn(nodes)
        whenever(extensionDao.findByClusterNames(listOf("center-sz"))).thenReturn(emptyList())
        whenever(replTaskDao.listAll()).thenReturn(tasks)

        // when
        val topology = service.buildTopology()

        // then：通道列表应为空，remoteSummary 反映任务统计
        assertTrue(topology.channels.isEmpty())
        assertEquals(2, topology.remoteSummary.remoteNodeCount)
        assertEquals(1, topology.remoteSummary.activeRemoteTaskCount)
        assertEquals(1, topology.remoteSummary.completedRemoteTaskCount)
        // 节点列表不包含 REMOTE
        assertEquals(1, topology.nodes.size)
        assertNotNull(topology.nodes.first { it.name == "center-sz" })
    }

    private fun buildCluster(name: String, type: ClusterNodeType): ClusterNodeRecord {
        return ClusterNodeRecord(
            name = name,
            url = "http://$name.bkrepo.local",
            type = type,
            status = "HEALTHY"
        )
    }

    private fun buildTask(
        key: String,
        replicaType: String,
        targets: List<String>,
        enabled: Boolean
    ): ReplicaTaskRecord {
        return ReplicaTaskRecord(
            key = key,
            name = key,
            replicaType = replicaType,
            remoteClusters = targets.map { ReplicaTaskRecord.RemoteClusterRef(id = it, name = it) },
            enabled = enabled
        )
    }
}
