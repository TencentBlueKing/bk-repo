package com.tencent.bkrepo.replication.metrics

import com.tencent.bkrepo.replication.constant.RESULT_FAILED
import com.tencent.bkrepo.replication.constant.RESULT_SUCCESS
import com.tencent.bkrepo.replication.constant.TAG_EVENT_TYPE
import com.tencent.bkrepo.replication.constant.TAG_PROJECT_ID
import com.tencent.bkrepo.replication.constant.TAG_REPO_NAME
import com.tencent.bkrepo.replication.constant.TAG_SYNC_RESULT
import com.tencent.bkrepo.replication.constant.TAG_TASK_KEY
import com.tencent.bkrepo.replication.constant.UNIT_BYTES
import com.tencent.bkrepo.replication.constant.UNIT_BYTES_PER_SECOND
import com.tencent.bkrepo.replication.constant.UNIT_EVENTS_PER_SECOND
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_ARTIFACT_SYNC_DURATION as METRIC_ARTIFACT_SYNC_DURATION
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_ARTIFACT_SYNC_DURATION_DESC as DESC_ARTIFACT_SYNC_DURATION
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_ARTIFACT_SYNC_FAILED as METRIC_ARTIFACT_SYNC_FAILED
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_ARTIFACT_SYNC_FAILED_DESC as DESC_ARTIFACT_SYNC_FAILED
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_ARTIFACT_SYNC_RATE as METRIC_ARTIFACT_SYNC_RATE
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_ARTIFACT_SYNC_RATE_DESC as DESC_ARTIFACT_SYNC_RATE
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_ARTIFACT_SYNC_SUCCESS as METRIC_ARTIFACT_SYNC_SUCCESS
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_ARTIFACT_SYNC_SUCCESS_DESC as DESC_ARTIFACT_SYNC_SUCCESS
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_EVENT_FAILED as METRIC_EVENT_FAILED
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_EVENT_FAILED_DESC as DESC_EVENT_FAILED
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_EVENT_RETRY_COUNT as METRIC_EVENT_RETRY
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_EVENT_RETRY_COUNT_DESC as DESC_EVENT_RETRY
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_EVENT_SUCCESS as METRIC_EVENT_SUCCESS
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_EVENT_SUCCESS_DESC as DESC_EVENT_SUCCESS
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_EVENT_TOTAL as METRIC_EVENT_TOTAL
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_EVENT_TOTAL_DESC as DESC_EVENT_TOTAL
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FILE_TRANSFER_BYTES as METRIC_FILE_TRANSFER_BYTES
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FILE_TRANSFER_BYTES_DESC as DESC_FILE_TRANSFER_BYTES
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FILE_TRANSFER_DURATION as METRIC_FILE_TRANSFER_DURATION
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FILE_TRANSFER_DURATION_DESC as DESC_FILE_TRANSFER_DURATION
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FILE_TRANSFER_FAILED as METRIC_FILE_TRANSFER_FAILED
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FILE_TRANSFER_FAILED_DESC as DESC_FILE_TRANSFER_FAILED
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FILE_TRANSFER_RATE as METRIC_FILE_TRANSFER_RATE
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FILE_TRANSFER_RATE_DESC as DESC_FILE_TRANSFER_RATE
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FILE_TRANSFER_SUCCESS as METRIC_FILE_TRANSFER_SUCCESS
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FILE_TRANSFER_SUCCESS_DESC as DESC_FILE_TRANSFER_SUCCESS

/**
 * 联邦仓库指标收集器
 *
 * 提供动态指标收集功能，包括：
 * - Event Metrics：事件处理指标（总数、成功、失败、重试）
 * - File Transfer Metrics：文件传输指标（字节数、耗时、速率）
 * - Metadata Sync Metrics：元数据同步指标（耗时、速率）
 */
@Component
class FederationMetricsCollector(
    private val registry: MeterRegistry
) {
    /**
     * Meter 缓存，避免重复注册导致内存泄漏
     * Key: Meter 的唯一标识（名称 + 标签）
     */
    private val meterCache = ConcurrentHashMap<String, Meter>()

    // ==================== Event Metrics（事件指标） ====================
    /**
     * 记录事件处理
     *
     * @param eventType 事件类型（CREATE、UPDATE、DELETE等）
     * @param success 是否成功
     */
    fun recordEvent(eventType: String, success: Boolean) {
        val baseTags = mapOf(TAG_EVENT_TYPE to eventType)

        // 记录事件总数
        getOrCreateCounter(METRIC_EVENT_TOTAL, DESC_EVENT_TOTAL, baseTags).increment()

        // 记录成功或失败
        val resultTags = baseTags + (TAG_SYNC_RESULT to if (success) RESULT_SUCCESS else RESULT_FAILED)
        if (success) {
            getOrCreateCounter(METRIC_EVENT_SUCCESS, DESC_EVENT_SUCCESS, resultTags).increment()
        } else {
            getOrCreateCounter(METRIC_EVENT_FAILED, DESC_EVENT_FAILED, resultTags).increment()
        }
    }

    fun recordRetryEvent(eventType: String) {
        val baseTags = mapOf(TAG_EVENT_TYPE to eventType)
        getOrCreateCounter(METRIC_EVENT_RETRY, DESC_EVENT_RETRY, baseTags).increment()

    }


    // ==================== File Transfer Metrics（文件传输指标） ====================

    /**
     * 记录文件传输
     *
     * @param projectId 项目ID
     * @param repoName 仓库名称
     * @param taskKey taskKey
     * @param success 是否成功
     * @param bytes 传输字节数
     * @param durationMillis 耗时（毫秒）
     */
    fun recordFileTransfer(
        projectId: String,
        repoName: String,
        taskKey: String,
        success: Boolean,
        bytes: Long,
        durationMillis: Long
    ) {
        val tags = mapOf(TAG_PROJECT_ID to projectId, TAG_REPO_NAME to repoName, TAG_TASK_KEY to taskKey)

        // 记录成功或失败
        val resultTags = tags + (TAG_SYNC_RESULT to if (success) RESULT_SUCCESS else RESULT_FAILED)
        if (success) {
            // 记录传输字节数
            getOrCreateDistributionSummary(METRIC_FILE_TRANSFER_BYTES, DESC_FILE_TRANSFER_BYTES, UNIT_BYTES, tags)
                .record(bytes.toDouble())

            // 记录传输耗时
            getOrCreateTimer(METRIC_FILE_TRANSFER_DURATION, DESC_FILE_TRANSFER_DURATION, tags)
                .record(durationMillis, TimeUnit.MILLISECONDS)

            // 记录传输速率（bytes/s）
            if (durationMillis > 0) {
                val rate = (bytes * 1000.0) / durationMillis
                getOrCreateDistributionSummary(
                    METRIC_FILE_TRANSFER_RATE,
                    DESC_FILE_TRANSFER_RATE,
                    UNIT_BYTES_PER_SECOND,
                    tags
                ).record(rate)
            }
            getOrCreateCounter(METRIC_FILE_TRANSFER_SUCCESS, DESC_FILE_TRANSFER_SUCCESS, resultTags).increment()
        } else {
            getOrCreateCounter(METRIC_FILE_TRANSFER_FAILED, DESC_FILE_TRANSFER_FAILED, resultTags).increment()
        }
    }

    // ==================== Metadata Sync Metrics（元数据同步指标） ====================

    /**
     * 记录制品同步
     *
     * @param projectId 项目ID
     * @param repoName 仓库名称
     * @param taskKey taskKey
     * @param success 是否成功
     * @param durationMillis 耗时（毫秒）
     */
    fun recordArtifactSync(
        projectId: String,
        repoName: String,
        taskKey: String,
        success: Boolean,
        durationMillis: Long
    ) {
        val tags = mapOf(TAG_PROJECT_ID to projectId, TAG_REPO_NAME to repoName)

        // 记录成功或失败
        val resultTags = tags + (TAG_SYNC_RESULT to if (success) RESULT_SUCCESS else RESULT_FAILED)
        if (success) {
            // 记录制品同步计时
            getOrCreateTimer(METRIC_ARTIFACT_SYNC_DURATION, DESC_ARTIFACT_SYNC_DURATION, tags)
                .record(durationMillis, TimeUnit.MILLISECONDS)

            // 记录制品同步速率（events/s）
            if (durationMillis > 0) {
                val rate = 1000.0 / durationMillis
                getOrCreateDistributionSummary(
                    METRIC_ARTIFACT_SYNC_RATE,
                    DESC_ARTIFACT_SYNC_RATE,
                    UNIT_EVENTS_PER_SECOND,
                    tags
                ).record(rate)
            }
            getOrCreateCounter(METRIC_ARTIFACT_SYNC_SUCCESS, DESC_ARTIFACT_SYNC_SUCCESS, resultTags).increment()
        } else {
            getOrCreateCounter(METRIC_ARTIFACT_SYNC_FAILED, DESC_ARTIFACT_SYNC_FAILED, resultTags).increment()
        }
    }

    // ==================== Private Helper Methods（私有辅助方法） ====================

    /**
     * 获取或创建 Counter
     * 使用缓存避免重复注册
     */
    @Suppress("UNCHECKED_CAST")
    private fun getOrCreateCounter(
        name: String,
        description: String,
        tags: Map<String, String> = emptyMap()
    ): Counter {
        val cacheKey = buildCacheKey(name, tags)
        return meterCache.computeIfAbsent(cacheKey) {
            Counter.builder(name)
                .description(description)
                .apply { tags.forEach { (k, v) -> tag(k, v) } }
                .register(registry)
        } as Counter
    }

    /**
     * 获取或创建 Timer
     * 使用缓存避免重复注册
     */
    @Suppress("UNCHECKED_CAST")
    private fun getOrCreateTimer(
        name: String,
        description: String,
        tags: Map<String, String> = emptyMap()
    ): Timer {
        val cacheKey = buildCacheKey(name, tags)
        return meterCache.computeIfAbsent(cacheKey) {
            Timer.builder(name)
                .description(description)
                .apply { tags.forEach { (k, v) -> tag(k, v) } }
                .register(registry)
        } as Timer
    }

    /**
     * 获取或创建 DistributionSummary
     * 使用缓存避免重复注册
     */
    @Suppress("UNCHECKED_CAST")
    private fun getOrCreateDistributionSummary(
        name: String,
        description: String,
        baseUnit: String,
        tags: Map<String, String> = emptyMap()
    ): DistributionSummary {
        val cacheKey = buildCacheKey(name, tags)
        return meterCache.computeIfAbsent(cacheKey) {
            DistributionSummary.builder(name)
                .description(description)
                .baseUnit(baseUnit)
                .apply { tags.forEach { (k, v) -> tag(k, v) } }
                .register(registry)
        } as DistributionSummary
    }

    /**
     * 构建缓存键
     * 格式：metricName#tag1=value1,tag2=value2
     */
    private fun buildCacheKey(name: String, tags: Map<String, String>): String {
        if (tags.isEmpty()) {
            return name
        }
        val tagString = tags.entries
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}=${it.value}" }
        return "$name#$tagString"
    }
}
