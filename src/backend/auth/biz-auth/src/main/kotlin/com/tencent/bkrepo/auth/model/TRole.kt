package com.tencent.bkrepo.auth.model

import com.tencent.bkrepo.auth.pojo.enums.RoleType
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 角色
 */
@Document("role")
data class TRole(
        val id: String? = null,
        val rId: String? = null,
        val type: RoleType,
        val name: String,
        val projectId: String
)
