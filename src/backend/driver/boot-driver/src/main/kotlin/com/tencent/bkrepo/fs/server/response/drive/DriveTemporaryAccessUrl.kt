package com.tencent.bkrepo.fs.server.response.drive

data class DriveTemporaryAccessUrl(
    val projectId: String,
    val repoName: String,
    val fullPath: String,
    val url: String,
    val authorizedUserList: Set<String>,
    val authorizedIpList: Set<String>,
    val expireDate: String?,
    val permits: Int?,
    val type: String,
    val snapSeq: Long? = null,
)
