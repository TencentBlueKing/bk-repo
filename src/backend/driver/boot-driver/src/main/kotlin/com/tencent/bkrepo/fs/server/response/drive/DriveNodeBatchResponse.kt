package com.tencent.bkrepo.fs.server.response.drive

import com.tencent.bkrepo.fs.server.request.drive.DriveNodeBatchOp

typealias DriveNodeBatchResponse = List<DriveNodeBatchResult>

data class DriveNodeBatchResult(
    val op: DriveNodeBatchOp,
    val ino: Long? = null,
    val nodeId: String? = null,
    /**
     * 仅非删除操作存在该字段
     */
    val node: DriveNode? = null,
    val code: Int,
    val message: String? = null,
)
