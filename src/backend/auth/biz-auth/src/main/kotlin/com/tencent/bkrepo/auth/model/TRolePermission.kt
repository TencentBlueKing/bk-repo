package com.tencent.bkrepo.auth.model

import org.springframework.data.mongodb.core.mapping.Document

/**
 * 角色
 */
@Document("role")
data class TRolePermission(
    val id: String? = null,
    val roleId: String,
    val roleType: String,
    val displayName: String
)
