package com.tencent.bkrepo.fs.server.request.drive

data class DriveSnapshotCreateRequest(
    val name: String,
    val description: String? = null,
)
