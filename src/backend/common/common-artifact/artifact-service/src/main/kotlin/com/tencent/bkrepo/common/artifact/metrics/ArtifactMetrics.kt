package com.tencent.bkrepo.common.artifact.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import javax.annotation.Resource

@Component
class ArtifactMetrics: MeterBinder {
    lateinit var uploadCount: Counter
    lateinit var downloadCount: Counter

    @Resource
    lateinit var taskAsyncExecutor: ThreadPoolTaskExecutor

    override fun bindTo(meterRegistry: MeterRegistry) {
        uploadCount = Counter.builder(ARTIFACT_UPLOADING_COUNT)
            .description(ARTIFACT_UPLOADING_COUNT_DESC)
            .register(meterRegistry)

        downloadCount = Counter.builder(ARTIFACT_DOWNLOADING_COUNT)
            .description(ARTIFACT_DOWNLOADING_COUNT_DESC)
            .register(meterRegistry)

        Gauge.builder(ASYNC_TASK_ACTIVE_COUNT, taskAsyncExecutor.threadPoolExecutor, { it.activeCount.toDouble() })
            .description(ASYNC_TASK_ACTIVE_COUNT_DESC)
            .register(meterRegistry)

        Gauge.builder(ASYNC_TASK_QUEUE_SIZE, taskAsyncExecutor.threadPoolExecutor, { it.queue.size.toDouble() })
            .description(ASYNC_TASK_QUEUE_SIZE_DESC)
            .register(meterRegistry)
    }
}