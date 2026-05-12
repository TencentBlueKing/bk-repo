package com.tencent.bkrepo.replication.pojo.federation

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 联邦仓库制品数量对比结果（最轻量级对比，只比较数量）
 */
@Schema(title = "联邦仓库制品数量对比结果")
data class FederationNodeCount(
    @get:Schema(title = "联邦ID")
    val federationId: String,
    @get:Schema(title = "本地项目ID")
    val localProjectId: String,
    @get:Schema(title = "本地仓库名称")
    val localRepoName: String,
    @get:Schema(title = "远程集群ID")
    val remoteClusterId: String,
    @get:Schema(title = "远程集群名称")
    val remoteClusterName: String,
    @get:Schema(title = "远程项目ID")
    val remoteProjectId: String,
    @get:Schema(title = "远程仓库名称")
    val remoteRepoName: String,
    @get:Schema(title = "本地节点总数")
    val localNodeCount: Long,
    @get:Schema(title = "远程节点总数")
    val remoteNodeCount: Long,
    @get:Schema(title = "数量差异（本地 - 远程）")
    val countDiff: Long = localNodeCount - remoteNodeCount,
    @get:Schema(title = "数量是否一致")
    val countMatch: Boolean = localNodeCount == remoteNodeCount,
    @get:Schema(title = "对比耗时（毫秒）")
    val durationMs: Long = 0
)


