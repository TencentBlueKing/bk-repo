package com.tencent.bkrepo.opdata.pojo.service

import com.tencent.bkrepo.opdata.pojo.enums.NodeStatus
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("服务节点信息")
data class NodeInfo(
    @ApiModelProperty("节点id", required = true)
    val id: String,
    @ApiModelProperty("节点ip或域名", required = true)
    val host: String,
    @ApiModelProperty("节点端口", required = true)
    val port: String,
    @ApiModelProperty("节点状态", required = true)
    val status: NodeStatus,
    @ApiModelProperty("节点详情")
    val detail: NodeDetail? = null
)
