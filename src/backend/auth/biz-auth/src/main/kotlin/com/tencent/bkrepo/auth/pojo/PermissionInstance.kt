package com.tencent.bkrepo.auth.pojo

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType

data class PermissionInstance(
    val resourceType: ResourceType,
    val action: PermissionAction,
    val projectId: String,
    val repoId: String?,
    val node: String?
)
