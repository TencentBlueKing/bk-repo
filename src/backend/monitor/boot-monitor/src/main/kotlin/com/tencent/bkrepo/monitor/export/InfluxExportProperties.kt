package com.tencent.bkrepo.monitor.export

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("monitor.export.influx")
data class InfluxExportProperties(
    var enabled: Boolean = false,
    var step: Duration = Duration.ofMinutes(1),
    var db: String = "mydb",
    var consistency: String = "ONE",
    var retentionPolicy: String? = null,
    var retentionDuration: String? = null,
    var retentionReplicationFactor: Int? = null,
    var retentionShardDuration: String? = null,
    var username: String? = null,
    var password: String? = null,
    var uri: String = "http://localhost:8086",
    var autoCreateDb: Boolean = true
)
