package com.tencent.bkrepo.repository.pojo.clientupgrade

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "客户端版本配置分页查询")
data class ClientVersionConfigListOption(
    @get:Schema(title = "当前页")
    val pageNumber: Int = DEFAULT_PAGE_NUMBER,
    @get:Schema(title = "分页大小")
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    @get:Schema(title = "制品产品线")
    val productId: String? = null,
    @get:Schema(title = "平台")
    val platform: String? = null,
    @get:Schema(title = "架构")
    val arch: String? = null,
    @get:Schema(title = "架构列表（与 arch 二选一，优先 arch）")
    val archs: List<String>? = null,
    @get:Schema(title = "目标用户，精确匹配")
    val targetUserId: String? = null,
    @get:Schema(title = "范围：all=全部，global=仅全员，user=仅用户灰度")
    val scope: String? = null,
    @get:Schema(title = "是否启用")
    val enabled: Boolean? = null,
    @get:Schema(title = "最新版本，精确匹配")
    val latestVersion: String? = null,
    @get:Schema(
        title = "排序字段",
        allowableValues = [
            "productId", "platform", "arch", "targetUserId", "latestVersion",
            "lastModifiedDate", "createdDate", "enabled",
        ],
    )
    val sortField: String? = "lastModifiedDate",
    @get:Schema(title = "排序方向", allowableValues = ["ASC", "DESC"])
    val sortDirection: String? = "DESC",
)
