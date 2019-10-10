package com.tencent.bkrepo.auth.pojo

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("创建权限")
data class CreatePermissionRequest(
    @ApiModelProperty("权限资源类型")
    val resourceType: ResourceType,
    @ApiModelProperty("Action")
    val action: PermissionAction,
    @ApiModelProperty("显示名")
    val displayName: String
)