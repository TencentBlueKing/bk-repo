package com.tencent.bkrepo.auth.pojo

import com.tencent.bkrepo.auth.pojo.enums.RoleType
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("角色")
data class Role(
    val id: String? = null,
    @ApiModelProperty("角色ID")
    val roleId: String? = null,
    @ApiModelProperty("角色类型")
    val type: RoleType,
    @ApiModelProperty("角色名")
    val name: String,
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("仓库名称")
    val repoName: String?=null,
    @ApiModelProperty("管理员")
    val admin: Boolean? = false
)
