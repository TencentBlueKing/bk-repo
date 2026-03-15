package com.tencent.bkrepo.fs.server.request.drive

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import java.time.LocalDateTime
import java.time.ZoneOffset

data class DriveNodeCreateRequest(
    val projectId: String,
    val repoName: String,
    val parent: Long,
    val name: String,
    val ino: Long,
    val targetIno: Long?,
    val size: Long,
    val mode: Int,
    val type: Int,
    val nlink: Int,
    val uid: Int,
    val gid: Int,
    val rdev: Int,
    val flags: Int,
    val symlinkTarget: String? = null,
    val mtime: Long? = null,
    val ctime: Long? = null,
    val atime: Long? = null,
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
        mtime = mtime ?: nowNanos(now),
        ctime = ctime ?: nowNanos(now),
        atime = atime ?: nowNanos(now),
        projectId = projectId,
        repoName = repoName,
        ino = ino,
        targetIno = targetIno,
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

private fun nowNanos(now: LocalDateTime): Long {
    return now.toInstant(ZoneOffset.UTC).let { it.epochSecond * 1_000_000_000L + it.nano }
}
