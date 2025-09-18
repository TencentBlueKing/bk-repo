package com.tencent.bkrepo.replication.pojo.federation.request

import com.tencent.bkrepo.replication.pojo.federation.FederatedCluster
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 联邦仓库更新请求
 */
@Schema(title = "联邦仓库更新请求")
data class FederatedRepositoryUpdateRequest(
    @get:Schema(title = "联邦仓库id")
    val federationId: String,
    @get:Schema(title = "项目")
    val projectId: String,
    @get:Schema(title = "仓库")
    val repoName: String,
    @get:Schema(title = "联邦仓库集群列表", description = "可选，不传则不更新集群配置")
    val federatedClusters: List<FederatedCluster>? = null,
)