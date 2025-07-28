package com.tencent.bkrepo.repository.pojo.schedule

import io.swagger.v3.oas.annotations.media.Schema

data class ScheduleLoadCreateRequest(
    @get:Schema(title = "用户id")
    val userId: String,
    @get:Schema(title = "项目id")
    val projectId: String,
    @get:Schema(title = "仓库名称")
    val repoName: String,
    @get:Schema(title = "文件匹配路径")
    val fullPathRegex: String,
    @get:Schema(title = "元数据")
    val nodeMetadata: Map<String, Any>,
    @get:Schema(title = "CRON表达式")
    val cronExpression: String,
    @get:Schema(title = "是否覆盖")
    val isCovered: Boolean,
    @get:Schema(title = "是否启用")
    val isEnabled: Boolean,
    @get:Schema(title = "适用平台类型")
    val platform: SchedulePlatformType,
    @get:Schema(title = "级别类型")
    val type: ScheduleType
)
