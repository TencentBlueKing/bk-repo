package com.tencent.bkrepo.media.job.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mediajob")
data class MediaJobProperties(
    var cleanSuccessJobDays: Long = 7,
)
