package com.tencent.bkrepo.auth.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("元数据信息")
data class User(
    @ApiModelProperty("ID")
    val id: String,
    @ApiModelProperty("用户ID名")
    val name: String,
    @ApiModelProperty("用户名")
    val displayName: String,
    @ApiModelProperty("密码")
    var pwd: String,
    @ApiModelProperty("是否是管理员")
    val admin: Boolean,
    @ApiModelProperty("是否锁定")
    val locked: Boolean
)
