package com.tencent.bkrepo.common.metrics.push.custom.base

data class CustomEventItem(
    val eventName: String,
    val content: String,
    val target: String = "",
    val dimension: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    /** 对应 event.extra，为空时序列化时忽略 */
    val extra: Map<String, Any> = emptyMap(),
)
