package com.tencent.bkrepo.generic.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty


@ApiModel("新分块信息")
data class SeparateBlockInfo(
    @ApiModelProperty("分块大小")
    val size: Long,
    @ApiModelProperty("分块sha256")
    val sha256: String,
    @ApiModelProperty("分块起始位置")
    val startPos: Long,
    @ApiModelProperty("分块版本")
    val version: String?
)