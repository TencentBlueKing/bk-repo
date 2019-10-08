package com.tencent.bkrepo.auth.pojo

import com.tencent.bkrepo.auth.pojo.enums.RoleType
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("创建用户")
data class CreateRoleRequest(
    @ApiModelProperty("名称")
    val name: String,
    @ApiModelProperty("角色类型")
    val roleType: RoleType,
    @ApiModelProperty("显示名")
    val displayName: String
)