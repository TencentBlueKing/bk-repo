package com.tencent.bkrepo.auth.pojo.permission

import com.tencent.bkrepo.auth.pojo.enums.AccessControlMode

data class RepoModeStatus(
    val id: String,
    val accessControlMode: AccessControlMode?,
    val officeDenyGroupSet: Set<String> = emptySet()
)
