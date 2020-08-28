package com.tencent.bkrepo.repository.pojo.node

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 节点大小信息
 */
@ApiModel("节点大小信息")
data class NodeSizeInfo(
    @ApiModelProperty("子节点数量, 包含文件夹")
    val subNodeCount: Long = 0,
    @ApiModelProperty("文件大小总和")
    val size: Long
)
