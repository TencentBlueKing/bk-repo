package com.tencent.bkrepo.common.metadata.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 短链接实体
 */
@Document("short_link")
@CompoundIndexes(
    CompoundIndex(
        name = "createdBy_createdDate_idx",
        def = "{'createdBy': 1, 'createdDate': -1}",
        background = true,
    ),
)
data class TShortLink(
    var id: String? = null,
    @Indexed(unique = true, background = true)
    val code: String,
    val target: String,
    @Indexed(expireAfterSeconds = 0, background = true)
    val expiredDate: LocalDateTime,
    val createdBy: String,
    val createdDate: LocalDateTime,
    val lastModifiedBy: String,
    val lastModifiedDate: LocalDateTime,
)
