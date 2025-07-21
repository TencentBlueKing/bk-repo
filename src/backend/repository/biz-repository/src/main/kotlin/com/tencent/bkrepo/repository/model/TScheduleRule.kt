package com.tencent.bkrepo.repository.model

data class TScheduleRule(
    /**
     * 元数据键
     */
    val key: String,
    /**
     * 元数据值
     */
    var value: Any,
    /**
     * 元数据描述信息
     */
    val description: String? = null,
)
