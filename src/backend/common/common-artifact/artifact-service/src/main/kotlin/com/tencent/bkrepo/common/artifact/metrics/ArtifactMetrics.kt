package com.tencent.bkrepo.common.artifact.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger
import javax.annotation.Resource

@Component
class ArtifactMetrics : MeterBinder {
    var uploadCount = AtomicInteger(0)
    var downloadCount = AtomicInteger(0)

    @Resource
    lateinit var taskAsyncExecutor: ThreadPoolTaskExecutor

    override fun bindTo(meterRegistry: MeterRegistry) {
        Gauge.builder(ARTIFACT_UPLOADING_COUNT, uploadCount, { it.get().toDouble() })
            .description(ARTIFACT_UPLOADING_COUNT_DESC)
            .register(meterRegistry)

        Gauge.builder(ARTIFACT_DOWNLOADING_COUNT, downloadCount, { it.get().toDouble() })
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
