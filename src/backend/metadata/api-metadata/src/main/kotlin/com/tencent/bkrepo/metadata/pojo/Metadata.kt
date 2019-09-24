package com.tencent.bkrepo.metadata.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 元数据信息
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@ApiModel("元数据信息")
data class Metadata(
    @ApiModelProperty("元数据id")
    val id: String,
    @ApiModelProperty("元数据key")
    val key: String,
    @ApiModelProperty("元数据value")
    val value: Any,
    @ApiModelProperty("元数据所属节点id")
    val nodeId: String
)
