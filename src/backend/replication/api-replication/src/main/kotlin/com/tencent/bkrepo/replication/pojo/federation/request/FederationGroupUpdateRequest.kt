package com.tencent.bkrepo.replication.pojo.federation.request

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "联邦集群组更新请求")
data class FederationGroupUpdateRequest(
    @get:Schema(title = "组ID")
    val id: String,
    @get:Schema(title = "当前节点的 ClusterNode.id")
    val currentClusterId: String? = null,
    @get:Schema(title = "参与联邦的所有 ClusterNode.id 列表（含当前节点）")
    val clusterIds: List<String>? = null,
    @get:Schema(title = "是否对新建仓库自动开启联邦同步")
    val autoEnableForNewRepo: Boolean? = null,
    @get:Schema(title = "限定自动开启联邦的项目范围，null 表示全部项目")
    val projectScope: List<String>? = null,
)
