package com.tencent.bkrepo.metadata.model

import java.time.LocalDateTime
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 元数据模型
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@Document("metadata")
data class TMetadata(
    val id: String,
    val createdBy: String,
    val createdDate: LocalDateTime,
    val lastModifiedBy: String,
    val lastModifiedDate: LocalDateTime,

    val key: String,
    val value: Any,
    val resourceId: String
)
