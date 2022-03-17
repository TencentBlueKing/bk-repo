package com.tencent.bkrepo.job.metrics

import com.tencent.bkrepo.job.ASYNC_TASK_ACTIVE_COUNT
import com.tencent.bkrepo.job.ASYNC_TASK_ACTIVE_COUNT_DESC
import com.tencent.bkrepo.job.ASYNC_TASK_QUEUE_SIZE
import com.tencent.bkrepo.job.ASYNC_TASK_QUEUE_SIZE_DESC
import com.tencent.bkrepo.job.JOB_AVG_EXECUTE_TIME_CONSUME_DESC
import com.tencent.bkrepo.job.JOB_AVG_TIME_CONSUME
import com.tencent.bkrepo.job.JOB_AVG_WAIT_TIME_CONSUME_DESC
import com.tencent.bkrepo.job.JOB_TASK_COUNT
import com.tencent.bkrepo.job.JOB_TASK_COUNT_DESC
import com.tencent.bkrepo.job.RUNNING_TASK_JOB_COUNT
import com.tencent.bkrepo.job.RUNNING_TASK_JOB_DESC
import com.tencent.bkrepo.job.executor.BlockThreadPoolTaskExecutorDecorator
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Component

/**
 * Job服务度量指标
 * */
@Component
class JobMetrics(
    val threadPoolTaskExecutor: BlockThreadPoolTaskExecutorDecorator
) : MeterBinder {

    lateinit var jobTasksCounter: Counter
    lateinit var jobAvgWaitTimeConsumeTimer: Timer
    lateinit var jobAvgExecuteTimeConsumeTimer: Timer

    override fun bindTo(registry: MeterRegistry) {
        Gauge.builder(ASYNC_TASK_ACTIVE_COUNT, threadPoolTaskExecutor) { it.activeCount().toDouble() }
            .description(ASYNC_TASK_ACTIVE_COUNT_DESC)
            .register(registry)

        Gauge.builder(ASYNC_TASK_QUEUE_SIZE, threadPoolTaskExecutor) { it.queueSize().toDouble() }
            .description(ASYNC_TASK_QUEUE_SIZE_DESC)
            .register(registry)

        Gauge.builder(RUNNING_TASK_JOB_COUNT, threadPoolTaskExecutor) { it.activeTaskCount().toDouble() }
            .description(RUNNING_TASK_JOB_DESC)
            .register(registry)
        jobTasksCounter = Counter.builder(JOB_TASK_COUNT)
            .description(JOB_TASK_COUNT_DESC)
            .register(registry)
        jobAvgWaitTimeConsumeTimer = Timer.builder(JOB_AVG_TIME_CONSUME)
            .description(JOB_AVG_WAIT_TIME_CONSUME_DESC)
            .tag("type", "waitTime")
            .register(registry)
        jobAvgExecuteTimeConsumeTimer = Timer.builder(JOB_AVG_TIME_CONSUME)
            .description(JOB_AVG_EXECUTE_TIME_CONSUME_DESC)
            .tag("type", "executeTime")
            .register(registry)
    }
}
