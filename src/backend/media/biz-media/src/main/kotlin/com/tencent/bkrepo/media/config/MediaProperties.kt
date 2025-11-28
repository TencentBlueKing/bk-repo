package com.tencent.bkrepo.media.config

import cn.hutool.core.io.unit.DataSize
import com.tencent.bkrepo.media.stream.TranscodeConfig
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "media")
data class MediaProperties(
    var maxRecordFileSize: DataSize = DataSize.ofGigabytes(100),
    var serverAddress: String = "",
    var transcodeConfig: Map<String, TranscodeConfig> = mutableMapOf(),
    var repoHost: String = "",
    var storageCredentialsKey: String? = null,
)
