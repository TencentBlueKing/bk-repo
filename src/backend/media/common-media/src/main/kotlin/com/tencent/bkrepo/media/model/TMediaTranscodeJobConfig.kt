package com.tencent.bkrepo.media.model

import org.springframework.data.mongodb.core.mapping.Document

@Document("media_transcode_job_config")
data class TMediaTranscodeJobConfig(
    // 未来可能会对不同项目做配置，没有项目的就是公共配置
    var projectId: String?,
    // 每次同时构建的最大任务数
    var maxJobCount: Int?,
)
