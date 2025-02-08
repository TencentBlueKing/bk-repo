package com.tencent.bkrepo.job.schedule

/**
 * 调度任务
 * */
data class Job(
    val id: String? = null,
    val name: String,
    val scheduleConf: String,
    val scheduleType: JobScheduleType,
    val group: String,
    val sharding: Boolean,
    val runnable: Runnable,
)
