package com.tencent.bkrepo.auth.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("用户信息")
data class User(
    @ApiModelProperty("用户ID")
    val userId: String,
    @ApiModelProperty("用户名")
    val name: String,
    @ApiModelProperty("用户密码")
    var pwd: String? = null,
    @ApiModelProperty("是否是管理员")
    val admin: Boolean = false,
    @ApiModelProperty("是否锁定")
    val locked: Boolean = false,
    @ApiModelProperty("用户token")
    val tokens: List<Token> = emptyList(),
    @ApiModelProperty("所属角色")
    val roles: List<String> = emptyList()
)
