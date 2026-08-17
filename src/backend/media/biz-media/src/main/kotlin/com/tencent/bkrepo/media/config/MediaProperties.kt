package com.tencent.bkrepo.media.config

import cn.hutool.core.io.unit.DataSize
import com.tencent.bkrepo.media.stream.MediaMod
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
    var reconnectByRepoProjects: MutableSet<String> = mutableSetOf()
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
    var srtOpen: Boolean = true
    var mediaMode: String = MediaMod.ALL.name
    var enableDecodeResync: Boolean = true
    var snapshot: List<SnapshotConfig> = mutableListOf()
}

class SnapshotConfig {
    /** 启用抽帧的项目 ID */
    var projectId: String = ""
    /** 抽帧间隔（秒），默认 10 */
    var intervalSec: Int = 10
    /** 该项目对应的 COS 配置，抽帧生成 JPEG 后直接上传到该 COS */
    var cos: CosProperties = CosProperties()
}

class CosProperties {
    var secretId: String = ""
    var secretKey: String = ""
    var region: String = ""
    var bucket: String = ""
    var endpoint: String = ""
}

class DevopsProperties {
    var appCode: String = ""
    var appSecret: String = ""
    var url: String = ""
}
