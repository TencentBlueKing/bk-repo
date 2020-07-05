package com.tencent.bkrepo.pypi.util.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("pypi info")
data class PypiInfo(
    @ApiModelProperty("name")
    val name: String,
    @ApiModelProperty("version")
    val version: String,
    @ApiModelProperty("summary")
    val summary: String
)
