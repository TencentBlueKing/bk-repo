package com.tencent.bkrepo.fs.server.request.drive

data class DriveNodeMoveRequest(
    val projectId: String,
    val repoName: String,
    val srcNodeId: String? = null,
    val srcParent: Long? = null,
    val srcName: String? = null,
    val destParent: Long,
    val destName: String,
    val overwrite: Boolean = false,
)
