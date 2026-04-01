package com.tencent.bkrepo.media.common.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 直播模式配置表
 * 用于配置哪些工作空间可以使用新的直播模式
 * projectId、userId、workspaceId 三选一填入即可
 */
@Document("media_live_config")
@CompoundIndexes(
    CompoundIndex(
        name = "media_live_config_idx",
        def = "{'projectId': 1, 'userId': 1, 'workspaceId': 1}",
        unique = true,
        background = true,
    )
)
data class TMediaLiveConfig(
    var id: String?,
    /** 项目ID，可为空 */
    var projectId: String? = null,
    /** 用户ID，可为空 */
    var userId: String? = null,
    /** 工作空间ID，可为空 */
    var workspaceId: String? = null,
    /** 是否启用新的直播模式 */
    var enabled: Boolean = true,
    var createdBy: String,
    var createdTime: LocalDateTime,
    var updatedBy: String,
    var updateTime: LocalDateTime,
)
