package com.tencent.bkrepo.opdata.pojo

import io.swagger.annotations.ApiModelProperty

data class Columns(
    @ApiModelProperty("columns text")
    val text: String,
    @ApiModelProperty("columns type")
    val type: String
)
