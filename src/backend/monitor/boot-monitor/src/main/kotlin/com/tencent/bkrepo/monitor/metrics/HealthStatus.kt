package com.tencent.bkrepo.monitor.metrics

import org.springframework.boot.actuate.health.Status

data class HealthStatus(
    val status: Status,
    val details: Map<String, Any> = emptyMap()
)
