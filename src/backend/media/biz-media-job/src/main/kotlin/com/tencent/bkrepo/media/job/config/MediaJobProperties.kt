package com.tencent.bkrepo.media.job.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mediajob")
data class MediaJobProperties(
    var cleanSuccessJobDays: Long = 7,
    /**
     * 每次重试失败任务的数量限制，默认100个
     */
    var retryFailedJobBatchSize: Int = 100,
    /**
     * 转码任务指标的回看天数，默认1（即查看昨天的数据）
     * 测试环境可设为0查看当天数据
     */
    var metricsLookbackDays: Long = 1,
)
