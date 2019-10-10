package com.tencent.bkrepo.auth.model

import com.tencent.bkrepo.auth.pojo.enums.RoleType
import java.time.LocalDateTime
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 角色
 */
@Document("role")
data class TRole(
    val id: String? = null,
    val roleType: RoleType,
    val name: String,
    val displayName: String
)
