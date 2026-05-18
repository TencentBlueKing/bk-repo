package com.tencent.bkrepo.repository.pojo.metrics

data class MetricsPushConfigResponse(
    val enabled: Boolean,
    val intervalSeconds: Long,
)
