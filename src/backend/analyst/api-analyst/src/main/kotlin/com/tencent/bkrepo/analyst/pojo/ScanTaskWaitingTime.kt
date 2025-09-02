package com.tencent.bkrepo.analyst.pojo

data class ScanTaskWaitingTime(
    val order: Int,
    val waitingTime: Long,
    val expireDay: Int
)