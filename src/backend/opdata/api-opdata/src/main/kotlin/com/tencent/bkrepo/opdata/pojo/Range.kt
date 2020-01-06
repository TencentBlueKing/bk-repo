package com.tencent.bkrepo.opdata.pojo

import io.swagger.annotations.ApiModelProperty

data class Range(
    @ApiModelProperty("from")
    val from: String,
    @ApiModelProperty("to")
    val to: String,
    @ApiModelProperty("raw")
    val raw: Raw
)
