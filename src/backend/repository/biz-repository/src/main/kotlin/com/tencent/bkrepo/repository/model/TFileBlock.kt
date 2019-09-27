package com.tencent.bkrepo.repository.model

import java.time.LocalDateTime
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 文件分块信息
 *
 * @author: carrypan
 * @date: 2019-09-27
 */
@Document("file_block")
data class TFileBlock(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    var index: Int,
    var size: Long,
    var sha256: String,
    var nodeId: String
)
