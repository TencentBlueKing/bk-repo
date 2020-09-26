package com.tencent.bkrepo.auth.pojo

data class PermissionUrlRequest(
    val system: String,
    val action: List<Action>
)