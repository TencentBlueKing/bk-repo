package com.tencent.bkrepo.fs.server.request.drive

import java.time.LocalDateTime

data class DriveNodeMoveRequest(
    val projectId: String,
    val repoName: String,
    val ino: Long,
    val destParent: Long,
    val destName: String,
    val mtime: Long? = null,
    val ctime: Long? = null,
    val atime: Long? = null,
    val ifMatch: LocalDateTime? = null,
    val overwrite: Boolean = false,
)
