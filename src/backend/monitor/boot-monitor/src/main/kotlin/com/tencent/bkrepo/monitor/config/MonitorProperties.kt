package com.tencent.bkrepo.monitor.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("monitor")
data class MonitorProperties(
    var clusterName: String = "default",
    var interval: Duration = Duration.ofSeconds(10),
    var metrics: Map<String, String>,
    var health: Map<String, String>
)
