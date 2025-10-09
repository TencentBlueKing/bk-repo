package com.tencent.bkrepo.common.metadata.pojo.sign

data class SignConfigCreateRequest(
    val projectId: String,
    val scanner: MutableMap<String, String>,
    val tags: MutableList<String> = mutableListOf("Alpha"),
)
