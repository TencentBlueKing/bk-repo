package com.tencent.bkrepo.auth.model

import org.springframework.data.mongodb.core.mapping.Document

/**
 * 角色
 */
@Document("role")
data class TPermission(
    val id: String? = null,

    val resourceType: String,
    val action: String,
    val displayName: String
)
