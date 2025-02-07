package com.tencent.bkrepo.common.artifact.cache.pojo

import io.swagger.v3.oas.annotations.media.Schema


@Schema(title = "预加载执行计划创建清理")
data class ArtifactPreloadPlanCreateRequest(
    @get:Schema(title = "所属项目ID")
    val projectId: String,
    @get:Schema(title = "所属仓库")
    val repoName: String,
    @get:Schema(title = "待加载制品的完整路径")
    val fullPath: String,
    @get:Schema(title = "预加载计划执行毫秒时间戳")
    val executeTime: Long,
)
