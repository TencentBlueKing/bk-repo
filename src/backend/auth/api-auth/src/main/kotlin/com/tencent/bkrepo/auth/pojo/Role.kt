package com.tencent.bkrepo.auth.pojo

import com.tencent.bkrepo.auth.pojo.enums.RoleType

/**
 * 角色
 */
data class Role(
    val id: String? = null,
    val roleType: RoleType,
    val name: String,
    val displayName: String
)
