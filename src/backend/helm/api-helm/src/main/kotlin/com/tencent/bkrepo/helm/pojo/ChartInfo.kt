package com.tencent.bkrepo.helm.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("chart info")
class ChartInfo(
    @ApiModelProperty("name")
    val name: String,
    @ApiModelProperty("version")
    val version: String,
    @ApiModelProperty("apiVersion")
    val apiVersion: String,
    @ApiModelProperty("创建时间")
    val created: String,
    @ApiModelProperty("digest")
    val digest: String,
    @ApiModelProperty("urls")
    val urls: List<String>
)
