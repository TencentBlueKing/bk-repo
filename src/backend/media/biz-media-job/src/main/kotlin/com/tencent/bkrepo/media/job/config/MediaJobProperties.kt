package com.tencent.bkrepo.media.job.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mediajob")
data class MediaJobProperties(
    var cleanSuccessJobDays: Long = 7,
    /**
     * 每次重试失败任务的数量限制，默认100个
     */
    var retryFailedJobBatchSize: Int = 100,
)
