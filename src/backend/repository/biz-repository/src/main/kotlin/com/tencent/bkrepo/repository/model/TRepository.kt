package com.tencent.bkrepo.repository.model

import com.tencent.bkrepo.repository.constant.enum.RepositoryCategoryEnum
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
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

    var name: String? = null,
    var type: String? = null,
    var category: RepositoryCategoryEnum? = null,
    var public: Boolean? = null,
    var description: String? = null,
    var extension: Any? = null,
    var projectId: String? = null
)
