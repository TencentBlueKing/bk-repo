package com.tencent.bkrepo.repository.pojo.schedule

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "创建预约下载")
data class ScheduleRequest(
    @get:Schema(title = "项目id")
    val projectId: String,
    @get:Schema(title = "流水线id")
    val pipeLineId: String,
    @get:Schema(title = "构建id")
    val buildId: String,
    @get:Schema(title = "CRON表达式")
    val cronExpression: String,
    @get:Schema(title = "是否启用")
    val isEnabled: Boolean,
    @get:Schema(title = "适用平台类型")
    val platform: SchedulePlatformType,
    @get:Schema(title = "预约规则")
    val rules: Map<String, Any>,
)
