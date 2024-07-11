package com.tencent.bkrepo.auth.pojo.permission

data class RepoModeStatus(
    val id: String,
    val status: Boolean = false,
    val controlEnable: Boolean =false,
    val officeDenyGroupSet: Set<String> = emptySet()
)
