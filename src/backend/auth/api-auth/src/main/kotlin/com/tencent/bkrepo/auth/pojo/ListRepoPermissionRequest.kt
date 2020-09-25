package com.tencent.bkrepo.auth.pojo

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("查询用户在项目下对仓库的权限")
data class ListRepoPermissionRequest (
    @ApiModelProperty("用户ID")
    val uid: String,
    @ApiModelProperty("资源类型")
    val resourceType: ResourceType,
    @ApiModelProperty("操作类型")
    val action: PermissionAction,
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("仓库名称列表")
    val repoNames: List<String> = emptyList(),
    @ApiModelProperty("路径")
    val path: String? = null,
    @ApiModelProperty("角色")
    val role: String? = null,
    @ApiModelProperty("AppId")
    val appId: String? = null
)