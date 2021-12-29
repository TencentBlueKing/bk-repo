package com.tencent.bkrepo.opdata.pojo.service

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("服务信息")
data class ServiceInfo(
    @ApiModelProperty("服务名")
    val name: String,
    @ApiModelProperty("服务节点信息")
    val nodes: List<NodeInfo>
)
