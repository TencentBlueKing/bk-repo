package com.tencent.bkrepo.fs.server.response.drive

import com.tencent.bkrepo.fs.server.model.drive.TDriveBlockNode
import java.time.LocalDateTime

data class DriveBlockNode(
    val id: String?,
    val createdBy: String,
    val createdDate: LocalDateTime,
    val projectId: String,
    val repoName: String,
    val ino: Long,
    val startPos: Long,
    val sha256: String,
    val crc64ecma: String?,
    val size: Long,
    val endPos: Long,
)

fun TDriveBlockNode.toDriveBlockNode(): DriveBlockNode {
    return DriveBlockNode(
        id = id,
        createdBy = createdBy,
        createdDate = createdDate,
        projectId = projectId,
        repoName = repoName,
        ino = ino,
        startPos = startPos,
        sha256 = sha256,
        crc64ecma = crc64ecma,
        size = size,
        endPos = endPos,
    )
}
