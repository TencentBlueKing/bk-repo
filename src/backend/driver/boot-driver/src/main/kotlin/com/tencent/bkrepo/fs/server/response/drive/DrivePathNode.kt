package com.tencent.bkrepo.fs.server.response.drive

import com.fasterxml.jackson.annotation.JsonFormat
import com.tencent.bkrepo.common.metadata.model.TMetadata
import java.time.LocalDateTime

/**
 * mongo存储的时间精度到小数点后3位
 */
private const val DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS"

/**
 * 带完整路径的 Drive 节点，用于按路径浏览目录。
 */
data class DrivePathNode(
    val id: String,
    val createdBy: String,
    @get:JsonFormat(pattern = DATE_TIME_FORMAT)
    val createdDate: LocalDateTime,
    val lastModifiedBy: String,
    val lastModifiedClientId: String? = null,
    @get:JsonFormat(pattern = DATE_TIME_FORMAT)
    val lastModifiedDate: LocalDateTime,
    val mtime: Long,
    val ctime: Long,
    val atime: Long,
    val projectId: String,
    val repoName: String,
    val ino: Long,
    val targetIno: Long? = null,
    val realIno: Long,
    val parent: Long? = null,
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
    @get:JsonFormat(pattern = DATE_TIME_FORMAT)
    val deleted: LocalDateTime? = null,
    val metadata: List<TMetadata>? = null,
    /**
     * 节点完整路径
     */
    val fullPath: String,
)

fun DriveNode.toDrivePathNode(fullPath: String): DrivePathNode {
    return DrivePathNode(
        id = id,
        createdBy = createdBy,
        createdDate = createdDate,
        lastModifiedBy = lastModifiedBy,
        lastModifiedClientId = lastModifiedClientId,
        lastModifiedDate = lastModifiedDate,
        mtime = mtime,
        ctime = ctime,
        atime = atime,
        projectId = projectId,
        repoName = repoName,
        ino = ino,
        targetIno = targetIno,
        realIno = realIno,
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
        deleted = deleted,
        metadata = metadata,
        fullPath = fullPath,
    )
}
