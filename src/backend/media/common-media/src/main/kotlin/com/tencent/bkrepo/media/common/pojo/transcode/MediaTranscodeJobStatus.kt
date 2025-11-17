package com.tencent.bkrepo.media.common.pojo.transcode

enum class MediaTranscodeJobStatus {
    WAITING, // 在数据库等待
    QUEUE, // 从数据库捞出来开始调度
    INIT, // 任务创建完成
    RUNNING,
    SUCCESS,
    FAIL;
}