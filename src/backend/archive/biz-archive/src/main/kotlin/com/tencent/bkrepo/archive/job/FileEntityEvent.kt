package com.tencent.bkrepo.archive.job

import com.tencent.bkrepo.archive.model.AbstractEntity

data class FileEntityEvent(
    val id: String,
    val entity: AbstractEntity,
)
