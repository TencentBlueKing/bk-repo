package com.tencent.bkrepo.migrate.pojo.suyan

import com.tencent.bkrepo.migrate.pojo.BkProduct
import java.time.LocalDateTime

data class SuyanArtifactInfo(
    val id: String?,
    val createdBy: String,
    val createdDate: LocalDateTime,
    val lastModifiedBy: String,
    val lastModifiedDate: LocalDateTime,
    val repositoryName: String,
    val groupId: String,
    val artifactId: String,
    val type: String,
    val version: String,
    val productList: MutableSet<BkProduct>?
)
