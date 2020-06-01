package com.tencent.bkrepo.npm.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("migration_error_data")
@CompoundIndexes(
    CompoundIndex(
        name = "projectId_repoName_error_data_idx",
        def = "{'projectId': 1, 'repoName': 1, 'counter': 1}",
        background = true
    )
)
data class TMigrationErrorData(
    var id: String? = null,
    // 同步次数计数器
    var counter: Int,
    // 错误数据
    var errorData: String,
    var projectId: String,
    var repoName: String,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime
)
