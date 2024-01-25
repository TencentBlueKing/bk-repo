package com.tencent.bkrepo.archive.request

import com.tencent.bkrepo.repository.constant.SYSTEM_USER

data class ArchiveFileRequest(
    val sha256: String,
    val storageCredentialsKey: String?,
    val operator: String = SYSTEM_USER,
)
