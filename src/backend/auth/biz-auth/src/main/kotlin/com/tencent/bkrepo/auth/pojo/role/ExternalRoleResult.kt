package com.tencent.bkrepo.auth.pojo.role

data class ExternalRoleResult(
    val name: String,
    val roleId: String,
    val userList: List<String>
)
