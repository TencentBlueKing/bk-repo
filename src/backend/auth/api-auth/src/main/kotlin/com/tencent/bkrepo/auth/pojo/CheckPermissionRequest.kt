package com.tencent.bkrepo.auth.pojo

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("校验权限请求")
data class CheckPermissionRequest(
    @ApiModelProperty("类型")
    val userId: String,
    @ApiModelProperty("资源类型")
    val resourceType: ResourceType,
    @ApiModelProperty("Action")
    val action: PermissionAction,
    @ApiModelProperty("项目")
    val project: String? = null,
    @ApiModelProperty("仓库")
    val repo: String? = null,
    @ApiModelProperty("node路径")
    val node: String? = null
)
