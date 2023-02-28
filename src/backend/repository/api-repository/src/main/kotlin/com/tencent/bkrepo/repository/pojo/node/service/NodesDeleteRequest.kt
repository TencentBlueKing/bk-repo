package com.tencent.bkrepo.repository.pojo.node.service

import com.tencent.bkrepo.repository.pojo.ServiceRequest
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 节点批量删除请求
 */
@ApiModel("节点批量删除请求")
class NodesDeleteRequest(
	@ApiModelProperty("所属项目", required = true)
	val projectId: String,
	@ApiModelProperty("仓库名称", required = true)
	val repoName: String,
	@ApiModelProperty("节点完整路径列表", required = true)
	val fullPaths: List<String>,
	@ApiModelProperty("操作用户", required = true)
	override val operator: String
) : ServiceRequest
