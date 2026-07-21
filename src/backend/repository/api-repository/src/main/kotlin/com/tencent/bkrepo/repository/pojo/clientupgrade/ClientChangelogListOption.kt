package com.tencent.bkrepo.repository.pojo.clientupgrade

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "客户端 changelog 分页查询")
data class ClientChangelogListOption(
    @get:Schema(title = "当前页")
    val pageNumber: Int = DEFAULT_PAGE_NUMBER,
    @get:Schema(title = "分页大小")
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    @get:Schema(title = "产品 ID")
    val productId: String? = null,
    @get:Schema(title = "版本号，精确匹配")
    val version: String? = null,
    @get:Schema(title = "发布状态：DRAFT / PUBLISHED；为空表示全部")
    val status: ClientChangelogStatus? = null,
    @get:Schema(
        title = "排序字段",
        allowableValues = [
            "version", "releasedAt", "lastModifiedDate", "createdDate", "status",
        ],
    )
    val sortField: String? = "releasedAt",
    @get:Schema(title = "排序方向", allowableValues = ["ASC", "DESC"])
    val sortDirection: String? = "DESC",
)
