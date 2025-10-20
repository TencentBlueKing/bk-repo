package com.tencent.bkrepo.common.metadata.pojo.sign

data class SignConfigUpdateRequest(
    val projectId: String,
    val scanner: MutableMap<String, String>,
    val tags: MutableList<String> = mutableListOf("Alpha"),
    val expireDays: Int,
)
