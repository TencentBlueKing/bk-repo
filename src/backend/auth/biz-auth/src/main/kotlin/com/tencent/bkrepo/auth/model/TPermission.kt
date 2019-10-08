package com.tencent.bkrepo.auth.model

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 角色
 */
@Document("permission")
data class TPermission(
    val id: String? = null,

    val resourceType: ResourceType,
    val action: PermissionAction,
    val displayName: String
)
