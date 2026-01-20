package com.tencent.bkrepo.replication.pojo.federation.request

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 联邦仓库差异统计请求
 */
@Schema(title = "联邦仓库差异统计请求")
data class FederationDiffStatsRequest(
    @get:Schema(title = "项目ID", required = true)
    val projectId: String,
    @get:Schema(title = "仓库名称", required = true)
    val repoName: String,
    @get:Schema(title = "联邦ID", required = true)
    val federationId: String,
    @get:Schema(title = "目标集群ID", required = true)
    val targetClusterId: String,
    @get:Schema(title = "起始路径", example = "/")
    val path: String = "/",
    @get:Schema(title = "查询深度（1-3，默认2）", example = "2")
    val depth: Int = 2
) {
    init {
        require(depth in 1..3) { "depth must be between 1 and 3" }
    }
}


