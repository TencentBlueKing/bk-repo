package com.tencent.bkrepo.repository.pojo.credendials

import java.time.LocalDateTime

data class StorageCredentialsInfo(
    val id: String,
    val createdBy: String,
    val createdDate: LocalDateTime,
    val lastModifiedBy: String,
    val lastModifiedDate: LocalDateTime,
    val region: String?
)
