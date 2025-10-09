package com.tencent.bkrepo.common.metadata.model

import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("sign_config")
data class TSignConfig(
    val id: String? = null,
    @Indexed(unique = true, background = true)
    val projectId: String,
    // key文件类型, value扫描器
    val scanner: MutableMap<String, String>,
    val tags: MutableList<String> = mutableListOf("Alpha"),
    val createdBy: String,
    val createdDate: LocalDateTime,
    val lastModifiedBy: String,
    val lastModifiedDate: LocalDateTime,
)
