package com.tencent.bkrepo.repository.pojo.schedule

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "创建预约下载规则请求")
data class UserScheduledDownloadRuleCreateRequest(
    @get:Schema(title = "用户id")
    val userIds: Set<String>? = null,
    @get:Schema(title = "项目id", required = true)
    val projectId: String,
    @get:Schema(title = "仓库名称")
    val repoNames: Set<String>? = null,
    @get:Schema(title = "文件路径匹配正则表达式")
    val fullPathRegex: String? = null,
    @get:Schema(title = "元数据匹配规则")
    val metadataRules: Set<MetadataRule>? = null,
    @get:Schema(title = "CRON表达式")
    val cron: String,
    @get:Schema(title = "下载的目标路径")
    val downloadDir: String? = null,
    @get:Schema(title = "冲突处理策略", defaultValue = "OVERWRITE")
    val conflictStrategy: ScheduledDownloadConflictStrategy = ScheduledDownloadConflictStrategy.OVERWRITE,
    @get:Schema(title = "是否启用")
    val enabled: Boolean = true,
    @get:Schema(title = "是否解压")
    val extracted: Boolean = false,
    @get:Schema(title = "适用平台类型")
    val platform: Platform = Platform.ALL,
    @get:Schema(title = "规则生效范围")
    val scope: ScheduledDownloadRuleScope,
    @JsonIgnore
    var operator: String? = null,
)
