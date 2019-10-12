package com.tencent.bkrepo.repository.model

import java.time.LocalDateTime
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 仓库模型
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@Document("storage_credentials")
data class TStorageCredentials(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    var repositoryId: String,
    var type: String,
    var credentials: String
)
