package com.tencent.bkrepo.repository.model

import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
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
    @Id
    var id: String? = null,
    @CreatedBy
    var createdBy: String? = null,
    @CreatedDate
    var createdDate: LocalDateTime? = null,
    @LastModifiedBy
    var lastModifiedBy: String? = null,
    @LastModifiedDate
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
