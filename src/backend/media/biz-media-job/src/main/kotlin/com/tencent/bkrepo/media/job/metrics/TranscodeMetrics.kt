package com.tencent.bkrepo.media.job.metrics

import com.tencent.bkrepo.common.metrics.constant.TAG_PROJECT_ID
import com.tencent.bkrepo.common.metrics.constant.TAG_STATUS
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_DONE_COUNT
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_DONE_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_FAIL_COUNT
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_FAIL_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_INIT_COUNT
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_INIT_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_QUEUE_COUNT
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_QUEUE_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_RUNNING_COUNT
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_RUNNING_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_STATUS_CHANGE_COUNT
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_STATUS_CHANGE_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_SUCCESS_COUNT
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_SUCCESS_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_WAITING_COUNT
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_WAITING_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_YESTERDAY_STATUS_COUNT
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_YESTERDAY_STATUS_COUNT_DESC
import com.tencent.bkrepo.media.common.dao.MediaTranscodeJobDao
import com.tencent.bkrepo.media.common.model.TMediaTranscodeJob
import com.tencent.bkrepo.media.common.pojo.transcode.MediaTranscodeJobStatus
import com.tencent.bkrepo.media.job.config.MediaJobProperties
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Component
class TranscodeMetrics(
    private val mediaTranscodeJobDao: MediaTranscodeJobDao,
    private val mediaJobProperties: MediaJobProperties,
) : MeterBinder {

    /**
     * 昨日创建任务按 (projectId, status) 缓存的计数
     * key = "$projectId:$status", value = count
     */
    private val yesterdayCache = ConcurrentHashMap<String, Double>()

    /**
     * 已注册的昨日 Gauge 的 key 集合，避免重复注册
     */
    private val registeredGaugeKeys = ConcurrentHashMap.newKeySet<String>()

    override fun bindTo(registry: MeterRegistry) {
        Companion.registry = registry
        // 全局 WAITING 状态指标
        Gauge.builder(TRANSCODE_JOB_WAITING_COUNT) {
            mediaTranscodeJobDao.count(
                Query(where(TMediaTranscodeJob::status).isEqualTo(MediaTranscodeJobStatus.WAITING))
            ).toDouble()
        }
            .description(TRANSCODE_JOB_WAITING_COUNT_DESC)
            .register(registry)

        // 全局 QUEUE 状态指标
        Gauge.builder(TRANSCODE_JOB_QUEUE_COUNT) {
            mediaTranscodeJobDao.count(
                Query(where(TMediaTranscodeJob::status).isEqualTo(MediaTranscodeJobStatus.QUEUE))
            ).toDouble()
        }
            .description(TRANSCODE_JOB_QUEUE_COUNT_DESC)
            .register(registry)

        // 全局 INIT 状态指标
        Gauge.builder(TRANSCODE_JOB_INIT_COUNT) {
            mediaTranscodeJobDao.count(
                Query(where(TMediaTranscodeJob::status).isEqualTo(MediaTranscodeJobStatus.INIT))
            ).toDouble()
        }
            .description(TRANSCODE_JOB_INIT_COUNT_DESC)
            .register(registry)

        // 全局 RUNNING 状态指标
        Gauge.builder(TRANSCODE_JOB_RUNNING_COUNT) {
            mediaTranscodeJobDao.count(
                Query(where(TMediaTranscodeJob::status).isEqualTo(MediaTranscodeJobStatus.RUNNING))
            ).toDouble()
        }
            .description(TRANSCODE_JOB_RUNNING_COUNT_DESC)
            .register(registry)

        // 全局 SUCCESS 状态指标
        Gauge.builder(TRANSCODE_JOB_SUCCESS_COUNT) {
            mediaTranscodeJobDao.count(
                Query(where(TMediaTranscodeJob::status).isEqualTo(MediaTranscodeJobStatus.SUCCESS))
            ).toDouble()
        }
            .description(TRANSCODE_JOB_SUCCESS_COUNT_DESC)
            .register(registry)

        // 全局 FAIL 状态指标
        Gauge.builder(TRANSCODE_JOB_FAIL_COUNT) {
            mediaTranscodeJobDao.count(
                Query(where(TMediaTranscodeJob::status).isEqualTo(MediaTranscodeJobStatus.FAIL))
            ).toDouble()
        }
            .description(TRANSCODE_JOB_FAIL_COUNT_DESC)
            .register(registry)

        // 全局 DONE 状态指标
        Gauge.builder(TRANSCODE_JOB_DONE_COUNT) {
            mediaTranscodeJobDao.count(
                Query(where(TMediaTranscodeJob::status).isEqualTo(MediaTranscodeJobStatus.DONE))
            ).toDouble()
        }
            .description(TRANSCODE_JOB_DONE_COUNT_DESC)
            .register(registry)
    }

    /**
     * 定时刷新昨日创建任务的按项目+状态统计缓存，每5分钟执行一次
     */
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    fun refreshYesterdayMetrics() {
        try {
            val results = mediaTranscodeJobDao.jobCountByProjectAndStatus(
                mediaJobProperties.metricsLookbackDays
            )
            val newKeys = mutableSetOf<String>()
            for (result in results) {
                val key = "${result.projectId}:${result.status}"
                newKeys.add(key)
                yesterdayCache[key] = result.count.toDouble()
                // 动态注册 Gauge（首次出现的 key）
                if (registeredGaugeKeys.add(key)) {
                    val projectId = result.projectId
                    val status = result.status
                    Gauge.builder(TRANSCODE_JOB_YESTERDAY_STATUS_COUNT) {
                        yesterdayCache.getOrDefault("$projectId:$status", 0.0)
                    }
                        .description(TRANSCODE_JOB_YESTERDAY_STATUS_COUNT_DESC)
                        .tag(TAG_PROJECT_ID, projectId)
                        .tag(TAG_STATUS, status)
                        .register(registry)
                }
            }
            // 将不再出现的 key 置零
            for (existingKey in yesterdayCache.keys) {
                if (existingKey !in newKeys) {
                    yesterdayCache[existingKey] = 0.0
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to refresh yesterday transcode metrics", e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TranscodeMetrics::class.java)
        lateinit var registry: MeterRegistry

        /**
         * 记录转码任务状态变更
         */
        fun recordStatusChange(projectId: String, status: MediaTranscodeJobStatus) {
            try {
                Counter.builder(TRANSCODE_JOB_STATUS_CHANGE_COUNT)
                    .description(TRANSCODE_JOB_STATUS_CHANGE_COUNT_DESC)
                    .tag(TAG_PROJECT_ID, projectId)
                    .tag(TAG_STATUS, status.name)
                    .register(registry)
                    .increment()
            } catch (e: Exception) {
                logger.error("Failed to record status change metric", e)
            }
        }
    }
}
