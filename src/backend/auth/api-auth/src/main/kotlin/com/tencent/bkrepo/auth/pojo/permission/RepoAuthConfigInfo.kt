package com.tencent.bkrepo.auth.pojo.permission

import com.tencent.bkrepo.auth.pojo.enums.AccessControlMode

data class RepoAuthConfigInfo(
    val id: String,
    val projectId: String,
    val repoName: String,
    val accessControlMode: AccessControlMode?,
    val officeDenyGroupSet: Set<String> = emptySet(),
    val bkiamv3Check: Boolean = false
)
