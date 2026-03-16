package com.tencent.bkrepo.fs.server.request.drive

import com.tencent.bkrepo.common.artifact.path.PathUtils
import java.time.LocalDateTime

data class DriveNodeUpdateRequest(
    override val projectId: String,
    override val repoName: String,
    val nodeId: String,
    override val parent: Long? = null,
    override val name: String? = null,
    override val size: Long? = null,
    override val mode: Int? = null,
    override val nlink: Int? = null,
    override val uid: Int? = null,
    override val gid: Int? = null,
    override val rdev: Int? = null,
    override val flags: Int? = null,
    override val symlinkTarget: String? = null,
    val mtime: Long? = null,
    val ctime: Long? = null,
    val atime: Long? = null,
    val lastModifiedDate: LocalDateTime? = null,
    val force: Boolean = false,
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

fun DriveNodeUpdateRequest.normalizedSymlinkTarget(): String? {
    return symlinkTarget?.let { PathUtils.normalizeFullPath(it) }
}
