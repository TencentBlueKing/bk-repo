package com.tencent.bkrepo.repository.pojo.schedule

import io.swagger.v3.oas.annotations.media.Schema

data class ScheduleResult(
    @get:Schema(title = "id")
    val id: String?,
    @get:Schema(title = "项目id")
    val projectId: String,
    @get:Schema(title = "流水线id")
    val pipeLineId: String,
    @get:Schema(title = "构建版本")
    val buildId: String,
    @get:Schema(title = "预约时间")
    val cronExpression: String,
    @get:Schema(title = "是否启用")
    val isEnabled: Boolean,
    @get:Schema(title = "使用平台")
    var platform: SchedulePlatformType,
    @get:Schema(title = "预约规则")
    val rules: List<ScheduleRule>,
)
