package com.tencent.bkrepo.fs.server.response.drive

typealias DriveNodeBatchResponse = List<DriveNodeBatchResult>

data class DriveNodeBatchResult(
    val ino: Long? = null,
    val nodeId: String? = null,
    val code: Int,
    val message: String? = null,
)
