package com.tencent.bkrepo.fs.server.response.drive

import com.tencent.bkrepo.fs.server.model.drive.TDriveNode
import java.time.LocalDateTime

data class DriveNode(
    val id: String,
    val createdBy: String,
    val createdDate: LocalDateTime,
    val lastModifiedBy: String,
    val lastModifiedDate: LocalDateTime,
    val lastAccessDate: LocalDateTime,
    val projectId: String,
    val repoName: String,
    val ino: String,
    val parent: String? = null,
    val name: String,
    val size: Long,
    val mode: Int,
    val type: Int,
    val nlink: Int,
    val uid: Int,
    val gid: Int,
    val rdev: Int,
    val flags: Int,
    val symlinkTarget: String? = null,
)

fun TDriveNode.toDriveNode(): DriveNode {
    return DriveNode(
        id = id!!,
        createdBy = createdBy,
        createdDate = createdDate,
        lastModifiedBy = lastModifiedBy,
        lastModifiedDate = lastModifiedDate,
        lastAccessDate = lastAccessDate,
        projectId = projectId,
        repoName = repoName,
        ino = targetIno ?: ino,
        parent = parent,
        name = name,
        size = size,
        mode = mode,
        type = type,
        nlink = nlink,
        uid = uid,
        gid = gid,
        rdev = rdev,
        flags = flags,
        symlinkTarget = symlinkTarget,
    )
}
