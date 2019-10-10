package com.tencent.bkrepo.auth.pojo

import com.tencent.bkrepo.auth.pojo.enums.RoleType
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("角色")
data class Role(
    @ApiModelProperty("ID")
    val id: String? = null,
    @ApiModelProperty("角色类型")
    val roleType: RoleType,
    @ApiModelProperty("角色名")
    val name: String,
    @ApiModelProperty("显示名")
    val displayName: String
)
