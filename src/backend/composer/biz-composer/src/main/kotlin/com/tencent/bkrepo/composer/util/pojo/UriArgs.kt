package com.tencent.bkrepo.composer.util.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("composer 构件")
data class UriArgs(
    @ApiModelProperty("构件名")
    val filename: String,
    @ApiModelProperty("构件版本")
    val version: String,
    @ApiModelProperty("构件打包格式")
    val format: String
)
