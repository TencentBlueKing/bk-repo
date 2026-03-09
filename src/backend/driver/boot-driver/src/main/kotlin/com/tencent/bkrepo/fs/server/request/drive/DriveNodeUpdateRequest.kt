package com.tencent.bkrepo.fs.server.request.drive

import com.tencent.bkrepo.common.artifact.path.PathUtils
import java.time.LocalDateTime

data class DriveNodeUpdateRequest(
    val projectId: String,
    val repoName: String,
    val nodeId: String,
    val parent: String? = null,
    val name: String? = null,
    val size: Long? = null,
    val mode: Int? = null,
    val nlink: Int? = null,
    val uid: Int? = null,
    val gid: Int? = null,
    val rdev: Int? = null,
    val flags: Int? = null,
    val symlinkTarget: String? = null,
    val lastModifiedDate: LocalDateTime? = null,
    val force: Boolean = false,
)

fun DriveNodeUpdateRequest.normalizedSymlinkTarget(): String? {
    return symlinkTarget?.let { PathUtils.normalizeFullPath(it) }
}
