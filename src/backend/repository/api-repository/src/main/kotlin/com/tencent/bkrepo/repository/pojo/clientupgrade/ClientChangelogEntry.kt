package com.tencent.bkrepo.repository.pojo.clientupgrade

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 客户端可见的 changelog 简要信息。
 * 仅用于 GET /api/client/upgrade/changelog 与 /history 接口返回。
 */
@Schema(title = "客户端 changelog 条目")
data class ClientChangelogEntry(
    @get:Schema(title = "版本号", required = true)
    val version: String,
    @get:Schema(title = "发布日期，展示用，如 2026-06-16", required = true)
    val releasedAt: String,
    @get:Schema(title = "更新日志正文（Markdown 格式）", required = true)
    val releaseNotes: String,
)
