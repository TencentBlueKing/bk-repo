package com.tencent.bkrepo.fs.server.request.service

data class DriveNodeMoveRequest(
    val projectId: String,
    val repoName: String,
    val srcNodeId: String? = null,
    val srcParent: String? = null,
    val srcName: String? = null,
    val destParent: String,
    val destName: String,
    val overwrite: Boolean = false,
)
