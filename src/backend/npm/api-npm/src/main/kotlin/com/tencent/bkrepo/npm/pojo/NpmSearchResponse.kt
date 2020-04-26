package com.tencent.bkrepo.npm.pojo

import io.swagger.annotations.Api
import io.swagger.annotations.ApiModelProperty

@Api("npm search 返回格式封装")
data class NpmSearchResponse(
    @ApiModelProperty("对应数据")
    val objects: MutableList<Map<String, Any>> = mutableListOf()
)
