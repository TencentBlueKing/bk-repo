package com.tencent.bkrepo.archive.request

import com.tencent.bkrepo.repository.constant.SYSTEM_USER

data class CompressFileRequest(
    val sha256: String,
    val size: Long,
    val baseSha256: String,
    val baseSize: Long,
    val storageCredentialsKey: String?,
    val sync: Boolean = false,
    val operator: String = SYSTEM_USER,
)
