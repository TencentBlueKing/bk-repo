package com.tencent.bkrepo.repository.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 客户端更新日志（changelog）记录。
 *
 * 唯一键：(productId, version)
 * - 更新日志按产品维度组织，全平台共享同一份内容
 * - 删除采用物理删除，与 ClientVersionConfig 保持一致风格
 */
@Document("client_changelog")
@CompoundIndexes(
    CompoundIndex(
        name = "client_changelog_key_unique",
        def = "{'productId': 1, 'version': 1}",
        unique = true,
        background = true,
    ),
    CompoundIndex(
        name = "client_changelog_query_idx",
        def = "{'productId': 1, 'status': 1, 'releasedAt': -1}",
        background = true,
    ),
)
data class TClientChangelog(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,
    var productId: String,
    var version: String,
    var releasedAt: String,
    /** 发布状态字符串，对应 ClientChangelogStatus 枚举 name */
    var status: String,
    /** 更新日志正文（Markdown 格式） */
    var releaseNotes: String,
)
