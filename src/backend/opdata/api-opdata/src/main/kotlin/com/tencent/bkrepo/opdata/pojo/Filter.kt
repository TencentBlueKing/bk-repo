package com.tencent.bkrepo.opdata.pojo

import io.swagger.annotations.ApiModelProperty

data class Filter(
    @ApiModelProperty("key")
    val key: String,
    @ApiModelProperty("operator")
    val operator: String,
    @ApiModelProperty("value")
    val value: String
)
