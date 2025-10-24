package com.tencent.bkrepo.media.stream

data class TranscodeParam(
    val inputUrl: String, // 源文件下载路径
    val callbackUrl: String, // 转码后回调地址，上传转码后的文件
    val reportUrl: String, // 任务上报地址
    val scale: String? = null, // 分辨率,比如1280x720
    val videoCodec: String? = null, // 视频编码
    val audioCodec: String? = null, // 音频编码
    var inputFileName: String, // 源文件名
    var outputFileName: String, // 输出文件名
    var extraParams: String, // 额外参数
    var extraFiles: List<Map<String, String>>?, // 额外文件
    var author: String?, // 云桌面使用人
    var videoStartTime: Long?, // 录屏开始时间
    var videoEndTime: Long?, // 录屏结束时间
)
