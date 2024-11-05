package com.tencent.bkrepo.archive.core

import com.tencent.bkrepo.archive.model.AbstractEntity

/**
 * 文件事件
 * */
data class FileEntityEvent(
    val id: String,
    val entity: AbstractEntity,
)
