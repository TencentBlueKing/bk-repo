package com.tencent.bkrepo.monitor.metrics

data class HealthInfo(
    val name: String,
    val status: HealthStatus,
    val application: String,
    val instance: String
)
