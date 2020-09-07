package com.tencent.bkrepo.repository.pojo.stage

import com.tencent.bkrepo.repository.pojo.ServiceRequest
import com.tencent.bkrepo.repository.pojo.node.NodeRequest
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("制品晋级请求")
data class StageUpgradeRequest(
    @ApiModelProperty("所属项目", required = true)
    override val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    override val repoName: String,
    @ApiModelProperty("节点完整路径", required = true)
    override val fullPath: String,
    @ApiModelProperty("新的tag", required = true)
    val newTag: String ?= null,
    @ApiModelProperty("操作用户", required = true)
    override val operator: String
) : NodeRequest, ServiceRequest