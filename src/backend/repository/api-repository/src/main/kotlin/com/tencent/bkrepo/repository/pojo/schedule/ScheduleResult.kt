package com.tencent.bkrepo.repository.pojo.schedule

import io.swagger.v3.oas.annotations.media.Schema

data class ScheduleResult(
    @get:Schema(title = "id")
    val id: String?,
    @get:Schema(title = "项目id")
    val projectId: String,
    @get:Schema(title = "仓库名称")
    val repoName: String,
    @get:Schema(title = "文件匹配路径")
    val fullPathRegex: String,
    @get:Schema(title = "元数据")
    val nodeMetadata: List<ScheduleMetadata>,
    @get:Schema(title = "预约时间")
    val cronExpression: String,
    @get:Schema(title = "是否启用")
    val isEnabled: Boolean,
    @get:Schema(title = "使用平台")
    var platform: SchedulePlatformType,
)
