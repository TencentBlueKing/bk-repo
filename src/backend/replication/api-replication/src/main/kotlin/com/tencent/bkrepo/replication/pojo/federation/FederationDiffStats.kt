package com.tencent.bkrepo.replication.pojo.federation

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 联邦仓库差异统计结果（多层目录聚合）
 */
@Schema(title = "联邦仓库差异统计结果")
data class FederationDiffStats(
    @get:Schema(title = "联邦ID")
    val federationId: String,
    @get:Schema(title = "远程集群ID")
    val remoteClusterId: String,
    @get:Schema(title = "远程集群名称")
    val remoteClusterName: String,
    @get:Schema(title = "根路径统计")
    val root: PathDiffStats,
    @get:Schema(title = "查询深度")
    val depth: Int,
    @get:Schema(title = "对比耗时（毫秒）")
    val durationMs: Long = 0
)

/**
 * 路径差异统计（包含子目录递归）
 */
@Schema(title = "路径差异统计")
data class PathDiffStats(
    @get:Schema(title = "路径")
    val path: String,
    @get:Schema(title = "是否为目录")
    val folder: Boolean,
    @get:Schema(title = "本地统计")
    val localStats: PathStatistics?,
    @get:Schema(title = "远程统计")
    val remoteStats: PathStatistics?,
    @get:Schema(title = "差异状态")
    val diffStatus: PathDiffStatus,
    @get:Schema(title = "子节点差异（仅目录有效，受 depth 参数控制）")
    val children: List<PathDiffStats>? = null
)

/**
 * 路径统计信息
 */
@Schema(title = "路径统计信息")
data class PathStatistics(
    @get:Schema(title = "文件数量（递归统计）")
    val fileCount: Long,
    @get:Schema(title = "总大小（字节）")
    val totalSize: Long,
    @get:Schema(title = "聚合哈希（所有文件sha256排序后的MD5）")
    val aggregateHash: String
)

