package com.tencent.bkrepo.pypi.artifact.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("migration_data")
@CompoundIndexes(
    CompoundIndex(
        name = "migration_data_idx",
        def = "{'id': 1, 'projectId': 1, 'repoName': 1}",
        background = true, unique = true
    )
)
data class TMigrateData(
    var id: String? = null,
    // 错误数据
    var errorData: String?,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,
    var packagesNum: Int,
    var filesNum: Int,
    var elapseTimeSeconds: Long,
    var description: String,
    var projectId: String,
    var repoName: String
)
