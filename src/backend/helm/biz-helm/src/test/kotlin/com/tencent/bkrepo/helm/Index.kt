package com.tencent.bkrepo.helm

class Index {
    val apiVersion: String? = null
    val entries: MutableMap<String, MutableList<Chart>>? = null
    val generated: String? = null
    val serverInfo: Map<String, Any>? = null

    override fun toString(): String {
        return "Index(apiVersion=$apiVersion, entries=$entries, generated=$generated, serverInfo=$serverInfo)"
    }
}