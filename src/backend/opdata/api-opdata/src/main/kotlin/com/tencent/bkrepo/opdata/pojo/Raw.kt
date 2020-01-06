package com.tencent.bkrepo.opdata.pojo

import io.swagger.annotations.ApiModelProperty

data class Raw(
    @ApiModelProperty("from")
    val from: String,
    @ApiModelProperty("to")
    val to: String
)
