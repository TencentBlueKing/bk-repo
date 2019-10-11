package com.tencent.bkrepo.auth.model

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 角色
 */
@Document("role_permission")
data class TRolePermission(
    val id: String? = null,
    val roleId: String,
    val permissionId: String
)
