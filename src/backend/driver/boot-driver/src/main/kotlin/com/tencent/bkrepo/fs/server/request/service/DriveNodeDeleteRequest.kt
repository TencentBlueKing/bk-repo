package com.tencent.bkrepo.fs.server.request.service

data class DriveNodeDeleteRequest(
    val projectId: String,
    val repoName: String,
    val nodeId: String? = null,
    val parent: String? = null,
    val name: String? = null,
)
