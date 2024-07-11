package com.tencent.bkrepo.auth.pojo.permission

data class RepoModeStatus(
    val id: String,
    val status: Boolean,
    val controlEnable: Boolean,
    val officeDenyGroupSet: Set<String>
)
