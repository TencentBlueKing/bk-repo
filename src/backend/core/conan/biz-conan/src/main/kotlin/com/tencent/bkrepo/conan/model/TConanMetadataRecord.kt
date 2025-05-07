package com.tencent.bkrepo.conan.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

@Document("conan_metadata")
@CompoundIndexes(
    CompoundIndex(
        name = "unique_index",
        def = "{'projectId':1, 'repoName':1, 'recipe':1}",
        background = true,
        unique = true
    )
)
data class TConanMetadataRecord(
    val id: String?,
    val projectId: String,
    val repoName: String,
    val user: String,
    val name: String,
    val version: String,
    val channel: String,
    val recipe: String
)
