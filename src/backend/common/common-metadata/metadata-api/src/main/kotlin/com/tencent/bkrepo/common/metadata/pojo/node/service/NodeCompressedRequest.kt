package com.tencent.bkrepo.common.metadata.pojo.node.service

import com.tencent.bkrepo.common.metadata.pojo.ServiceRequest
import com.tencent.bkrepo.common.metadata.pojo.node.NodeRequest
import io.swagger.v3.oas.annotations.media.Schema


data class NodeCompressedRequest(
    @get:Schema(title = "所属项目", required = true)
    override val projectId: String,
    @get:Schema(title = "仓库名称", required = true)
    override val repoName: String,
    @get:Schema(title = "节点完整路径", required = true)
    override val fullPath: String,
    @get:Schema(title = "操作用户", required = true)
    override val operator: String,
) : NodeRequest, ServiceRequest
