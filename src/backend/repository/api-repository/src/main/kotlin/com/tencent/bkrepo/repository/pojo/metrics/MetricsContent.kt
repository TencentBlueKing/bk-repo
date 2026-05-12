package com.tencent.bkrepo.repository.pojo.metrics

data class MetricsContent(
    val metricName: String,
    val metricHelp: String,
    val metricDataModel: String,
    val value: String,
    val keepHistory: Boolean = true,
    val labels: MutableMap<String, String> = mutableMapOf(),
)
