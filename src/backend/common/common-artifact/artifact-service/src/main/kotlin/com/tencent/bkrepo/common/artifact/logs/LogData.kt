package com.tencent.bkrepo.common.artifact.logs

data class LogData(
    val fileSize: Long = 0,
    val logContent: String = "",
    val lastUpdateLabel: String = "",
)
