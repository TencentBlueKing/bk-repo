package com.tencent.bkrepo.repository.model

import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("storage_credentials")
data class TStorageCredentials(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    var credentials: String
)
