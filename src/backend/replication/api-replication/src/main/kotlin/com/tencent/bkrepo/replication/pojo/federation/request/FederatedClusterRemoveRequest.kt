package com.tencent.bkrepo.replication.pojo.federation.request

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 联邦仓库删除指定cluster对应的联邦配置请求
 */
@Schema(title = "联邦仓库删除指定cluster对应的联邦配置请求")
data class FederatedClusterRemoveRequest(
    @get:Schema(title = "联邦仓库id")
    val federationId: String,
    @get:Schema(title = "项目")
    val projectId: String,
    @get:Schema(title = "仓库")
    val repoName: String,
    @get:Schema(title = "待删除联邦配置的项目")
    val federatedProjectId: String,
    @get:Schema(title = "待删除联邦配置的仓库")
    val federatedRepoName: String,
    @get:Schema(title = "待删除联邦配置的集群name")
    val federatedClusterName: String,
)