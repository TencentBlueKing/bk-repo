package com.tencent.bkrepo.replication.pojo.federation

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 目录级别差异统计（用于分层对比，支持分页）
 */
@Schema(title = "目录级别差异统计")
data class FederationPathDiff(
    @get:Schema(title = "联邦ID")
    val federationId: String,
    @get:Schema(title = "远程集群ID")
    val remoteClusterId: String,
    @get:Schema(title = "远程集群名称")
    val remoteClusterName: String,
    @get:Schema(title = "当前对比路径")
    val path: String,
    @get:Schema(title = "本地该路径下文件总数（递归）")
    val localTotalCount: Long,
    @get:Schema(title = "远程该路径下文件总数（递归）")
    val remoteTotalCount: Long,
    @get:Schema(title = "是否一致（数量和内容都一致）")
    val consistent: Boolean,
    @get:Schema(title = "子节点差异列表（目录和文件）")
    val children: List<PathChildDiff>,
    @get:Schema(title = "当前页码")
    val pageNumber: Int = 1,
    @get:Schema(title = "每页数量")
    val pageSize: Int = 1000,
    @get:Schema(title = "子节点总数")
    val totalChildren: Long = 0,
    @get:Schema(title = "是否还有更多子节点")
    val hasMore: Boolean = false
)

/**
 * 子节点差异信息（目录或文件）
 */
@Schema(title = "子节点差异信息")
data class PathChildDiff(
    @get:Schema(title = "完整路径")
    val fullPath: String,
    @get:Schema(title = "是否为目录")
    val folder: Boolean,
    @get:Schema(title = "差异状态")
    val diffStatus: PathDiffStatus,
    @get:Schema(title = "本地文件数量（目录时为递归统计，文件时为1或0）")
    val localCount: Long = 0,
    @get:Schema(title = "远程文件数量（目录时为递归统计，文件时为1或0）")
    val remoteCount: Long = 0,
    @get:Schema(title = "文件大小（仅文件有效）")
    val size: Long? = null,
    @get:Schema(title = "本地sha256（仅文件有效）")
    val localSha256: String? = null,
    @get:Schema(title = "远程sha256（仅文件有效）")
    val remoteSha256: String? = null
)

/**
 * 路径差异状态
 */
enum class PathDiffStatus {
    /** 完全一致 */
    CONSISTENT,

    /** 只在本地存在 */
    LOCAL_ONLY,

    /** 只在远程存在 */
    REMOTE_ONLY,

    /** 数量不一致（仅目录） */
    COUNT_MISMATCH,

    /** 内容不一致（仅文件，sha256不同） */
    CONTENT_MISMATCH
}


