package com.tencent.bkrepo.monitor.export

import com.tencent.bkrepo.monitor.metrics.MetricInfo

class InfluxMetricsConverter {
    fun convert(metric: MetricInfo): String {
        val builder = StringBuilder()
        builder.append(name(metric))
            .append(tags(metric))
            .append(metricType())
            .append(" ")
            .append(fields(metric))
            .append(" ")
            .append(System.currentTimeMillis())
            .append("\n")
        return builder.toString()
    }

    private fun name(metric: MetricInfo): String {
        return toSnakeCase(metric.name)
    }

    private fun tags(metric: MetricInfo): String {
        return metric.availableTags.joinToString("") { ",${toSnakeCase(it.tag)}=${toSnakeCase(it.values.first())}" }
    }

    private fun metricType(): String {
        return ",metric_type=gauge"
    }

    private fun fields(metric: MetricInfo): String {
        return metric.measurements.joinToString(",") { "${it.statistic.tagValueRepresentation}=${it.value}" }
    }

    private fun toSnakeCase(value: String) = value.split(".").joinToString("_")
}
