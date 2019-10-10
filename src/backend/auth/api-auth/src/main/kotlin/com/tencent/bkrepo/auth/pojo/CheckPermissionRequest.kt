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
    @ApiModelProperty("项目ID")
    val projectId: String?,
    @ApiModelProperty("仓库ID")
    val repoId: String?,
    @ApiModelProperty("node路径")
    val node: String?,
    @ApiModelProperty("Action")
    val action: PermissionAction
)
