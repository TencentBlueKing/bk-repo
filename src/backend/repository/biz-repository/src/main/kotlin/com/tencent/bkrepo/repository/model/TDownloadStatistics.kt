package com.tencent.bkrepo.repository.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDate

@Document("artifact_download_count")
@CompoundIndexes(
    CompoundIndex(
        name = "projectId_repoName_artifact_download_count_idx",
        def = "{'projectId': 1, 'repoName': 1, 'artifact': 1, 'version': 1, 'date': 1}",
        background = true,
        unique = true
    )
)
data class TDownloadStatistics(
    var id: String? = null,
    var projectId: String,
    var repoName: String,
    // artifact name
    var artifact: String,
    var version: String?,
    // download count
    var count: Long,
    var date: LocalDate
)
