package com.tencent.bkrepo.cargo.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("cargo_version_dependency")
@CompoundIndexes(
    CompoundIndex(
        name = "src_dep_unique_idx",
        def = "{'projectId': 1, 'repoName': 1, 'srcPackageName': 1, 'srcVersion': 1, " +
            "'depPackageName': 1, 'depVersionReq': 1, 'kind': 1, 'target': 1}",
        background = true,
        unique = true
    ),
    CompoundIndex(
        name = "dep_page_sort_idx",
        def = "{'projectId': 1, 'repoName': 1, 'depPackageName': 1, 'srcPackageName': 1, 'srcVersion': 1}",
        background = true
    )
)
data class TCargoVersionDependency(
    var id: String? = null,
    var projectId: String,
    var repoName: String,
    var srcPackageName: String,
    var srcVersion: String,
    var depPackageName: String,
    var depVersionReq: String,
    var kind: String? = null,
    var optional: Boolean = false,
    var target: String? = null,
    var defaultFeatures: Boolean = true,
    var features: List<String> = emptyList(),
    var createdDate: LocalDateTime,
    var lastModifiedDate: LocalDateTime
)
