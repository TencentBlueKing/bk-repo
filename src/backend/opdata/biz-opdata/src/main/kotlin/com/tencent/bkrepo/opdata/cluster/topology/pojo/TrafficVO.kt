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

package com.tencent.bkrepo.opdata.cluster.topology.pojo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 预设的流量统计时段。
 */
enum class TrafficPeriod(val displayHours: Long) {
    LAST_1H(1),
    LAST_24H(24),
    LAST_7D(24 * 7),
    LAST_30D(24 * 30);

    companion object {
        /**
         * 根据外部入参 1h / 24h / 7d / 30d 解析为枚举，未识别值默认 LAST_24H。
         */
        fun parse(raw: String?): TrafficPeriod {
            return when (raw?.trim()?.lowercase()) {
                "1h" -> LAST_1H
                "24h", "today" -> LAST_24H
                "7d" -> LAST_7D
                "30d" -> LAST_30D
                else -> LAST_24H
            }
        }
    }
}

/**
 * 时间分桶粒度。
 */
enum class TrafficGranularity {
    HOUR, DAY;

    companion object {
        fun parse(raw: String?): TrafficGranularity? {
            return when (raw?.trim()?.lowercase()) {
                "hour", "h" -> HOUR
                "day", "d" -> DAY
                null, "" -> null
                else -> null
            }
        }
    }
}

@Schema(title = "通道流量摘要")
data class ChannelTrafficVO(
    @get:Schema(title = "源集群名称") val sourceCluster: String,
    @get:Schema(title = "目标集群名称") val targetCluster: String,
    @get:Schema(title = "成功传输的总流量字节数") val totalBytes: Long,
    @get:Schema(title = "成功制品数") val successCount: Long
)

@Schema(title = "通道流量趋势点")
data class ChannelTrafficTrendPoint(
    @get:Schema(title = "时间桶起点") val time: LocalDateTime,
    @get:Schema(title = "该桶内的流量字节数") val bytes: Long,
    @get:Schema(title = "该桶内成功制品数") val successCount: Long
)

@Schema(title = "通道流量趋势")
data class ChannelTrafficTrendVO(
    @get:Schema(title = "源集群名称") val sourceCluster: String,
    @get:Schema(title = "目标集群名称") val targetCluster: String,
    @get:Schema(title = "时间粒度") val granularity: TrafficGranularity,
    @get:Schema(title = "趋势数据点列表") val points: List<ChannelTrafficTrendPoint>,
    @get:Schema(title = "总流量") val totalBytes: Long,
    @get:Schema(title = "总成功制品数") val totalSuccessCount: Long
)

@Schema(title = "节点流量出入汇总")
data class NodeTrafficSummaryVO(
    @get:Schema(title = "集群名称") val clusterName: String,
    @get:Schema(title = "出站总流量字节") val outboundBytes: Long,
    @get:Schema(title = "入站总流量字节") val inboundBytes: Long
)
