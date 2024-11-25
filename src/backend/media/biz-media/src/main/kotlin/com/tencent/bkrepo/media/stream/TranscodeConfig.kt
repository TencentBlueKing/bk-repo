package com.tencent.bkrepo.media.stream

data class TranscodeConfig(
    var scale: String = "", // 分辨率,比如1280x720
    var videoCodec: String = "", // 视频编码
    var audioCodec: String = "", // 音频编码
    var jobId: String = "", // 转码任务id，
    var extraParams: String? = null,
)
