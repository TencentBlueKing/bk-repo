package com.tencent.bkrepo.binary.model

import java.time.LocalDateTime

/**
 * 分块记录信息
 *
 * @author: carrypan
 * @date: 2019-09-30
 */
data class TBlockRecord(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    var sequence: Int,
    var size: Long,
    var sha256: String,
    var uploadId: String
)
