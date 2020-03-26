package com.tencent.bkrepo.common.artifact.pojo.configuration.local.webhook

data class WebHookInfo (
    val url: String,
    val headers: Map<String, String>? = null
)