package com.tencent.bkrepo.auth.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document


@Document("personal_path")
@CompoundIndexes(
    CompoundIndex(name = "userId_idx", def = "{'userId': 1}", background = true),
    CompoundIndex(name = "type_idx", def = "{'userId': 1, 'projectId':1 , 'repoName':1}", background = true),
)
data class TPersonalPath(
    val id: String? = null,
    val projectId: String,
    val repoName: String,
    val fullPath: String,
    val userId: String
)