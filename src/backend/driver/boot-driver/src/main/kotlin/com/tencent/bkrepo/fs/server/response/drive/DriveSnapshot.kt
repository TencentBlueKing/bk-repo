package com.tencent.bkrepo.fs.server.response.drive

import com.tencent.bkrepo.fs.server.model.drive.TDriveSnapshot
import java.time.LocalDateTime

data class DriveSnapshot(
    val id: String,
    val createdBy: String,
    val createdDate: LocalDateTime,
    val lastModifiedBy: String,
    val lastModifiedDate: LocalDateTime,
    val projectId: String,
    val repoName: String,
    val name: String,
    val description: String? = null,
    val snapSeq: Long,
)

fun TDriveSnapshot.toDriveSnapshot(): DriveSnapshot {
    return DriveSnapshot(
        id = requireNotNull(id),
        createdBy = createdBy,
        createdDate = createdDate,
        lastModifiedBy = lastModifiedBy,
        lastModifiedDate = lastModifiedDate,
        projectId = projectId,
        repoName = repoName,
        name = name,
        description = description,
        snapSeq = snapSeq,
    )
}
