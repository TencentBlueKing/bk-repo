package com.tencent.bkrepo.repository.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDate

@Document("artifact_download_statistics")
@CompoundIndexes(
    CompoundIndex(
        name = "projectId_repoName_artifact_version_date_idx",
        def = "{'projectId': 1, 'repoName': 1, 'artifact': 1, 'version': 1, 'date': 1}",
        background = true,
        unique = true
    )
)
data class TDownloadStatistics(
    var id: String? = null,
    var projectId: String,
    var repoName: String,
    var artifact: String,
    var version: String?,
    var date: LocalDate,
    var count: Long
)
