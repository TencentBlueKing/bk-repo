package com.tencent.bkrepo.auth.model

import java.time.LocalDateTime
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 角色
 */
@Document("user_role")
data class TUserRole(
    val id: String? = null,

    val userId: String,
    val roleId: String,

    val projectId: String,
    val repoId: String?
)
