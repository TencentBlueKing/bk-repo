package com.tencent.bkrepo.opdata.pojo.config

import io.swagger.annotations.ApiModelProperty

data class GetConfigRequest(
    @ApiModelProperty("查询的应用名")
    val appName: String = "",
    @ApiModelProperty("查询的profile")
    val profile: String = "",
    @ApiModelProperty("查询的key")
    val key:String
)
