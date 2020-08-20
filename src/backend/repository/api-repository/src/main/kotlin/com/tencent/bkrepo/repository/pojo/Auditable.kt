package com.tencent.bkrepo.repository.pojo

import java.time.LocalDateTime

interface Auditable {
    val createdBy: String?
    val createdDate: LocalDateTime?
    val lastModifiedBy: String?
    val lastModifiedDate: LocalDateTime?
}