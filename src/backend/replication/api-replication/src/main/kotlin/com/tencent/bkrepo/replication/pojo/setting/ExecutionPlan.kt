package com.tencent.bkrepo.replication.pojo.setting

import java.time.LocalDateTime

/**
 * 执行计划
 */
data class ExecutionPlan(
    /**
     * 执行一次，创建后立即执行
     */
    val executeImmediately: Boolean = true,
    /**
     * 执行一次，指定时间执行
     */
    val executeTime: LocalDateTime? = null,
    /**
     * cron表达式定时执行
     */
    val cronExpression: String? = null
)
