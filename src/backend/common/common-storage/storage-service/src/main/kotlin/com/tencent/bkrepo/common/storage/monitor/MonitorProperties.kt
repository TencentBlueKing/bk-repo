package com.tencent.bkrepo.common.storage.monitor

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize
import java.time.Duration

@ConfigurationProperties("upload.monitor")
data class MonitorProperties(
    var enabled: Boolean = false,
    var fallbackLocation: String? = null,
    var enableTransfer: Boolean = false,
    var interval: Duration = Duration.ofSeconds(10),
    var dataSize: DataSize = DataSize.ofMegabytes(1),
    var timeout: Duration = Duration.ofSeconds(5),
    var timesToRestore: Int = 5,
    var timesToFallback: Int = 3
)
