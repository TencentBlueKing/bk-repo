package com.tencent.bkrepo.helm.model.metadata

import java.util.SortedSet

data class HelmIndexYamlMetadata(
    val apiVersion: String,
    val entries: MutableMap<String, SortedSet<HelmChartMetadata>> = mutableMapOf(),
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
