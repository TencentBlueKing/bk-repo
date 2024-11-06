package com.tencent.bkrepo.job.schedule

/**
 * 任务调度类型
 * */
enum class JobScheduleType {
    /**
     * cron表达式
     * */
    CRON,

    /**
     * 固定延迟
     * */
    FIX_DELAY,

    /**
     * 固定频率
     * */
    FIX_RATE,
}
