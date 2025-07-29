package com.tencent.bkrepo.repository.pojo.schedule

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "查询预约下载规则请求")
data class UserScheduledDownloadRuleQueryRequest(
    @get:Schema(title = "项目id")
    val projectId: String,
    @get:Schema(title = "用户id")
    val userIds: Set<String>? = null,
    @get:Schema(title = "仓库名称")
    val repoNames: Set<String>? = null,
    @get:Schema(title = "元数据匹配规则")
    val metadataRules: Set<MetadataRule>? = null,
    @get:Schema(title = "是否启用")
    val enabled: Boolean? = null,
    @get:Schema(title = "适用平台")
    val platform: Platform? = null,
    @get:Schema(title = "规则生效范围")
    val scope: ScheduledDownloadRuleScope? = null,
    @get:Schema(title = "页码")
    val pageNumber: Int = DEFAULT_PAGE_NUMBER,
    @get:Schema(title = "页大小")
    val pageSize: Int = DEFAULT_PAGE_SIZE
)
