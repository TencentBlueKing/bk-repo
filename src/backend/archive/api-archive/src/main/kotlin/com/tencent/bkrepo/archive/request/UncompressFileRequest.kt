package com.tencent.bkrepo.archive.request

import com.tencent.bkrepo.common.metadata.constant.SYSTEM_USER

data class UncompressFileRequest(
    val sha256: String,
    val storageCredentialsKey: String?,
    val sync: Boolean = false,
    val operator: String = SYSTEM_USER,
)
