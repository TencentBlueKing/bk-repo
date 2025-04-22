package com.tencent.bkrepo.common.service.log

data class LogData(
    val fileSize: Long = 0,
    val logContent: String = "",
    val lastUpdateLabel: String = "",
)
