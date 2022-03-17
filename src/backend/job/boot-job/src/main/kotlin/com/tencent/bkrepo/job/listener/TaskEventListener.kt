package com.tencent.bkrepo.job.listener

import com.tencent.bkrepo.job.listener.event.TaskExecutedEvent
import com.tencent.bkrepo.job.metrics.JobMetrics
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 任务监听器
 * */
@Component
class TaskEventListener(private val jobMetrics: JobMetrics) {

    @EventListener(TaskExecutedEvent::class)
    fun listen(event: TaskExecutedEvent) {
        with(event) {
            logger.info("Receive taskExecutedEvent:$event")
            jobMetrics.jobTasksCounter.increment(doneCount.toDouble())
            jobMetrics.jobAvgWaitTimeConsumeTimer.record(avgWaitTime)
            jobMetrics.jobAvgExecuteTimeConsumeTimer.record(avgExecuteTime)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TaskExecutedEvent::class.java)
    }
}
