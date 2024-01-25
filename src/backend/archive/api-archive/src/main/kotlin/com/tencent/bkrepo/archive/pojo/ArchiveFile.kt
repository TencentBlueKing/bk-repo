package com.tencent.bkrepo.archive.pojo

import com.tencent.bkrepo.archive.ArchiveStatus
import java.time.LocalDateTime

data class ArchiveFile(
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,
    val sha256: String,
    val size: Long,
    val storageCredentialsKey: String?,
    var status: ArchiveStatus,
)
