package com.tencent.bkrepo.auth.pojo

import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import io.swagger.annotations.ApiModel
import java.time.LocalDateTime

@ApiModel("权限")
data class Permission(
    val id: String? = null,
    val resourceType: ResourceType,
    val projectId: String? = null,
    val permName: String,
    val repos: List<String> = emptyList(),
    val includePattern: List<String> = emptyList(),
    val excludePattern: List<String> = emptyList(),
    val users: List<PermissionSet> = emptyList(),
    val roles: List<PermissionSet> = emptyList(),
    val createBy: String,
    val updatedBy: String,
    val createAt: LocalDateTime,
    val updateAt: LocalDateTime
)
