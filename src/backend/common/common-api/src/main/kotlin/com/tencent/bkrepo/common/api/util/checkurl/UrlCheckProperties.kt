package com.tencent.bkrepo.common.api.util.checkurl

data class UrlCheckProperties(
    var schemes: List<String> = listOf("http", "https"),
    var rules: List<String> = emptyList(),
    var mode: String = "subhost",
)
