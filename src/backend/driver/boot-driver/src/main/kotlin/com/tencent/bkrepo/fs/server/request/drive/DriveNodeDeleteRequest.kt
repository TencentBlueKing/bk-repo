package com.tencent.bkrepo.fs.server.request.drive

import java.time.LocalDateTime

data class DriveNodeDeleteRequest(
    val projectId: String,
    val repoName: String,
    val nodeId: String,
    val lastModifiedDate: LocalDateTime? = null,
    val force: Boolean = false,
)
