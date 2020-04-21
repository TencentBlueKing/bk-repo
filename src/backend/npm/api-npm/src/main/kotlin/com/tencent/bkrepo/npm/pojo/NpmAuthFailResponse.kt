package com.tencent.bkrepo.npm.pojo

import io.swagger.annotations.Api
import io.swagger.annotations.ApiModelProperty

@Api("npm auth 返回包装模型")
data class NpmAuthFailResponse(
    @ApiModelProperty("错误信息")
    val errors: List<AuthFailInfo>
)

data class AuthFailInfo(
    val status: Int,
    val message: String
)
