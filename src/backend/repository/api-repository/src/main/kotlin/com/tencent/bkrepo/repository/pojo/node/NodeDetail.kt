package com.tencent.bkrepo.repository.pojo.node

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 节点详细信息
 * @author: carrypan
 * @date: 2019-10-13
 */
@ApiModel("节点详细信息")
data class NodeDetail(
    @ApiModelProperty("节点基本信息")
    val nodeInfo: NodeInfo,
    @ApiModelProperty("元数据")
    val metadata: Map<String, String>
)
