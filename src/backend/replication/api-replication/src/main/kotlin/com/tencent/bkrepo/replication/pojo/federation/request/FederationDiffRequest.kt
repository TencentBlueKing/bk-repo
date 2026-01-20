package com.tencent.bkrepo.replication.pojo.federation.request

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 联邦仓库制品差异摘要请求
 */
@Schema(title = "联邦仓库制品差异摘要请求")
data class FederationDiffRequest(
    @get:Schema(title = "本地项目ID", required = true)
    val projectId: String,
    @get:Schema(title = "本地仓库名称", required = true)
    val repoName: String,
    @get:Schema(title = "联邦ID", required = true)
    val federationId: String,
    @get:Schema(title = "目标集群ID（如果不指定，则对比所有联邦集群）")
    val targetClusterId: String? = null,
    @get:Schema(title = "对比的根路径，默认为/", example = "/")
    val rootPath: String = "/"
)

