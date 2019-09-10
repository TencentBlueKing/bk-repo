package com.tencent.bkrepo.repository.model

import java.time.LocalDateTime
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 资源模型
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@Document("resource")
data class TResource(
    val id: String,
    val createdBy: String,
    val createdDate: LocalDateTime,
    val lastModifiedBy: String,
    val lastModifiedDate: LocalDateTime,

    val folder: Boolean,
    val path: String,
    val name: String,
    val fullPath: Boolean,
    val size: Long,
    val sha256: String,
    val deleted: LocalDateTime,
    val repositoryId: String
)
