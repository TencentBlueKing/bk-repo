package com.tencent.bkrepo.common.metadata.pojo.sign

import java.time.LocalDateTime

data class SignConfig(
    val projectId: String,
    val scanner: Map<String, String>,
    val tags: List<String>,
    val expireDays: Int,
    val createdBy: String,
    val createdDate: LocalDateTime,
    val lastModifiedBy: String,
    val lastModifiedDate: LocalDateTime,
)
