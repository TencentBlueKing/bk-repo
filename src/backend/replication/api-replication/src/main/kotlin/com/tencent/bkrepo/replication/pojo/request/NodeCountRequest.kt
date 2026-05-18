package com.tencent.bkrepo.replication.pojo.request

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 节点统计请求
 */
@Schema(title = "节点统计请求")
data class NodeCountRequest(
    @get:Schema(title = "项目ID", required = true)
    val projectId: String,
    @get:Schema(title = "仓库名称", required = true)
    val repoName: String,
    @get:Schema(title = "根路径", example = "/")
    val rootPath: String = "/"
)

/**
 * 节点统计结果
 */
@Schema(title = "节点统计结果")
data class NodeCountResult(
    @get:Schema(title = "文件节点总数")
    val fileCount: Long,
    @get:Schema(title = "所有 sha256 的集合哈希（用于快速判断是否一致）")
    val sha256SetHash: String? = null
)


