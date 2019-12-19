package com.tencent.bkrepo.auth.pojo

import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.LocalDateTime

@ApiModel("权限")
data class Permission(
    @ApiModelProperty("主键")
    val id: String? = null,
    @ApiModelProperty("资源类型")
    val resourceType: ResourceType,
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("权限名")
    val permName: String,
    @ApiModelProperty("关联仓库名")
    val repos: List<String> ? = emptyList(),
    @ApiModelProperty("匹配路径")
    val includePattern: List<String> ? = emptyList(),
    @ApiModelProperty("不匹配路径")
    val excludePattern: List<String> ? = emptyList(),
    @ApiModelProperty("绑定用户")
    val users: List<PermissionSet> ? = emptyList(),
    @ApiModelProperty("绑定角色")
    val roles: List<PermissionSet> ? = emptyList(),
    @ApiModelProperty("创建人")
    val createBy: String,
    @ApiModelProperty("修改人")
    val updatedBy: String,
    @ApiModelProperty("创建时间")
    val createAt: LocalDateTime,
    @ApiModelProperty("修改时间")
    val updateAt: LocalDateTime
)
