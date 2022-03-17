package com.tencent.bkrepo.job.listener.event

import java.time.Duration

/**
 * 任务执行完事件
 * */
data class TaskExecutedEvent(
    val doneCount: Int,
    val avgWaitTime: Duration,
    val avgExecuteTime: Duration
)
