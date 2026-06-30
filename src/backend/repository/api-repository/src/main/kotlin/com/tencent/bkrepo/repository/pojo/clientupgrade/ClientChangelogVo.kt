package com.tencent.bkrepo.repository.pojo.clientupgrade

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 管理端 changelog 完整记录。
 */
@Schema(title = "客户端 changelog 完整记录")
data class ClientChangelogVo(
    @get:Schema(title = "记录 ID")
    val id: String?,
    @get:Schema(title = "产品 ID，如 bk_artifacts_ui")
    val productId: String,
    @get:Schema(title = "版本号")
    val version: String,
    @get:Schema(title = "发布日期（展示用），如 2026-06-16")
    val releasedAt: String,
    @get:Schema(title = "发布状态：DRAFT / PUBLISHED")
    val status: ClientChangelogStatus,
    @get:Schema(title = "更新日志正文（Markdown 格式）")
    val releaseNotes: String,
    @get:Schema(title = "创建人")
    val createdBy: String,
    @get:Schema(title = "创建时间")
    val createdDate: LocalDateTime,
    @get:Schema(title = "修改人")
    val lastModifiedBy: String,
    @get:Schema(title = "修改时间")
    val lastModifiedDate: LocalDateTime,
)
