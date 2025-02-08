package com.tencent.bkrepo.archive.pojo

import com.tencent.bkrepo.archive.CompressStatus
import java.time.LocalDateTime

data class CompressFile(
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,
    val sha256: String,
    val baseSha256: String,
    val uncompressedSize: Long,
    var compressedSize: Long = -1,
    val storageCredentialsKey: String?,
    var status: CompressStatus,
)
