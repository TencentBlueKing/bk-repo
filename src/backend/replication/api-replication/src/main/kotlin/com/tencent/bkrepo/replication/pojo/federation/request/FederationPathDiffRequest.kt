package com.tencent.bkrepo.replication.pojo.federation.request

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 分层目录差异对比请求
 */
@Schema(title = "分层目录差异对比请求")
data class FederationPathDiffRequest(
    @get:Schema(title = "项目ID", required = true)
    val projectId: String,
    @get:Schema(title = "仓库名称", required = true)
    val repoName: String,
    @get:Schema(title = "联邦ID", required = true)
    val federationId: String,
    @get:Schema(title = "目标集群ID", required = true)
    val targetClusterId: String,
    @get:Schema(title = "要对比的路径（默认根目录）", example = "/")
    val path: String = "/",
    @get:Schema(title = "是否只返回有差异的子节点", example = "false")
    val onlyDiff: Boolean = false,
    @get:Schema(title = "页码（从1开始）", example = "1")
    val pageNumber: Int = 1,
    @get:Schema(title = "每页数量（默认1000，最大5000）", example = "1000")
    val pageSize: Int = DEFAULT_PAGE_SIZE
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 1000
        const val MAX_PAGE_SIZE = 5000
    }
}


