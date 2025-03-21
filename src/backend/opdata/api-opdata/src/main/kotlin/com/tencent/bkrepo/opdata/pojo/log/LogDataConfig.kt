package com.tencent.bkrepo.opdata.pojo.log

data class LogDataConfig(
    val logs: Set<String> = emptySet(),
    var nodes: Map<String, Set<String>> = emptyMap(),
    var refreshRateMillis: Long
)
