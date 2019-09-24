package com.tencent.bkrepo.repository.model

import com.tencent.bkrepo.repository.constant.enum.RepositoryCategoryEnum
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
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    var name: String,
    var type: String,
    var category: RepositoryCategoryEnum,
    var public: Boolean,
    var projectId: String,
    var description: String? = null,
    var extension: Any? = null

)
