package com.tencent.bkrepo.common.artifact.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger
import javax.annotation.Resource

@Component
class ArtifactMetrics: MeterBinder {
    var uploadingCount = AtomicInteger(0)
    var downloadingCount = AtomicInteger(0)

    lateinit var uploadedCounter: Counter
    lateinit var downloadedCounter: Counter
    lateinit var uploadedBytesCounter: Counter
    lateinit var uploadedConsumeCounter: Counter
    lateinit var downloadedBytesCounter: Counter
    lateinit var downloadedConsumeCounter: Counter

    @Resource
    lateinit var taskAsyncExecutor: ThreadPoolTaskExecutor

    override fun bindTo(meterRegistry: MeterRegistry) {
        Gauge.builder(ARTIFACT_UPLOADING_COUNT, uploadingCount, { it.get().toDouble() })
            .description(ARTIFACT_UPLOADING_COUNT_DESC)
            .register(meterRegistry)

        Gauge.builder(ARTIFACT_DOWNLOADING_COUNT, downloadingCount, { it.get().toDouble() })
            .description(ARTIFACT_DOWNLOADING_COUNT_DESC)
            .register(meterRegistry)

        uploadedCounter = Counter.builder(ARTIFACT_UPLOADED_COUNT)
            .description(ARTIFACT_UPLOADED_COUNT_DESC)
            .register(meterRegistry)

        downloadedCounter = Counter.builder(ARTIFACT_DOWNLOADED_COUNT)
            .description(ARTIFACT_DOWNLOADED_COUNT_DESC)
            .register(meterRegistry)

        uploadedBytesCounter = Counter.builder(ARTIFACT_UPLOADED_BYTES_COUNT)
            .description(ARTIFACT_UPLOADED_BYTES_COUNT_DESC)
            .register(meterRegistry)

        uploadedConsumeCounter = Counter.builder(ARTIFACT_UPLOADED_CONSUME_COUNT)
            .description(ARTIFACT_UPLOADED_CONSUME_COUNT_DESC)
            .register(meterRegistry)

        downloadedBytesCounter = Counter.builder(ARTIFACT_DOWNLOADED_BYTES_COUNT)
            .description(ARTIFACT_DOWNLOADED_BYTES_COUNT_DESC)
            .register(meterRegistry)

        downloadedConsumeCounter = Counter.builder(ARTIFACT_DOWNLOADED_CONSUME_COUNT)
            .description(ARTIFACT_DOWNLOADED_CONSUME_COUNT_DESC)
            .register(meterRegistry)

        Gauge.builder(ASYNC_TASK_ACTIVE_COUNT, taskAsyncExecutor.threadPoolExecutor, { it.activeCount.toDouble() })
            .description(ASYNC_TASK_ACTIVE_COUNT_DESC)
            .register(meterRegistry)

        Gauge.builder(ASYNC_TASK_QUEUE_SIZE, taskAsyncExecutor.threadPoolExecutor, { it.queue.size.toDouble() })
            .description(ASYNC_TASK_QUEUE_SIZE_DESC)
            .register(meterRegistry)
    }
}