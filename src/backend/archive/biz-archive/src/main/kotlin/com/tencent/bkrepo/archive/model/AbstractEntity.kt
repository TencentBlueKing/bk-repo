package com.tencent.bkrepo.archive.model

import java.time.LocalDateTime

/**
 * 抽象实体类
 * */
abstract class AbstractEntity(
    id: String? = null,
    val createdBy: String,
    val createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,
) : IdEntity(id)
