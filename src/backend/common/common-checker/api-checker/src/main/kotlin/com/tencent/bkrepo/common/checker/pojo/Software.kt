package com.tencent.bkrepo.common.checker.pojo

data class Software(
    val id: String,
    val versionEndExcluding: String?,
    val vulnerabilityIdMatched: String?
)
