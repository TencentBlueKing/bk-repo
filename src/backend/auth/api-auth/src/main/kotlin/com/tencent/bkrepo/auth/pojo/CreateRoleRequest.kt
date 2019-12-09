package com.tencent.bkrepo.auth.pojo

import com.tencent.bkrepo.auth.pojo.enums.RoleType
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("创建角色请求")
data class CreateRoleRequest(
    @ApiModelProperty("角色id")
    val rid: String,
    @ApiModelProperty("角色名称")
    val name: String,
    @ApiModelProperty("角色类型")
    val type: RoleType,
    @ApiModelProperty("显示名")
    val projectId: String
)