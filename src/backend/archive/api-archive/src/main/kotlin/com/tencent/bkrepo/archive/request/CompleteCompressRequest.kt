package com.tencent.bkrepo.archive.request

import com.tencent.bkrepo.common.api.constant.SYSTEM_USER

data class CompleteCompressRequest(
    val sha256: String,
    val storageCredentialsKey: String?,
    val operator: String = SYSTEM_USER,
)
