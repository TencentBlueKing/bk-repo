package com.tencent.bkrepo.repository.model

import java.time.LocalDateTime
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 资源模型
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@Document("node")
data class TNode(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,
    var folder: Boolean,
    var path: String,
    var name: String,
    var fullPath: String,
    var repositoryId: String,
    var size: Long,
    var expired: Long,
    var sha256: String? = null,
    var deleted: LocalDateTime? = null

)
