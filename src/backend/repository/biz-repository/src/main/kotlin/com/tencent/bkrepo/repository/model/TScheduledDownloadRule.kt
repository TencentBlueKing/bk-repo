package com.tencent.bkrepo.repository.model

import com.tencent.bkrepo.repository.pojo.schedule.MetadataRule
import com.tencent.bkrepo.repository.pojo.schedule.Platform
import com.tencent.bkrepo.repository.pojo.schedule.ScheduledDownloadConflictStrategy
import com.tencent.bkrepo.repository.pojo.schedule.ScheduledDownloadRuleScope
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("scheduled_download_rule")
@CompoundIndexes(
    CompoundIndex(
        name = "projectId_userIds_idx",
        def = "{'projectId': 1, 'userIds': 1}",
        background = true,
    )
)
data class TScheduledDownloadRule(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    /**
     * 指定规则生效的用户，当为项目全局规则时该字段值为null
     */
    var userIds: Set<String>? = null,

    /**
     * 规则所属项目
     */
    var projectId: String,

    /**
     * 指定需要下载的制品所属仓库，该字段值为null时表示搜索所有仓库
     */
    var repoNames: Set<String>? = null,

    /**
     * 指定需要下载的制品路径匹配规则，为null时匹配所有制品
     */
    var fullPathRegex: String? = null,

    /**
     * 制品元数据匹配规则
     */
    var metadataRules: Set<MetadataRule>? = null,

    /**
     * 预约时间cron表达式
     */
    var cron: String,

    /**
     * 下载的目标路径，为null时使用本地配置的全局下载路径
     */
    var downloadDir: String? = null,

    /**
     * 下载文件冲突处理策略
     */
    var conflictStrategy: ScheduledDownloadConflictStrategy = ScheduledDownloadConflictStrategy.OVERWRITE,

    /**
     * 是否启用规则
     */
    var enabled: Boolean = false,

    /**
     * 预约下载规则所属平台
     */
    var platform: Platform = Platform.All,

    /**
     * 生效范围
     */
    var scope: ScheduledDownloadRuleScope = ScheduledDownloadRuleScope.USER,
)
