package com.tencent.bkrepo.archive.config

import org.springframework.util.unit.DataSize
import java.time.Duration

data class DownloadProperties(
    var path: String = System.getProperty("java.io.tmpdir"),
    var ioThreads: Int = Runtime.getRuntime().availableProcessors(),
    var healthCheckInterval: Duration = Duration.ofMinutes(1),
    var lowWaterMark: DataSize = DataSize.ofGigabytes(10),
    var highWaterMark: DataSize = DataSize.ofGigabytes(100),
)
