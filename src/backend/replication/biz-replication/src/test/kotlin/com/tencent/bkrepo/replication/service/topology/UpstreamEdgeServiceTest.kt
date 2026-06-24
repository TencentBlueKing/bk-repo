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
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.replication.dao.ClusterNodeDao
import com.tencent.bkrepo.replication.dao.UpstreamEdgeDao
import com.tencent.bkrepo.replication.model.TClusterNode
import com.tencent.bkrepo.replication.model.TUpstreamEdge
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeStatus
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeEntry
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeSourceType
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeStatus
import com.tencent.bkrepo.replication.service.topology.UpstreamEdgeService.LocalUpstreamEdge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

/**
 * UpstreamEdgeService 关键行为单元测试。
 *
 * 通过 mock [UpstreamEdgeDao]，把"先删后插"流程拆成两步独立断言；
 * 不依赖 MongoDB 容器，专注校验 service 自己的业务规则。
 *
 *  - 远程快照只覆盖 REPLICA_PUSH，不动 EDGE_PULL / FEDERATION（FEDERATION 由本地直写维护）；
 *  - 空快照触发删除（最终一致）；
 *  - 未注册集群拒绝写入并写审计日志；
 *  - 快照中混入 EDGE_PULL 或 FEDERATION 条目被拒绝；
 *  - 本地直写 EDGE_PULL / FEDERATION 的覆盖范围。
 */
@DisplayName("UpstreamEdgeService 单元测试")
class UpstreamEdgeServiceTest {

    private val upstreamEdgeDao: UpstreamEdgeDao = mock()
    private val clusterNodeDao: ClusterNodeDao = mock()
    private lateinit var service: UpstreamEdgeService

    @BeforeEach
    fun setUp() {
        service = UpstreamEdgeService(upstreamEdgeDao, clusterNodeDao)
        // 默认所有上游都视为已注册
        whenever(clusterNodeDao.findByName(any())).doAnswer { stubCluster(it.getArgument(0)) }
    }

    @Test
    fun `applyRemoteSnapshot must use REPLICA_PUSH-only scope and not touch EDGE_PULL or FEDERATION`() {
        val written = service.applyRemoteSnapshot(
            "clusterA",
            listOf(UpstreamEdgeEntry("task-y", "task-y", UpstreamEdgeSourceType.REPLICA_PUSH))
        )
        assertEquals(1, written)
        // 删除范围必须是 {REPLICA_PUSH}，不能包含 EDGE_PULL / FEDERATION
        val scopeCaptor = argumentCaptor<Collection<UpstreamEdgeSourceType>>()
        verify(upstreamEdgeDao).deleteByUpstreamAndSourceTypes(eq("clusterA"), scopeCaptor.capture())
        val scope = scopeCaptor.firstValue.toSet()
        assertEquals(setOf(UpstreamEdgeSourceType.REPLICA_PUSH), scope)
        assertTrue(UpstreamEdgeSourceType.EDGE_PULL !in scope)
        assertTrue(UpstreamEdgeSourceType.FEDERATION !in scope)

        verify(upstreamEdgeDao).batchUpsert(any())
    }

    @Test
    fun `empty remote snapshot still triggers delete to ensure eventual consistency`() {
        val written = service.applyRemoteSnapshot("clusterA", emptyList())
        assertEquals(0, written)
        verify(upstreamEdgeDao).deleteByUpstreamAndSourceTypes(eq("clusterA"), any())
        // 空集合不调 batchUpsert
        verify(upstreamEdgeDao, never()).batchUpsert(any())
    }

    @Test
    fun `unregistered upstream cluster should be rejected and not write anything`() {
        whenever(clusterNodeDao.findByName(eq("ghost"))).doReturn(null)
        assertThrows(
            ErrorCodeException::class.java
        ) {
            service.applyRemoteSnapshot(
                "ghost",
                listOf(UpstreamEdgeEntry("task-x", "task-x", UpstreamEdgeSourceType.REPLICA_PUSH))
            )
        }
        verify(upstreamEdgeDao, never()).deleteByUpstreamAndSourceTypes(any(), any())
        verify(upstreamEdgeDao, never()).batchUpsert(any())
    }

    @Test
    fun `snapshot must not contain EDGE_PULL entry`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.applyRemoteSnapshot(
                "clusterA",
                listOf(UpstreamEdgeEntry("task-x", "task-x", UpstreamEdgeSourceType.EDGE_PULL))
            )
        }
        verify(upstreamEdgeDao, never()).batchUpsert(any())
    }

    @Test
    fun `snapshot must not contain FEDERATION entry`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.applyRemoteSnapshot(
                "clusterA",
                listOf(UpstreamEdgeEntry("fed-x", "fed-x", UpstreamEdgeSourceType.FEDERATION))
            )
        }
        verify(upstreamEdgeDao, never()).batchUpsert(any())
    }

    @Test
    fun `applyLocalEdgePullSnapshot deletes ALL edge_pull then upserts new set`() {
        service.applyLocalEdgePullSnapshot(
            listOf(
                LocalUpstreamEdge("clusterA", "task1", "task1"),
                LocalUpstreamEdge("clusterB", "task2", "task2")
            )
        )
        verify(upstreamEdgeDao).deleteAllByEdgePull()
        val captor = argumentCaptor<Collection<TUpstreamEdge>>()
        verify(upstreamEdgeDao).batchUpsert(captor.capture())
        val records = captor.firstValue.toList()
        assertEquals(2, records.size)
        assertTrue(records.all { it.sourceType == UpstreamEdgeSourceType.EDGE_PULL })
    }

    @Test
    fun `applyLocalFederationMirror deletes within member-set scope only`() {
        service.applyLocalFederationMirror(
            setOf("clusterA"),
            listOf(LocalUpstreamEdge("clusterA", "fedA-new", "n"))
        )
        val scopeCaptor = argumentCaptor<Collection<String>>()
        verify(upstreamEdgeDao).deleteFederationByUpstreams(scopeCaptor.capture())
        val scope = scopeCaptor.firstValue.toSet()
        assertTrue("clusterA" in scope)
    }

    private fun stubCluster(name: String): TClusterNode {
        val now = LocalDateTime.now()
        return TClusterNode(
            name = name,
            status = ClusterNodeStatus.HEALTHY,
            type = ClusterNodeType.STANDALONE,
            url = "http://$name",
            username = null,
            password = null,
            certificate = null,
            createdBy = "ut",
            createdDate = now,
            lastModifiedBy = "ut",
            lastModifiedDate = now
        )
    }
}
