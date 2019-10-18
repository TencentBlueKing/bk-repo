package com.tencent.bkrepo.repository.pojo.node

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 节点大小信息
 *
 * @author: carrypan
 * @date: 2019-10-15
 */
@ApiModel("节点大小信息")
data class NodeSizeInfo(
    @ApiModelProperty("子节点数量")
    val subNodeCount: Long = 0,
    @ApiModelProperty("大小")
    val size: Long
)
