package com.tencent.bkrepo.auth.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("创建用户请求")
data class CreateUserToProjectRequest(
    @ApiModelProperty("用户id")
    val userId: String,
    @ApiModelProperty("用户名")
    val name: String,
    @ApiModelProperty("密码")
    val pwd: String? = null,
    @ApiModelProperty("管理员")
    val admin: Boolean = false,
    @ApiModelProperty("关联用户")
    val asstUsers: List<String> = emptyList(),
    @ApiModelProperty("群组账号")
    val group: Boolean = false,
    @ApiModelProperty("项目ID")
    val projectId: String
)
