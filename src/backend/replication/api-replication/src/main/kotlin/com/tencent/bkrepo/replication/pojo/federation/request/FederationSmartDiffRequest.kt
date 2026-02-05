package com.tencent.bkrepo.replication.pojo.federation.request

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 智能差异对比请求
 */
@Schema(description = "智能差异对比请求")
data class FederationSmartDiffRequest(
    @Schema(description = "项目ID", required = true)
    val projectId: String,
    
    @Schema(description = "仓库名称", required = true)
    val repoName: String,
    
    @Schema(description = "联邦ID", required = true)
    val federationId: String,
    
    @Schema(description = "目标集群ID", required = true)
    val targetClusterId: String,
    
    @Schema(description = "根路径", required = false, defaultValue = "/")
    val rootPath: String = "/",
    
    @Schema(description = "最大深度（1-3层）", required = false, defaultValue = "3")
    val maxDepth: Int = 3
)

