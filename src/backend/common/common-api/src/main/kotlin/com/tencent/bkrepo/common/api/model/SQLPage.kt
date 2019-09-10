package com.tencent.bkrepo.common.api.model

data class SQLPage<out T>(
    val count: Long,
    val records: List<T>
)
