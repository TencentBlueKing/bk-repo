package com.tencent.bkrepo.replication.pojo.request

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 路径统计请求（用于差异对比）
 */
@Schema(title = "路径统计请求")
data class PathStatsRequest(
    @get:Schema(title = "项目ID", required = true)
    val projectId: String,
    @get:Schema(title = "仓库名称", required = true)
    val repoName: String,
    @get:Schema(title = "起始路径", example = "/")
    val path: String = "/",
    @get:Schema(title = "查询深度（1-3）", example = "2")
    val depth: Int = 2
)

/**
 * 路径统计结果（树形结构）
 */
@Schema(title = "路径统计结果")
data class PathStatsResult(
    @get:Schema(title = "路径")
    val path: String,
    @get:Schema(title = "是否为目录")
    val folder: Boolean,
    @get:Schema(title = "文件数量")
    val fileCount: Long,
    @get:Schema(title = "总大小")
    val totalSize: Long,
    @get:Schema(title = "聚合哈希")
    val aggregateHash: String,
    @get:Schema(title = "sha256（仅文件有效）")
    val sha256: String? = null,
    @get:Schema(title = "子节点")
    val children: List<PathStatsResult>? = null
)


