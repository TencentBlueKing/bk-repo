package com.tencent.bkrepo.fs.server.request.drive

import com.tencent.bkrepo.auth.pojo.token.TokenType
import java.time.Duration

data class DriveTemporaryUrlCreateRequest(
    val projectId: String,
    val repoName: String,
    val fullPathSet: Set<String>,
    val authorizedUserSet: Set<String> = emptySet(),
    val authorizedIpSet: Set<String> = emptySet(),
    val expireSeconds: Long = Duration.ofDays(1).seconds,
    val permits: Int? = null,
    val type: TokenType,
    val host: String? = null,
    val snapSeq: Long? = null,
)
