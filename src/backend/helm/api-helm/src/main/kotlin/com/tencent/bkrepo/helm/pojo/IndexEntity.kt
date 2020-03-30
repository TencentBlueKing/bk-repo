package com.tencent.bkrepo.helm.pojo

data class IndexEntity(
    var apiVersion: String? = null,
    var entries: MutableMap<String, MutableList<ChartEntity>>? = null,
    var generated: String? = null,
    var serverInfo: Map<String, Any>? = null
)
