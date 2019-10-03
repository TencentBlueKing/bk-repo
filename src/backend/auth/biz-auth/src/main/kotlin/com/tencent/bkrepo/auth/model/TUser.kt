package com.tencent.bkrepo.auth.model

import java.time.LocalDateTime
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 角色
 */
@Document("user")
data class TUser(
    val id: String? = null,
    val createdBy: String,
    val createdDate: LocalDateTime,
    val lastModifiedBy: String,
    val lastModifiedDate: LocalDateTime,

    val name: String,
    val displayName: String
)
