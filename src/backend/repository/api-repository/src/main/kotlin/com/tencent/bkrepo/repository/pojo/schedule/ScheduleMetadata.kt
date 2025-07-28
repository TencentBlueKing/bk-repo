package com.tencent.bkrepo.repository.pojo.schedule

data class ScheduleMetadata(
    /**
     * 元数据键
     */
    val key: String,
    /**
     * 元数据值
     */
    var value: Any,
)
