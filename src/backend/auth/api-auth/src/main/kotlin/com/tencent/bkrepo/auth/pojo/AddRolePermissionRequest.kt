package com.tencent.bkrepo.auth.pojo

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("添加角色权限请求")
data class AddRolePermissionRequest(
    @ApiModelProperty("角色ID")
    val roleId: String,
    @ApiModelProperty("权限ID")
    val permissionId: String
)