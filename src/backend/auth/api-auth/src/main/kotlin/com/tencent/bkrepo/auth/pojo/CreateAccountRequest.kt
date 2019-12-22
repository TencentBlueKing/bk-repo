package com.tencent.bkrepo.auth.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("创建账号请求")
data class CreateAccountRequest(
    @ApiModelProperty("系统Id")
    val appId: String,
    @ApiModelProperty("是否锁定")
    val locked: Boolean = false
)