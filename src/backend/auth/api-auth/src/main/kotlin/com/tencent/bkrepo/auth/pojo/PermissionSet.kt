package com.tencent.bkrepo.auth.pojo

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction

data class PermissionSet(
        val id: String,
        val action: List<PermissionAction>
)