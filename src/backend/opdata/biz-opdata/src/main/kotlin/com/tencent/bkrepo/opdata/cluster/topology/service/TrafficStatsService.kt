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
import com.google.common.cache.CacheLoader
import com.tencent.bkrepo.opdata.cluster.dao.ReplRecordDetailDao
import com.tencent.bkrepo.opdata.cluster.topology.pojo.ChannelTrafficTrendPoint
import com.tencent.bkrepo.opdata.cluster.topology.pojo.ChannelTrafficTrendVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.ChannelTrafficVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.NodeTrafficSummaryVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.TrafficGranularity
import com.tencent.bkrepo.opdata.cluster.topology.pojo.TrafficPeriod
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * 通道流量统计服务。
 *
 * 基于 replica_record_detail 集合实时聚合，按 (localCluster, remoteCluster) 划分通道方向，
 * 仅统计 status=SUCCESS 的成功流量。所有聚合查询设置 maxTimeMS 超时，并对相同入参的结果
 * 进行 60 秒本地缓存，避免对核心 replication 表造成重复压力。
 *
 * 任意一次聚合查询失败/超时时，返回空数据并记录 WARN 日志，由调用方据此进行降级展示。
 */
@Service
class TrafficStatsService(
    private val recordDetailDao: ReplRecordDetailDao
) {

    /**
     * 通道时段汇总缓存，key = (period, since)。since 取分钟级整点以保证短期可命中。
     */
    private val channelSummaryCache = CacheBuilder.newBuilder()
        .expireAfterWrite(SUMMARY_CACHE_TTL_SECONDS, TimeUnit.SECONDS)
        .maximumSize(64)
        .build(object : CacheLoader<TrafficPeriod, List<ChannelTrafficVO>>() {
            override fun load(key: TrafficPeriod): List<ChannelTrafficVO> {
                return doAggregateChannelTraffic(key)
            }
        })

    /**
     * 节点流量汇总缓存，key = clusterName。
     */
    private val nodeSummaryCache = CacheBuilder.newBuilder()
        .expireAfterWrite(SUMMARY_CACHE_TTL_SECONDS, TimeUnit.SECONDS)
        .maximumSize(256)
        .build(object : CacheLoader<NodeSummaryKey, NodeTrafficSummaryVO>() {
            override fun load(key: NodeSummaryKey): NodeTrafficSummaryVO {
                return doNodeTrafficSummary(key.clusterName, key.period)
            }
        })

    /**
     * 聚合所有通道在指定时段内的成功流量字节数。
     *
     * @return 失败/超时时返回空列表（表示流量数据不可用）
     */
    fun aggregateChannelTraffic(period: TrafficPeriod): List<ChannelTrafficVO> {
        return try {
            channelSummaryCache.get(period)
        } catch (e: Exception) {
            logger.warn("aggregate channel traffic failed for period={}, fallback to empty", period, e)
            emptyList()
        }
    }

    /**
     * 查询单条通道的时段流量趋势。
     *
     * 自适应粒度：跨度 ≤ 24h 按小时分桶；≤ 7 天按天分桶；更长跨度按天分桶并降采样。
     * 最大跨度 90 天，超出抛出 IllegalArgumentException。
     */
    fun getChannelTrend(
        source: String,
        target: String,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        granularity: TrafficGranularity? = null
    ): ChannelTrafficTrendVO {
        require(!startTime.isAfter(endTime)) { "startTime must not be after endTime" }
        val span = Duration.between(startTime, endTime)
        require(span <= MAX_QUERY_SPAN) { "query span exceeds 90 days limit" }

        val effectiveGranularity = granularity ?: chooseGranularity(span)
        return try {
            doChannelTrend(source, target, startTime, endTime, effectiveGranularity)
        } catch (e: Exception) {
            logger.warn(
                "channel trend aggregation failed: source={}, target={}, range={}~{}, granularity={}",
                source, target, startTime, endTime, effectiveGranularity, e
            )
            ChannelTrafficTrendVO(
                sourceCluster = source,
                targetCluster = target,
                granularity = effectiveGranularity,
                points = emptyList(),
                totalBytes = 0,
                totalSuccessCount = 0
            )
        }
    }

    /**
     * 查询节点的入/出站总流量。
     */
    fun getNodeTrafficSummary(
        clusterName: String,
        period: TrafficPeriod = TrafficPeriod.LAST_24H
    ): NodeTrafficSummaryVO {
        return try {
            nodeSummaryCache.get(NodeSummaryKey(clusterName, period))
        } catch (e: Exception) {
            logger.warn("node traffic summary failed: cluster={}, period={}", clusterName, period, e)
            NodeTrafficSummaryVO(clusterName, 0, 0)
        }
    }

    private fun doAggregateChannelTraffic(period: TrafficPeriod): List<ChannelTrafficVO> {
        val start = startTimeOf(period)
        val matchStage = Document(
            "\$match", Document()
                .append(FIELD_STATUS, STATUS_SUCCESS)
                .append(FIELD_START_TIME, Document("\$gte", toDate(start)))
        )
        val groupStage = Document(
            "\$group", Document()
                .append(
                    "_id",
                    Document()
                        .append("source", "\$$FIELD_LOCAL")
                        .append("target", "\$$FIELD_REMOTE")
                )
                .append("totalBytes", Document("\$sum", Document("\$ifNull", listOf("\$$FIELD_SIZE", 0L))))
                .append("successCount", Document("\$sum", 1))
        )
        val pipeline = listOf(matchStage, groupStage)
        val results = runAggregate(pipeline)
        return results.mapNotNull { doc ->
            val id = doc.get("_id", Document::class.java) ?: return@mapNotNull null
            val source = id.getString("source") ?: return@mapNotNull null
            val target = id.getString("target") ?: return@mapNotNull null
            ChannelTrafficVO(
                sourceCluster = source,
                targetCluster = target,
                totalBytes = (doc.get("totalBytes") as? Number)?.toLong() ?: 0L,
                successCount = (doc.get("successCount") as? Number)?.toLong() ?: 0L
            )
        }
    }

    private fun doChannelTrend(
        source: String,
        target: String,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        granularity: TrafficGranularity
    ): ChannelTrafficTrendVO {
        val matchStage = Document(
            "\$match", Document()
                .append(FIELD_STATUS, STATUS_SUCCESS)
                .append(FIELD_LOCAL, source)
                .append(FIELD_REMOTE, target)
                .append(
                    FIELD_START_TIME,
                    Document()
                        .append("\$gte", toDate(startTime))
                        .append("\$lte", toDate(endTime))
                )
        )
        val unit = if (granularity == TrafficGranularity.HOUR) "hour" else "day"
        val groupStage = Document(
            "\$group", Document()
                .append(
                    "_id",
                    Document(
                        "\$dateTrunc",
                        Document()
                            .append("date", "\$$FIELD_START_TIME")
                            .append("unit", unit)
                    )
                )
                .append("bytes", Document("\$sum", Document("\$ifNull", listOf("\$$FIELD_SIZE", 0L))))
                .append("successCount", Document("\$sum", 1))
        )
        val sortStage = Document("\$sort", Document("_id", 1))
        val pipeline = listOf(matchStage, groupStage, sortStage)

        val results = runAggregate(pipeline)
        val points = results.mapNotNull { doc ->
            val bucket = doc.get("_id") as? Date ?: return@mapNotNull null
            ChannelTrafficTrendPoint(
                time = bucket.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                bytes = (doc.get("bytes") as? Number)?.toLong() ?: 0L,
                successCount = (doc.get("successCount") as? Number)?.toLong() ?: 0L
            )
        }
        return ChannelTrafficTrendVO(
            sourceCluster = source,
            targetCluster = target,
            granularity = granularity,
            points = points,
            totalBytes = points.sumOf { it.bytes },
            totalSuccessCount = points.sumOf { it.successCount }
        )
    }

    private fun doNodeTrafficSummary(clusterName: String, period: TrafficPeriod): NodeTrafficSummaryVO {
        val start = startTimeOf(period)
        val outbound = sumByDirection(FIELD_LOCAL, clusterName, start)
        val inbound = sumByDirection(FIELD_REMOTE, clusterName, start)
        return NodeTrafficSummaryVO(
            clusterName = clusterName,
            outboundBytes = outbound,
            inboundBytes = inbound
        )
    }

    private fun sumByDirection(directionField: String, clusterName: String, since: LocalDateTime): Long {
        val pipeline = listOf(
            Document(
                "\$match", Document()
                    .append(FIELD_STATUS, STATUS_SUCCESS)
                    .append(directionField, clusterName)
                    .append(FIELD_START_TIME, Document("\$gte", toDate(since)))
            ),
            Document(
                "\$group", Document()
                    .append("_id", null)
                    .append("totalBytes", Document("\$sum", Document("\$ifNull", listOf("\$$FIELD_SIZE", 0L))))
            )
        )
        val results = runAggregate(pipeline)
        return results.firstOrNull()?.let { (it.get("totalBytes") as? Number)?.toLong() } ?: 0L
    }

    /**
     * 通过 DAO 封装的原生 driver 执行 aggregate（已设置 maxTimeMS 与 allowDiskUse）。
     *
     * Spring Data Mongo 的 [Criteria] 不便表达 \$dateTrunc 等高级阶段，
     * 故直接构造 [Document] pipeline。
     */
    private fun runAggregate(pipeline: List<Document>): List<Document> {
        return recordDetailDao.aggregate(pipeline, AGGREGATE_MAX_TIME_MS)
    }

    private fun startTimeOf(period: TrafficPeriod): LocalDateTime {
        return LocalDateTime.now().minusHours(period.displayHours)
    }

    private fun chooseGranularity(span: Duration): TrafficGranularity {
        return if (span <= Duration.ofHours(24)) TrafficGranularity.HOUR else TrafficGranularity.DAY
    }

    private fun toDate(time: LocalDateTime): Date {
        return Date.from(time.atZone(ZoneId.systemDefault()).toInstant())
    }

    private data class NodeSummaryKey(val clusterName: String, val period: TrafficPeriod)

    companion object {
        private val logger = LoggerFactory.getLogger(TrafficStatsService::class.java)

        private const val FIELD_LOCAL = "localCluster"
        private const val FIELD_REMOTE = "remoteCluster"
        private const val FIELD_STATUS = "status"
        private const val FIELD_SIZE = "size"
        private const val FIELD_START_TIME = "startTime"
        private const val STATUS_SUCCESS = "SUCCESS"

        private const val AGGREGATE_MAX_TIME_MS: Long = 5_000
        private const val SUMMARY_CACHE_TTL_SECONDS: Long = 60
        private val MAX_QUERY_SPAN: Duration = Duration.ofDays(90)
    }
}
