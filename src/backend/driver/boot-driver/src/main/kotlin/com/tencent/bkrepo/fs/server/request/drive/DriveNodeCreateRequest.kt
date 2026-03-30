package com.tencent.bkrepo.fs.server.request.drive

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode
import com.tencent.bkrepo.fs.server.utils.DriveServiceUtils.toNanoTimestamp
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import java.time.LocalDateTime

data class DriveNodeCreateRequest(
    override val projectId: String,
    override val repoName: String,
    override val parent: Long,
    override val name: String,
    val ino: Long,
    val targetIno: Long?,
    override val size: Long,
    override val mode: Int,
    val type: Int,
    override val nlink: Int,
    override val uid: Int,
    override val gid: Int,
    override val rdev: Int,
    override val flags: Int,
    override val symlinkTarget: String? = null,
    val mtime: Long? = null,
    val ctime: Long? = null,
    val atime: Long? = null,
) : DriveNodeBaseRequest(
    projectId = projectId,
    repoName = repoName,
    parent = parent,
    name = name,
    size = size,
    mode = mode,
    nlink = nlink,
    uid = uid,
    gid = gid,
    rdev = rdev,
    flags = flags,
    symlinkTarget = symlinkTarget,
)

fun DriveNodeCreateRequest.toDriveNode(
    snapSeq: Long,
    operator: String = SYSTEM_USER,
    clientId: String? = null,
    now: LocalDateTime = LocalDateTime.now(),
): TDriveNode {
    return TDriveNode(
        createdBy = operator,
        createdDate = now,
        lastModifiedBy = operator,
        lastModifiedClientId = clientId,
        lastModifiedDate = now,
        mtime = mtime ?: toNanoTimestamp(now),
        ctime = ctime ?: toNanoTimestamp(now),
        atime = atime ?: toNanoTimestamp(now),
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
