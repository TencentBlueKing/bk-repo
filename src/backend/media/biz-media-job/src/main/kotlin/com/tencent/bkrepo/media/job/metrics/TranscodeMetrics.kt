package com.tencent.bkrepo.media.job.metrics

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
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_SUCCESS_COUNT
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_SUCCESS_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_WAITING_COUNT
import com.tencent.bkrepo.common.metrics.constant.TRANSCODE_JOB_WAITING_COUNT_DESC
import com.tencent.bkrepo.media.common.dao.MediaTranscodeJobDao
import com.tencent.bkrepo.media.common.model.TMediaTranscodeJob
import com.tencent.bkrepo.media.common.pojo.transcode.MediaTranscodeJobStatus
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component

@Component
class TranscodeMetrics(
    private val mediaTranscodeJobDao: MediaTranscodeJobDao
) : MeterBinder {
    override fun bindTo(registry: MeterRegistry) {
        // WAITING 状态指标
        Gauge.builder(TRANSCODE_JOB_WAITING_COUNT) {
            mediaTranscodeJobDao.count(
                Query(where(TMediaTranscodeJob::status).isEqualTo(MediaTranscodeJobStatus.WAITING))
            ).toDouble()
        }
            .description(TRANSCODE_JOB_WAITING_COUNT_DESC)
            .register(registry)

        // QUEUE 状态指标
        Gauge.builder(TRANSCODE_JOB_QUEUE_COUNT) {
            mediaTranscodeJobDao.count(
                Query(where(TMediaTranscodeJob::status).isEqualTo(MediaTranscodeJobStatus.QUEUE))
            ).toDouble()
        }
            .description(TRANSCODE_JOB_QUEUE_COUNT_DESC)
            .register(registry)

        // INIT 状态指标
        Gauge.builder(TRANSCODE_JOB_INIT_COUNT) {
            mediaTranscodeJobDao.count(
                Query(where(TMediaTranscodeJob::status).isEqualTo(MediaTranscodeJobStatus.INIT))
            ).toDouble()
        }
            .description(TRANSCODE_JOB_INIT_COUNT_DESC)
            .register(registry)

        // RUNNING 状态指标
        Gauge.builder(TRANSCODE_JOB_RUNNING_COUNT) {
            mediaTranscodeJobDao.count(
                Query(where(TMediaTranscodeJob::status).isEqualTo(MediaTranscodeJobStatus.RUNNING))
            ).toDouble()
        }
            .description(TRANSCODE_JOB_RUNNING_COUNT_DESC)
            .register(registry)

        // SUCCESS 状态指标
        Gauge.builder(TRANSCODE_JOB_SUCCESS_COUNT) {
            mediaTranscodeJobDao.count(
                Query(where(TMediaTranscodeJob::status).isEqualTo(MediaTranscodeJobStatus.SUCCESS))
            ).toDouble()
        }
            .description(TRANSCODE_JOB_SUCCESS_COUNT_DESC)
            .register(registry)

        // FAIL 状态指标
        Gauge.builder(TRANSCODE_JOB_FAIL_COUNT) {
            mediaTranscodeJobDao.count(
                Query(where(TMediaTranscodeJob::status).isEqualTo(MediaTranscodeJobStatus.FAIL))
            ).toDouble()
        }
            .description(TRANSCODE_JOB_FAIL_COUNT_DESC)
            .register(registry)

        // DONE 状态指标
        Gauge.builder(TRANSCODE_JOB_DONE_COUNT) {
            mediaTranscodeJobDao.count(
                Query(where(TMediaTranscodeJob::status).isEqualTo(MediaTranscodeJobStatus.DONE))
            ).toDouble()
        }
            .description(TRANSCODE_JOB_DONE_COUNT_DESC)
            .register(registry)
    }
}