package com.tencent.bkrepo.repository.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 项目模型
 */
@Document("project")
@CompoundIndexes(
    CompoundIndex(name = "name_idx", def = "{'name': 1}", unique = true)
)
data class TProject(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    var name: String,
    var displayName: String,
    var description: String
)
