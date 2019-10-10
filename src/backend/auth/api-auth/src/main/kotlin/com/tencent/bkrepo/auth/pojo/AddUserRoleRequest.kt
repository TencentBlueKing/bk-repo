package com.tencent.bkrepo.auth.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("添加用户角色请求")
data class AddUserRoleRequest(
    @ApiModelProperty("用户名")
    val userName: String,
    @ApiModelProperty("权限ID")
    val roleId: String,
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("仓库ID")
    val repoId: String?
)