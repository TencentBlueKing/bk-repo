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
    var createdBy: String? = null,
    var createdDate: LocalDateTime? = null,
    var lastModifiedBy: String? = null,
    var lastModifiedDate: LocalDateTime? = null,

    var folder: Boolean? = null,
    var path: String? = null,
    var name: String? = null,
    var fullPath: String? = null,
    var size: Long? = null,
    var sha256: String? = null,
    var deleted: LocalDateTime? = null,
    var repositoryId: String? = null
)
