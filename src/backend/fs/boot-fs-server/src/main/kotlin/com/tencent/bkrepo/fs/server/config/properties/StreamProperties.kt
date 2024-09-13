package com.tencent.bkrepo.fs.server.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize

@ConfigurationProperties("stream")
data class StreamProperties(
    var blockSize: DataSize = DataSize.ofMegabytes(8)
)
