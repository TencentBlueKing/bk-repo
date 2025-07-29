package com.tencent.bkrepo.repository.pojo.schedule

import com.fasterxml.jackson.annotation.JsonInclude
import com.tencent.bkrepo.common.metadata.pojo.node.ConflictStrategy
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(title = "预约下载规则")
data class ScheduledDownloadRule(
    @get:Schema(title = "id")
    val id: String?,
    @get:Schema(title = "生效的用户id列表")
    var userIds: Set<String>?,
    @get:Schema(title = "项目id")
    val projectId: String,
    @get:Schema(title = "仓库名称")
    val repoNames: Set<String>?,
    @get:Schema(title = "路径匹配正则表达式")
    val fullPathRegex: String?,
    @get:Schema(title = "元数据匹配规则")
    val metadataRules: Set<MetadataRule>?,
    @get:Schema(title = "预约时间")
    val cron: String,
    @get:Schema(title = "下载目标路径")
    var downloadDir: String? = null,
    @get:Schema(title = "冲突处理策略")
    var conflictStrategy: ConflictStrategy,
    @get:Schema(title = "是否启用")
    val enabled: Boolean,
    @get:Schema(title = "规则所属平台")
    val platform: Platform,
    @get:Schema(title = "规则生效范围")
    val scope: ScheduledDownloadRuleScope,
 )
