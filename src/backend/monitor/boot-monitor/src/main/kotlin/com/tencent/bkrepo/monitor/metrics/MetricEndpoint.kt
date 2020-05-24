package com.tencent.bkrepo.monitor.metrics

enum class MetricEndpoint(val metricName: String) {

    ARTIFACT_UPLOADING_COUNT("artifact.uploading.count"),
    ARTIFACT_DOWNLOADING_COUNT("artifact.downloading.count"),
    ASYNC_TASK_ACTIVE_COUNT("async.task.active.count"),
    ASYNC_TASK_QUEUE_SIZE("async.task.queue.size");

    fun getEndpoint() = "metrics/$metricName"

    companion object {
        fun ofMetricName(metricName: String): MetricEndpoint {
            return values().first { it.metricName == metricName }
        }
    }
}
