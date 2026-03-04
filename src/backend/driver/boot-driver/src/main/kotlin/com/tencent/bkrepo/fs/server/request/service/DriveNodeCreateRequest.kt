package com.tencent.bkrepo.fs.server.request.service

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.bson.types.ObjectId
import java.time.LocalDateTime

data class DriveNodeCreateRequest(
    val projectId: String,
    val repoName: String,
    val parent: String,
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

fun DriveNodeCreateRequest.toDriveNode(
    snapSeq: Long,
    operator: String = SYSTEM_USER,
    now: LocalDateTime = LocalDateTime.now(),
): TDriveNode {
    return TDriveNode(
        createdBy = operator,
        createdDate = now,
        lastModifiedBy = operator,
        lastModifiedDate = now,
        lastAccessDate = now,
        projectId = projectId,
        repoName = repoName,
        ino = ObjectId.get().toHexString(),
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
        symlinkTarget = symlinkTarget?.let { PathUtils.normalizeFullPath(it) },
        snapSeq = snapSeq,
    )
}
