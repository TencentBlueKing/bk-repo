package com.tencent.bkrepo.nuget.pojo.request


import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "删除包请求")
data class PackageDeleteRequest(
    @get:Schema(title = "所属项目", required = true)
    val projectId: String,
    @get:Schema(title = "仓库名称", required = true)
    val repoName: String,
    @get:Schema(title = "包名称", required = true)
    val name: String,
    @get:Schema(title = "操作用户", required = true)
    val operator: String
)
