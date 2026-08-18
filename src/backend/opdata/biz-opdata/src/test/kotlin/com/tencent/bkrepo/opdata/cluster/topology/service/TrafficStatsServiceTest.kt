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

import com.tencent.bkrepo.opdata.cluster.dao.ReplRecordDetailDao
import com.tencent.bkrepo.opdata.cluster.topology.pojo.TrafficGranularity
import com.tencent.bkrepo.opdata.cluster.topology.pojo.TrafficPeriod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

/**
 * TrafficStatsService 单元测试。
 *
 * 覆盖：
 * - TrafficPeriod / TrafficGranularity 字符串解析
 * - 查询跨度超过 90 天时拒绝（IllegalArgumentException）
 * - startTime > endTime 时拒绝
 * - 聚合查询底层异常时降级为空数据，不抛异常
 */
internal class TrafficStatsServiceTest {

    private lateinit var recordDetailDao: ReplRecordDetailDao
    private lateinit var service: TrafficStatsService

    @BeforeEach
    fun setUp() {
        // 让 DAO 的 aggregate() 直接抛异常以模拟“底层 mongo 不可用”，用以验证降级行为
        recordDetailDao = mock()
        whenever(recordDetailDao.aggregate(any(), any(), any()))
            .thenThrow(RuntimeException("simulated mongo unavailable"))
        service = TrafficStatsService(recordDetailDao)
    }

    @Test
    fun `TrafficPeriod parse should fall back to LAST_24H for unknown input`() {
        assertEquals(TrafficPeriod.LAST_1H, TrafficPeriod.parse("1h"))
        assertEquals(TrafficPeriod.LAST_24H, TrafficPeriod.parse("24h"))
        assertEquals(TrafficPeriod.LAST_24H, TrafficPeriod.parse("today"))
        assertEquals(TrafficPeriod.LAST_7D, TrafficPeriod.parse("7d"))
        assertEquals(TrafficPeriod.LAST_30D, TrafficPeriod.parse("30d"))
        // 未知值
        assertEquals(TrafficPeriod.LAST_24H, TrafficPeriod.parse("foo"))
        assertEquals(TrafficPeriod.LAST_24H, TrafficPeriod.parse(null))
    }

    @Test
    fun `TrafficGranularity parse should accept hour and day case-insensitively`() {
        assertEquals(TrafficGranularity.HOUR, TrafficGranularity.parse("hour"))
        assertEquals(TrafficGranularity.HOUR, TrafficGranularity.parse("H"))
        assertEquals(TrafficGranularity.DAY, TrafficGranularity.parse("DAY"))
        assertEquals(null, TrafficGranularity.parse(null))
        assertEquals(null, TrafficGranularity.parse("invalid"))
    }

    @Test
    fun `getChannelTrend should reject query span exceeding 90 days`() {
        val end = LocalDateTime.now()
        val start = end.minusDays(91)
        assertThrows(IllegalArgumentException::class.java) {
            service.getChannelTrend("center-sz", "edge-hk", start, end)
        }
    }

    @Test
    fun `getChannelTrend should reject startTime after endTime`() {
        val now = LocalDateTime.now()
        val later = now.plusHours(1)
        assertThrows(IllegalArgumentException::class.java) {
            service.getChannelTrend("center-sz", "edge-hk", later, now)
        }
    }

    @Test
    fun `aggregateChannelTraffic should return empty list when underlying aggregation fails`() {
        val result = service.aggregateChannelTraffic(TrafficPeriod.LAST_24H)
        assertTrue(result.isEmpty(), "fallback to empty list when aggregation throws")
    }

    @Test
    fun `getNodeTrafficSummary should return zero bytes when underlying aggregation fails`() {
        val summary = service.getNodeTrafficSummary("center-sz", TrafficPeriod.LAST_24H)
        assertEquals(0L, summary.outboundBytes)
        assertEquals(0L, summary.inboundBytes)
        assertEquals("center-sz", summary.clusterName)
    }

    @Test
    fun `getChannelTrend should fall back to empty trend when aggregation fails`() {
        val end = LocalDateTime.now()
        val start = end.minusHours(1)
        val trend = service.getChannelTrend("center-sz", "edge-hk", start, end)
        assertTrue(trend.points.isEmpty())
        assertEquals(0L, trend.totalBytes)
        assertEquals(0L, trend.totalSuccessCount)
        // 默认粒度（≤24h => HOUR）
        assertEquals(TrafficGranularity.HOUR, trend.granularity)
    }
}
