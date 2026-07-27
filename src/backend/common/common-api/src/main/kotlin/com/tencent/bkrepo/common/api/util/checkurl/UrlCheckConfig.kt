package com.tencent.bkrepo.common.api.util.checkurl

data class UrlCheckConfig(
    var schemes: List<String> = listOf("http", "https"),
    var rules: List<String> = mutableListOf(),
    var mode: String = "subhost",
)
