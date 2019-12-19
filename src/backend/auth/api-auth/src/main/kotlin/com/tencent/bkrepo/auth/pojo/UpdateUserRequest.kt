package com.tencent.bkrepo.auth.pojo

import io.swagger.annotations.ApiModelProperty

data class UpdateUserRequest(
    @ApiModelProperty("用户名")
    val name: String? = null,
    @ApiModelProperty("密码")
    val pwd: String ? = null,
    @ApiModelProperty("管理员")
    val admin: Boolean? = false
)
