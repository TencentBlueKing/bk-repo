package com.tencent.bkrepo.repository.pojo.node.user

import io.swagger.v3.oas.annotations.media.Schema


@Schema(title = "归档节点恢复请求")
data class UserNodeArchiveRestoreRequest(
    @get:Schema(title = "项目id")
    val projectId: String,
    @get:Schema(title = "仓库名称")
    val repoName: String,
    @get:Schema(title = "路径")
    val path: String?,
    @get:Schema(title = "元数据")
    val metadata: Map<String, String> = emptyMap(),
    @get:Schema(title = "恢复限制个数")
    val limit: Int = 10000,
)
