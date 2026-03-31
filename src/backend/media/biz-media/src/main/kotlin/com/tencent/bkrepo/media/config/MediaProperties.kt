package com.tencent.bkrepo.media.config

import cn.hutool.core.io.unit.DataSize
import com.tencent.bkrepo.media.stream.TranscodeConfig
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "media")
class MediaProperties {
    var maxRecordFileSize: DataSize = DataSize.ofGigabytes(100)
    var serverAddress: String = ""
    var grayServerAddress: String = ""
    var transcodeConfig: Map<String, TranscodeConfig> = mutableMapOf()
    var repoHost: String = ""
    var storageCredentialsKey: String? = null
    var enabledLiveProjects: List<String> = mutableListOf()
    var rtcSecret: String = "rtc-stream-pull-secret-2m98cx37yr21"
    var remoteDevHost: String = ""
    var plugin: PluginProperties = PluginProperties()
}

class PluginProperties {
    var devx: DevxProperties = DevxProperties()
}

class DevxProperties {
    var key: String = ""
    var devops: DevopsProperties = DevopsProperties()
    var srtUrl: String = ""
    var mediaMode: String = "BOTH"
}

class DevopsProperties {
    var appCode: String = ""
    var appSecret: String = ""
    var url: String = ""
}
