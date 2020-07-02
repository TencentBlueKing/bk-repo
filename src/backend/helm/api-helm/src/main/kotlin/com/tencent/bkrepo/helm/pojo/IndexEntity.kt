package com.tencent.bkrepo.helm.pojo

data class IndexEntity(
    val apiVersion: String,
    val entries: MutableMap<String, MutableList<MutableMap<String, Any>>> = mutableMapOf(),
    var generated: String,
    val serverInfo: Map<String, Any> = emptyMap()
) {
    fun entriesSize(): Int {
        var count = 0
        entries.values.forEach {
            count += it.size
        }
        return count
    }
}
