package com.tencent.bkrepo.replication.pojo.task.setting

/**
 * 计划调度策略
 */
enum class ExecutionStrategy {
    /**
     * 立即执行
     */
    IMMEDIATELY,

    /**
     * 指定时间执行
     */
    SPECIFIED_TIME,

    /**
     * cron表达式执行
     */
    CRON_EXPRESSION
}
