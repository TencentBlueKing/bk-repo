package com.tencent.bkrepo.replication.pojo.request

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 直接子节点信息（用于分层对比）
 */
@Schema(title = "直接子节点信息")
data class DirectChildInfo(
    @get:Schema(title = "完整路径")
    val fullPath: String,
    @get:Schema(title = "是否为目录")
    val folder: Boolean,
    @get:Schema(title = "文件大小（目录为0）")
    val size: Long = 0,
    @get:Schema(title = "文件sha256（目录为null）")
    val sha256: String? = null
)

/**
 * 直接子节点查询请求
 */
@Schema(title = "直接子节点查询请求")
data class DirectChildrenRequest(
    @get:Schema(title = "项目ID", required = true)
    val projectId: String,
    @get:Schema(title = "仓库名称", required = true)
    val repoName: String,
    @get:Schema(title = "父路径", example = "/")
    val parentPath: String = "/",
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

/**
 * 直接子节点分页响应
 */
@Schema(title = "直接子节点分页响应")
data class DirectChildrenPage(
    @get:Schema(title = "子节点列表")
    val records: List<DirectChildInfo>,
    @get:Schema(title = "当前页码")
    val pageNumber: Int,
    @get:Schema(title = "每页数量")
    val pageSize: Int,
    @get:Schema(title = "总记录数")
    val totalRecords: Long,
    @get:Schema(title = "是否还有更多数据")
    val hasMore: Boolean
)

/**
 * 路径文件数量统计请求
 */
@Schema(title = "路径文件数量统计请求")
data class PathCountRequest(
    @get:Schema(title = "项目ID", required = true)
    val projectId: String,
    @get:Schema(title = "仓库名称", required = true)
    val repoName: String,
    @get:Schema(title = "路径", example = "/")
    val path: String = "/"
)


