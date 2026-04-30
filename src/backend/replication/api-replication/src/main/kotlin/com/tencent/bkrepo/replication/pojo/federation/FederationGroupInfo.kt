package com.tencent.bkrepo.replication.pojo.federation

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(title = "联邦集群组信息")
data class FederationGroupInfo(
    @get:Schema(title = "组ID")
    val id: String,
    @get:Schema(title = "组名称")
    val name: String,
    @get:Schema(title = "当前节点的 ClusterNode.id")
    val currentClusterId: String,
    @get:Schema(title = "参与联邦的所有 ClusterNode.id 列表")
    val clusterIds: List<String>,
    @get:Schema(title = "是否对新建仓库自动开启联邦同步")
    val autoEnableForNewRepo: Boolean,
    @get:Schema(title = "限定自动开启联邦的项目范围，null 表示全部项目")
    val projectScope: List<String>?,
    @get:Schema(title = "创建人")
    val createdBy: String,
    @get:Schema(title = "创建时间")
    val createdDate: LocalDateTime,
    @get:Schema(title = "最后修改人")
    val lastModifiedBy: String,
    @get:Schema(title = "最后修改时间")
    val lastModifiedDate: LocalDateTime,
)
