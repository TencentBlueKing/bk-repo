package com.tencent.bkrepo.helm.pojo

data class IndexEntity(
    val apiVersion: String,
    val entries: MutableMap<String, MutableList<MutableMap<String, Any>>> = mutableMapOf(),
    val generated: String,
    val serverInfo: Map<String, Any> = emptyMap()
)
