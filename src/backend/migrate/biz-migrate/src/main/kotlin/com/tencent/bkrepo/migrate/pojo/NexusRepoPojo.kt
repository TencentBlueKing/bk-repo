package com.tencent.bkrepo.migrate.pojo

data class NexusRepoPojo(
    val name: String,
    val format: String,
    val type: String,
    val url: String,
    val attributes: Any?
)
