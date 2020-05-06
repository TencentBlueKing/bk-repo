package com.tencent.bkrepo.repository.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("module_deps")
@CompoundIndexes(
    CompoundIndex(
        name = "projectId_repoName_name_deps_idx",
        def = "{'projectId': 1, 'repoName': 1, 'name': 1, 'deps': 1, 'deleted': 1}",
        background = true
    )
)
data class TModuleDeps(
    var id: String? = null,
    // module name
    var name: String,
    // which module depend on this module
    var deps: String,
    var projectId: String,
    var repoName: String,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,
    var deleted: LocalDateTime? = null
)
