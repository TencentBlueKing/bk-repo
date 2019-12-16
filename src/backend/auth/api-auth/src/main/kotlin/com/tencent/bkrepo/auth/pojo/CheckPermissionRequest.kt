package com.tencent.bkrepo.auth.pojo

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("校验权限请求")
data class CheckPermissionRequest(
    @ApiModelProperty("类型")
    val uid: String,
    @ApiModelProperty("资源类型")
    val resourceType: ResourceType,
    @ApiModelProperty("Action")
    val action: PermissionAction,
    @ApiModelProperty("项目ID")
    val projectId: String? = null,
    @ApiModelProperty("仓库名称")
    val repoName: String? = null,
    @ApiModelProperty("路径")
    val path: String? = null,
    @ApiModelProperty("角色")
    val role: String? = null
)