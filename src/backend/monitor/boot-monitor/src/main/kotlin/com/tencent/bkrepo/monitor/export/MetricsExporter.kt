package com.tencent.bkrepo.monitor.export

import com.tencent.bkrepo.monitor.metrics.MetricInfo

interface MetricsExporter {
    fun export(metricInfo: MetricInfo)
}
