package com.tencent.bkrepo.repository.pojo.clientupgrade

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "客户端 changelog 新增/更新请求")
data class ClientChangelogUpsertRequest(
    @get:Schema(title = "记录 ID；存在则按 ID 更新，否则按 (productId, version) 唯一键 upsert")
    val id: String? = null,
    @get:Schema(title = "产品 ID", required = true)
    val productId: String,
    @get:Schema(title = "版本号", required = true)
    val version: String,
    @get:Schema(title = "发布日期（展示用），如 2026-06-16", required = true)
    val releasedAt: String,
    @get:Schema(title = "发布状态：DRAFT / PUBLISHED")
    val status: ClientChangelogStatus = ClientChangelogStatus.DRAFT,
    @get:Schema(title = "更新日志正文（Markdown 格式），不能为空", required = true)
    val releaseNotes: String,
)
