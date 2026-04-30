package com.tencent.bkrepo.auth.pojo.role

data class RoleInfo(
    val id: String? = null,
    val roleId: String,
    val name: String,
    val type: String,
    val projectId: String? = null,
    val repoName: String? = null,
    val admin: Boolean = false,
    val users: List<String> = emptyList(),
    val description: String? = null,
)
