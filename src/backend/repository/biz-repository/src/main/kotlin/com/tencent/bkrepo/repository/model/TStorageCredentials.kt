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
    var createdBy: String? = null,
    var createdDate: LocalDateTime? = null,
    var lastModifiedBy: String? = null,
    var lastModifiedDate: LocalDateTime? = null,

    var repositoryId: String? = null,
    var type: String? = null,
    var credentials: Any? = null
)
