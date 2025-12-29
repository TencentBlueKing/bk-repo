package com.tencent.bkrepo.replication.pojo.request

import io.swagger.v3.oas.annotations.media.Schema

data class BlockNodeCreateFinishRequest(
    @get:Schema(title = "所属项目", required = true)
    val projectId: String,
    @get:Schema(title = "仓库名称", required = true)
    val repoName: String,
    @get:Schema(title = "完整路径", required = true)
    val fullPath: String,
    @get:Schema(title = "上传id")
    val uploadId: String,
)
