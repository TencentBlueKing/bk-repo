package com.tencent.bkrepo.archive.request

import com.tencent.bkrepo.archive.constant.ArchiveStorageClass
import com.tencent.bkrepo.repository.constant.SYSTEM_USER

data class CreateArchiveFileRequest(
    val sha256: String,
    val size: Long,
    val storageCredentialsKey: String?,
    val archiveCredentialsKey: String?,
    val storageClass: ArchiveStorageClass,
    val operator: String = SYSTEM_USER,
)
