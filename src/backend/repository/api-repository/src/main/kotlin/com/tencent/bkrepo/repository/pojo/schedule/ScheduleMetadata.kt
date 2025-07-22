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
    /**
     * 是否为属于系统创建的元数据
     */
    val system: Boolean = false,
    /**
     * 元数据描述信息
     */
    val description: String? = null,
    /**
     * 元数据链接地址
     */
    val link: String? = null
)
