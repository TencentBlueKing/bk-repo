package com.tencent.bkrepo.repository.pojo.blocknode

import java.time.LocalDateTime

data class BlockNodeDetail(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    val nodeFullPath: String,
    val startPos: Long,
    var sha256: String,
    var crc64ecma: String? = null,
    val projectId: String,
    val repoName: String,
    val size: Long,
    val endPos: Long,
    var deleted: LocalDateTime? = null,
    val uploadId: String? = null,
    var expireDate: LocalDateTime? = null,
)
