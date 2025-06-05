package com.tencent.bkrepo.archive.request

import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.common.api.constant.SYSTEM_USER

data class UpdateCompressFileStatusRequest(
    val sha256: String,
    val storageCredentialsKey: String?,
    val status: CompressStatus,
    val operator: String = SYSTEM_USER,
)
