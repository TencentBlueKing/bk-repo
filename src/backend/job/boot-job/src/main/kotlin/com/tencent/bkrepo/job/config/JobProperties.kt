package com.tencent.bkrepo.job.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties("job")
@EnableConfigurationProperties(JobProperties::class)
@Configuration
class JobProperties {
    /**
     * 是否执行仓库后台任务
     */
    var enabled: Boolean = true
}
