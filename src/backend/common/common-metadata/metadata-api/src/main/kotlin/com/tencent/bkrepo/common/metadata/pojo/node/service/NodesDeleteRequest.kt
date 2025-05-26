package com.tencent.bkrepo.common.metadata.pojo.node.service

import com.tencent.bkrepo.common.metadata.pojo.ServiceRequest
import io.swagger.v3.oas.annotations.media.Schema


/**
 * 节点批量删除请求
 */
@Schema(title = "节点批量删除请求")
class NodesDeleteRequest(
    @get:Schema(title = "所属项目", required = true)
    val projectId: String,
    @get:Schema(title = "仓库名称", required = true)
    val repoName: String,
    @get:Schema(title = "节点完整路径列表", required = true)
    val fullPaths: List<String>,
    @get:Schema(title = "操作用户", required = true)
    override val operator: String,
    @get:Schema(title = "是否为文件夹", required = false)
    val isFolder: Boolean? = false,
) : ServiceRequest
