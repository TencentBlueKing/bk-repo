package com.tencent.bkrepo.job.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("job.repository-metrics-report")
class RepositoryMetricsReportJobProperties : MongodbJobProperties() {
    override var enabled: Boolean = false
    override var cron: String = Scheduled.CRON_DISABLED
}
