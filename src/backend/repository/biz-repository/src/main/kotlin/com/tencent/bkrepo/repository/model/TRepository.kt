package com.tencent.bkrepo.repository.model

import java.time.LocalDateTime
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 仓库模型
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@Document("repository")
data class TRepository(
    val id: String,
    val createdBy: String,
    val createdDate: LocalDateTime,
    val lastModifiedBy: String,
    val lastModifiedDate: LocalDateTime,

    val name: String,
    val type: String,
    val category: String,
    val public: Boolean,
    val description: String,
    val extension: Any,
    val projectId: String
)
