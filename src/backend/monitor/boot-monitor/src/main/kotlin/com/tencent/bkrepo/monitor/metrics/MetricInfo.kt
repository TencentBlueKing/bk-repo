package com.tencent.bkrepo.monitor.metrics

data class MetricInfo(
    val name: String,
    val description: String?,
    val baseUnit: String?,
    val measurements: List<Sample>,
    val availableTags: List<AvailableTag>
)
